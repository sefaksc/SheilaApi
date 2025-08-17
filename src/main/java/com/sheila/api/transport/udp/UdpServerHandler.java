package com.sheila.api.transport.udp;

import com.sheila.api.application.RoomService;
import com.sheila.api.application.ServerProber;
import com.sheila.api.core.dto.Endpoint;
import com.sheila.api.core.dto.RoomJoinResult;
import com.sheila.api.core.exception.AppNotFoundException;
import com.sheila.api.core.exception.ApplicationCapacityExceededException;
import com.sheila.api.core.exception.RoomFullException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.sheila.api.transport.udp.UdpMessageUtil.joinClientsList;
import static com.sheila.api.transport.udp.UdpMessageUtil.tryParseCapacity;

public class UdpServerHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger log = LoggerFactory.getLogger(UdpServerHandler.class);

    private final RoomService roomService;
    private final ServerProber prober;

    public UdpServerHandler(RoomService roomService, ServerProber prober) {
        this.roomService = roomService;
        this.prober = prober;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
        String msg = packet.content().toString(CharsetUtil.UTF_8).trim();
        InetSocketAddress sender = packet.sender();
        String senderIp = sender.getAddress().getHostAddress();
        int senderPort = sender.getPort();

        if (msg.isEmpty()) {
            send(ctx, sender, "ERR|BAD_REQUEST|empty message");
            return;
        }

        String[] parts = msg.split("\\|");
        String cmd = parts[0].trim().toUpperCase();

        try {
            switch (cmd) {
                case "JOIN" -> handleJoin(ctx, sender, parts, senderIp, senderPort);
                case "LEAVE" -> handleLeave(ctx, sender, parts, senderIp, senderPort);
                case "LIST" -> handleList(ctx, sender, parts);
                case "PING" -> handlePing(ctx, sender, parts, senderIp, senderPort);
                case "PONG" -> handlePong(ctx, sender, parts, senderIp, senderPort);
                default -> send(ctx, sender, "ERR|UNKNOWN_COMMAND|" + cmd);
            }
        } catch (AppNotFoundException e) {
            send(ctx, sender, "ERR|APP_NOT_FOUND|" + e.getMessage());
        } catch (RoomFullException e) {
            send(ctx, sender, "ERR|ROOM_FULL|" + e.getMessage());
        } catch (IllegalArgumentException e) {
            send(ctx, sender, "ERR|BAD_REQUEST|" + e.getMessage());
        } catch (ApplicationCapacityExceededException e) {
            send(ctx, sender, "ERR|APP_CAP_EXCEEDED|" + e.getMessage());
        } catch (Exception e) {
            log.error("UDP handler error", e);
            send(ctx, sender, "ERR|INTERNAL|unexpected");
        }
    }

    private void handleJoin(ChannelHandlerContext ctx, InetSocketAddress sender, String[] p, String ip, int port) {
        if (p.length < 3) throw new IllegalArgumentException("JOIN|<appKey>|<roomName>|[capacity]");
        String appKey = p[1].trim();
        String roomName = p[2].trim();
        Integer capacity = (p.length >= 4) ? tryParseCapacity(p[3]) : null;

        RoomJoinResult result = roomService.joinRoom(appKey, roomName, ip, port, capacity);

        // 1) İstek sahibine oda listesi
        List<String> peers = result.getParticipants().stream().map(Endpoint::toString).toList();
        send(ctx, sender, "ROOM|" + result.getRoomName() + "|clients=" + joinClientsList(peers));

        // 2) Odadaki diğerlerine NEW_CLIENT
        String me = result.getJoined().toString();
        for (Endpoint ep : result.getParticipants()) {
            if (Objects.equals(ep.toString(), me)) continue; // kendine gönderme
            InetSocketAddress target = new InetSocketAddress(ep.getIp(), ep.getPort());
            send(ctx, target, "NEW_CLIENT|" + me);
        }
    }

    private void handleLeave(ChannelHandlerContext ctx, InetSocketAddress sender, String[] p, String ip, int port) {
        if (p.length < 3) throw new IllegalArgumentException("LEAVE|<appKey>|<roomName>");
        String appKey = p[1].trim();
        String roomName = p[2].trim();

        roomService.leaveRoom(appKey, roomName, ip, port);

        // Kalanlara broadcast
        List<Endpoint> remain = roomService.listRoomPeers(appKey, roomName);
        for (Endpoint ep : remain) {
            InetSocketAddress target = new InetSocketAddress(ep.getIp(), ep.getPort());
            send(ctx, target, "CLIENT_LEFT|" + ip + ":" + port);
        }
        send(ctx, sender, "OK|LEFT");
    }

    private void handleList(ChannelHandlerContext ctx, InetSocketAddress sender, String[] p) {
        if (p.length < 3) throw new IllegalArgumentException("LIST|<appKey>|<roomName>");
        String appKey = p[1].trim();
        String roomName = p[2].trim();
        List<String> peers = roomService.listRoomPeers(appKey, roomName).stream()
                .map(Endpoint::toString)
                .collect(Collectors.toList());
        send(ctx, sender, "ROOM|" + roomName + "|clients=" + joinClientsList(peers));
    }

    /** PING: lastSeen tazeleme. Eğer kayıt yoksa oluşturmak istersen JOIN gibi davranır. */
    private void handlePing(ChannelHandlerContext ctx, InetSocketAddress sender, String[] p, String ip, int port) {
        if (p.length < 3) throw new IllegalArgumentException("PING|<appKey>|<roomName>");
        String appKey = p[1].trim();
        String roomName = p[2].trim();
        roomService.joinRoom(appKey, roomName, ip, port); // keep-alive mantığı
        send(ctx, sender, "OK|PING");
    }

    private void handlePong(ChannelHandlerContext ctx, InetSocketAddress sender, String[] p, String ip, int port) {
        if (p.length < 3) throw new IllegalArgumentException("PONG|<appName>|<roomName>");
        String appName = p[1].trim();    // app NAME bekliyoruz
        String roomName = p[2].trim();

        // lastSeen'i tazele (JOIN'le aynı idempotent davranış)
        roomService.joinRoom(appName, roomName, ip, port);

        // probe sayaçlarını sıfırla
        prober.onPong(ip, port);

        // send(ctx, sender, "OK|PONG");
    }

    private void send(ChannelHandlerContext ctx, InetSocketAddress target, String text) {
        ByteBuf buf = Unpooled.copiedBuffer(text, CharsetUtil.UTF_8);
        ctx.writeAndFlush(new DatagramPacket(buf, target));
    }
}
