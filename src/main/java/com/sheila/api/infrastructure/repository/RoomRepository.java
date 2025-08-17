package com.sheila.api.infrastructure.repository;

import com.sheila.api.core.model.RoomDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends MongoRepository<RoomDoc, String> {
    Optional<RoomDoc> findByApplicationIdAndName(String applicationId, String name);
    List<RoomDoc> findByApplicationId(String applicationId);
}
