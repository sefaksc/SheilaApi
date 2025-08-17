package com.sheila.api.application;

import com.sheila.api.core.dto.Endpoint;
import com.sheila.api.core.dto.RoomJoinResult;

import java.util.List;

public interface RoomService {
    /**
     * appKey: Application kimliği (id veya name)
     * roomName: Oda adı (yoksa oluşturulur)
     * ip/port: İstemci
     */
    RoomJoinResult joinRoom(String appKey, String roomName, String ip, int port);

    /**
     * Oda oluşturulurken kapasite belirtmek için opsiyonel parametre.
     * roomCapacity null veya <1 ise config'teki default kapasite kullanılır.
     * Mevcut oda varsa bu parametre yok sayılır (kapasite değişmez).
     */
    RoomJoinResult joinRoom(String appKey, String roomName, String ip, int port, Integer roomCapacity);

    List<Endpoint> listRoomPeers(String appKey, String roomName);

    void leaveRoom(String appKey, String roomName, String ip, int port);

    List<String> listRoomNames(String appKey);

    void touchClient(String appKey, String roomName, String ip, int port);
}

