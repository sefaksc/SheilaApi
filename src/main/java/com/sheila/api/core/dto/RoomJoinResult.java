package com.sheila.api.core.dto;

import java.util.List;

/** JOIN işleminin sonucu: oda adı, mevcut katılımcılar ve yeni katılan. */
public class RoomJoinResult {
    private final String roomName;
    private final List<Endpoint> participants;
    private final Endpoint joined; // yeni katılan

    public RoomJoinResult(String roomName, List<Endpoint> participants, Endpoint joined) {
        this.roomName = roomName;
        this.participants = participants;
        this.joined = joined;
    }
    public String getRoomName() { return roomName; }
    public List<Endpoint> getParticipants() { return participants; }
    public Endpoint getJoined() { return joined; }
}
