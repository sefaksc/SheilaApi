package com.sheila.api.transport.udp;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.CharsetUtil;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class UdpMessenger {
    private final AtomicReference<Channel> channelRef = new AtomicReference<>();

    void setChannel(Channel ch) { channelRef.set(ch); }
    public boolean isReady() {
        Channel ch = channelRef.get();
        return ch != null && ch.isActive();
    }
    public void send(String ip, int port, String text) {
        Channel ch = channelRef.get();
        if (ch == null || !ch.isActive()) return;
        ch.writeAndFlush(new DatagramPacket(
                Unpooled.copiedBuffer(text, CharsetUtil.UTF_8),
                new InetSocketAddress(ip, port)
        ));
    }
}
