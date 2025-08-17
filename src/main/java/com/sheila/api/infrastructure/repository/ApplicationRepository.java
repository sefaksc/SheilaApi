package com.sheila.api.infrastructure.repository;

import com.sheila.api.core.model.ApplicationDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ApplicationRepository extends MongoRepository<ApplicationDoc, String> {
    Optional<ApplicationDoc> findByName(String name);
}
