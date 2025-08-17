package com.sheila.api.core.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Uygulama (application) meta bilgisi.
 * name alanı benzersizdir.
 */
@Document("applications")
public class ApplicationDoc {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    private Integer capacity;

    private Integer currentUserCount;

    public ApplicationDoc() { }

    public ApplicationDoc(String name, Integer capacity) {
        this.name = name;
        this.capacity = capacity;
        this.currentUserCount = 0;
    }

    // --- getters & setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public Integer getCurrentUserCount() { return currentUserCount; }
    public void setCurrentUserCount(Integer currentUserCount) { this.currentUserCount = currentUserCount; }
}
