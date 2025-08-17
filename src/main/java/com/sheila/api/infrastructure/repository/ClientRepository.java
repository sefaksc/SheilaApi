package com.sheila.api.infrastructure.repository;

import com.sheila.api.core.model.ClientDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends MongoRepository<ClientDoc, String> {
    List<ClientDoc> findByRoomId(String roomId);
    Optional<ClientDoc> findByRoomIdAndIpAndPort(String roomId, String ip, Integer port);
    long countByRoomId(String roomId);
}