package com.sheila.api.core.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * Oda üyesi (client). roomId + ip + port benzersizdir.
 * lastSeen üzerinde TTL index ile "zombi" kayıtlar otomatik silinebilir.
 */
@Document("clients")
@CompoundIndex(name = "room_ip_port_unique", def = "{'roomId': 1, 'ip': 1, 'port': 1}", unique = true)
public class ClientDoc {

    @Id
    private String id;

    @Indexed
    private String roomId;

    private String ip;
    private Integer port;

    @org.springframework.data.mongodb.core.index.Indexed(name = "last_seen_ttl", expireAfter = "PT24H")
    private Date lastSeen;

    public ClientDoc() { }

    public ClientDoc(String roomId, String ip, Integer port, Date lastSeen) {
        this.roomId = roomId;
        this.ip = ip;
        this.port = port;
        this.lastSeen = lastSeen;
    }

    // --- getters & setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public Date getLastSeen() { return lastSeen; }
    public void setLastSeen(Date lastSeen) { this.lastSeen = lastSeen; }
}
