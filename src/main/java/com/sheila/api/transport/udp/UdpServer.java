package com.sheila.api.transport.udp;

import com.sheila.api.application.RoomService;
import com.sheila.api.application.ServerProber;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

@Component
public class UdpServer {

    private static final Logger log = LoggerFactory.getLogger(UdpServer.class);

    @Value("${app.udp.port:9876}")
    private int port;
    private final RoomService roomService;
    private EventLoopGroup group;
    private Channel channel;
    private final UdpMessenger messenger;
    private final ServerProber prober;

    public UdpServer(RoomService roomService, UdpMessenger messenger, ServerProber prober) {
        this.roomService = roomService;
        this.messenger = messenger;
        this.prober = prober;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws InterruptedException {
        // Zaten çalışıyorsa tekrar başlatma
        if (channel != null && channel.isActive()) {
            log.info("UDP server already running on port {}", port);
            return;
        }

        group = new NioEventLoopGroup();

        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, false)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel ch) {
                        ch.pipeline().addLast(new UdpServerHandler(roomService, prober));
                    }
                });

        // Porta bağlan
        ChannelFuture bindFuture = bootstrap.bind(port).sync();
        channel = bindFuture.channel();

        // Diğer bean'lerin UDP mesajı gönderebilmesi için channel'ı paylaş
        messenger.setChannel(channel);

        log.info("Netty UDP Server listening on port {}", port);

        // Kapanışı arka planda bekle
        Thread waiter = new Thread(() -> {
            try {
                channel.closeFuture().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "udp-server-close-waiter");
        waiter.setDaemon(true);
        waiter.start();
    }

    @PreDestroy
    public void stop() {
        try {
            if (channel != null) {
                log.info("Stopping UDP server...");
                channel.close().syncUninterruptibly();
            }
        } finally {
            if (group != null) group.shutdownGracefully();
        }
    }
}
