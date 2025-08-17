package com.sheila.api.transport.udp;

public final class NetUtil {
    private NetUtil() {}
    /** ::1 ve ::ffff:a.b.c.d biçimlerini IPv4 string'e indirger. */
    public static String normalizeIp(String ip) {
        if (ip == null) return null;
        String s = ip.trim().toLowerCase();
        if ("::1".equals(s) || "0:0:0:0:0:0:0:1".equals(s)) return "127.0.0.1";
        if (s.startsWith("::ffff:") && s.length() > 7) return s.substring(7);
        return ip;
    }
}