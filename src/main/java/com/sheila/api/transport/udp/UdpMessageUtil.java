package com.sheila.api.transport.udp;

final class UdpMessageUtil {
    private UdpMessageUtil() {}

    static String joinClientsList(java.util.List<String> items) {
        return "[" + String.join(",", items) + "]";
    }

    static Integer tryParseCapacity(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.contains("=") ? raw.substring(raw.indexOf('=') + 1) : raw;
        try {
            int v = Integer.parseInt(s.trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
