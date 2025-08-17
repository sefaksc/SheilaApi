package com.sheila.api.application;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProbeTracker {
    private final Map<String, Integer> missed = new ConcurrentHashMap<>();
    public static String key(String ip, int port) { return ip + ":" + port; }
    public void onProbeSent(String endpointKey) { missed.merge(endpointKey, 1, Integer::sum); }
    public void onPong(String endpointKey) { missed.remove(endpointKey); }
    public boolean shouldDrop(String endpointKey, int maxMissed) {
        return missed.getOrDefault(endpointKey, 0) >= maxMissed;
    }
    public void clear(String endpointKey) { missed.remove(endpointKey); }
}
