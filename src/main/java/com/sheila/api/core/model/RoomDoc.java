package com.sheila.api.core.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Oda bilgisi: applicationId + name ikilisi benzersizdir.
 */
@Document("rooms")
@CompoundIndex(name = "app_room_unique", def = "{'applicationId': 1, 'name': 1}", unique = true)
public class RoomDoc {

    @Id
    private String id;

    @Indexed
    private String applicationId;

    private String name;

    private Integer capacity;

    public RoomDoc() { }

    public RoomDoc(String applicationId, String name, Integer capacity) {
        this.applicationId = applicationId;
        this.name = name;
        this.capacity = capacity;
    }

    // --- getters & setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
}
