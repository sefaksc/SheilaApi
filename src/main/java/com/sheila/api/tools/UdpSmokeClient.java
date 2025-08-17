package com.sheila.api.tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Basit UDP smoke test client:
 * - Açılışta JOIN|<appName>|<roomName>|[capacity] gönderir.
 * - Sunucudan gelen SRV_PING'e PONG|<appName>|<roomName> ile yanıt verir.
 * - Konsoldan LIST/LEAVE/QUIT komutlarını alır.
 *
 * Varsayılanlar:
 *   SERVER=127.0.0.1:9876
 *   APP_NAME=demo-app (SeedConfig ile eklenmişti)
 *   ROOM_NAME=room-alpha
 *   ROOM_CAPACITY (opsiyonel) = 0 → gönderilmez
 *
 * Parametre geçmek istersen:
 *   java ... UdpSmokeClient <serverHost> <serverPort> <appName> <roomName> [roomCapacity]
 */
public class UdpSmokeClient {

    public static void main(String[] args) throws Exception {
        String serverHost = args.length > 0 ? args[0] : "127.0.0.1";
        int serverPort = args.length > 1 ? Integer.parseInt(args[1]) : 9876;
        String appName   = args.length > 2 ? args[2] : "demo-app";
        String roomName  = args.length > 3 ? args[3] : "room-alpha";
        Integer roomCap  = (args.length > 4) ? tryParseInt(args[4]) : null;

        InetAddress serverAddr = InetAddress.getByName(serverHost);
        InetSocketAddress server = new InetSocketAddress(serverAddr, serverPort);

        System.out.printf("[client] starting on ephemeral UDP port → server=%s:%d, app=%s, room=%s%n",
                serverHost, serverPort, appName, roomName);

        try (DatagramSocket sock = new DatagramSocket(0)) { // 0 = OS seçsin, socket sabit kalsın
            sock.setSoTimeout(0); // blocking receive

            // Receiver thread: tüm server mesajlarını dinle
            AtomicBoolean running = new AtomicBoolean(true);
            Thread rx = new Thread(() -> receiveLoop(sock, server, appName, roomName, running), "udp-smoke-rx");
            rx.setDaemon(true);
            rx.start();

            // Açılışta JOIN gönder
            sendJoin(sock, server, appName, roomName, roomCap);

            // Konsol komutları
            try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                System.out.println("[client] commands: LIST | LISTAPP | LEAVE | JOIN [cap] | QUIT");
                String line;
                while (running.get() && (line = br.readLine()) != null) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length == 0 || parts[0].isBlank()) continue;
                    String cmd = parts[0].toUpperCase();

                    switch (cmd) {
                        case "LIST" -> send(sock, server, "LIST|" + appName + "|" + roomName);
                        case "LISTAPP" -> send(sock, server, "LIST|" + appName);
                        case "LEAVE" -> send(sock, server, "LEAVE|" + appName + "|" + roomName);
                        case "JOIN" -> {
                            Integer cap = (parts.length >= 2) ? tryParseInt(parts[1]) : null;
                            sendJoin(sock, server, appName, roomName, cap);
                        }
                        case "QUIT" -> {
                            running.set(false);
                            System.out.println("[client] quitting…");
                        }
                        default -> System.out.println("[client] unknown: " + line);
                    }
                }
            }
        }
    }

    private static void receiveLoop(DatagramSocket sock, InetSocketAddress server,
                                    String appName, String roomName, AtomicBoolean running) {
        byte[] buf = new byte[2048];
        DatagramPacket pkt = new DatagramPacket(buf, buf.length);

        while (running.get()) {
            try {
                pkt.setData(buf);
                pkt.setLength(buf.length);
                sock.receive(pkt);
                String s = new String(pkt.getData(), pkt.getOffset(), pkt.getLength(), StandardCharsets.UTF_8).trim();
                System.out.println("[server] " + s);

                // SRV_PING|<appName>|<roomName>
                if (s.startsWith("SRV_PING|")) {
                    List<String> parts = Arrays.asList(s.split("\\|"));
                    if (parts.size() >= 3) {
                        String aName = parts.get(1).trim();
                        String rName = parts.get(2).trim();
                        String pong = "PONG|" + aName + "|" + rName;
                        send(sock, server, pong);
                    }
                }
            } catch (Exception e) {
                if (running.get()) System.out.println("[client] rx error: " + e.getMessage());
            }
        }
    }

    private static void sendJoin(DatagramSocket sock, InetSocketAddress server,
                                 String app, String room, Integer cap) {
        String msg = "JOIN|" + app + "|" + room;
        if (cap != null && cap > 0) msg += "|" + cap;
        send(sock, server, msg);
    }

    private static void send(DatagramSocket sock, InetSocketAddress server, String msg) {
        try {
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            DatagramPacket out = new DatagramPacket(data, data.length, server.getAddress(), server.getPort());
            sock.send(out);
            System.out.println("[client] >> " + msg);
        } catch (Exception e) {
            System.out.println("[client] send error: " + e.getMessage());
        }
    }

    private static Integer tryParseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception ignored) { return null; }
    }
}
