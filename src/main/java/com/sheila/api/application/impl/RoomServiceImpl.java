package com.sheila.api.application.impl;

import com.sheila.api.application.RoomService;
import com.sheila.api.core.dto.Endpoint;
import com.sheila.api.core.dto.RoomJoinResult;
import com.sheila.api.core.exception.AppNotFoundException;
import com.sheila.api.core.exception.RoomFullException;
import com.sheila.api.core.model.ApplicationDoc;
import com.sheila.api.core.model.ClientDoc;
import com.sheila.api.core.model.RoomDoc;
import com.sheila.api.infrastructure.repository.ApplicationRepository;
import com.sheila.api.infrastructure.repository.ClientRepository;
import com.sheila.api.infrastructure.repository.RoomRepository;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JOIN/LEAVE akışlarının iş kuralları.
 * - Application, hem id hem name ile bulunabilir (esneklik için).
 * - Room upsert (yoksa oluştur).
 * - Kapasite kontrolü.
 * - Client upsert + lastSeen güncelleme.
 */
@Service
public class RoomServiceImpl implements RoomService {

    private final ApplicationRepository applicationRepository;
    private final RoomRepository roomRepository;
    private final ClientRepository clientRepository;
    private final MongoTemplate mongo;

    private static final Logger log = LoggerFactory.getLogger(RoomServiceImpl.class);

    @Value("${app.rooms.defaultCapacity:100}")
    private int defaultRoomCapacity;

    public RoomServiceImpl(ApplicationRepository applicationRepository,
                           RoomRepository roomRepository,
                           ClientRepository clientRepository,
                           MongoTemplate mongo) {
        this.applicationRepository = applicationRepository;
        this.roomRepository = roomRepository;
        this.clientRepository = clientRepository;
        this.mongo = mongo;
    }

    public RoomJoinResult joinRoom(String appKey, String roomName, String ip, int port) {
        return joinRoom(appKey, roomName, ip, port, null);
    }

    @Override
    @Transactional
    public RoomJoinResult joinRoom(String appKey, String roomName, String ip, int port, Integer roomCapacity) {
        String appId = resolveApplicationId(appKey)
                .orElseThrow(() -> new AppNotFoundException(appKey));

        // 1) Odayı upsert et
        // 2) Eğer oda zaten varsa setOnInsert mevcut değeri değiştirmez.
        Query roomQ = new Query(Criteria.where("applicationId").is(appId).and("name").is(roomName));
        Update roomU = new Update()
                .setOnInsert("applicationId", appId)
                .setOnInsert("name", roomName)
                .setOnInsert("capacity", normalizeCapacity(roomCapacity)); // null/<1 ise default

        RoomDoc room = mongo.findAndModify(
                roomQ, roomU,
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                RoomDoc.class
        );

        // 2) Kapasite kontrolü
        long memberCount = clientRepository.countByRoomId(room.getId());
        Integer cap = room.getCapacity();
        if (cap != null && memberCount >= cap) {
            throw new RoomFullException(roomName);
        }

        // 3) Client upsert + lastSeen
        Query cQ = new Query(Criteria.where("roomId").is(room.getId())
                .and("ip").is(ip)
                .and("port").is(port));
        Update cU = new Update()
                .set("roomId", room.getId())
                .set("ip", ip)
                .set("port", port)
                .set("lastSeen", new Date());
        mongo.upsert(cQ, cU, ClientDoc.class);

        // 4) Katılımcıları döndür
        List<Endpoint> endpoints = clientRepository.findByRoomId(room.getId()).stream()
                .map(c -> new Endpoint(c.getIp(), c.getPort()))
                .collect(Collectors.toList());

        return new RoomJoinResult(roomName, endpoints, new Endpoint(ip, port));
    }


    private Integer normalizeCapacity(Integer cap) {
        if (cap == null || cap < 1) return defaultRoomCapacity;
        return cap;
    }


    @Override
    public List<Endpoint> listRoomPeers(String appKey, String roomName) {
        String appId = resolveApplicationId(appKey)
                .orElseThrow(() -> new AppNotFoundException(appKey));
        RoomDoc room = roomRepository.findByApplicationIdAndName(appId, roomName)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomName));

        return clientRepository.findByRoomId(room.getId()).stream()
                .map(c -> new Endpoint(c.getIp(), c.getPort()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void leaveRoom(String appKey, String roomName, String ip, int port) {
        String appId = resolveApplicationId(appKey)
                .orElseThrow(() -> new AppNotFoundException(appKey));

        RoomDoc room = roomRepository.findByApplicationIdAndName(appId, roomName)
                .orElse(null);
        if (room == null) return; // oda yoksa yapılacak iş yok

        clientRepository.findByRoomIdAndIpAndPort(room.getId(), ip, port)
                .ifPresent(c -> clientRepository.deleteById(c.getId()));
    }

    /**
     * appKey hem ID hem name olabilir.
     * Önce ID olarak dener; yoksa name ile arar.
     */
    private Optional<String> resolveApplicationId(String appKey) {
        return applicationRepository.findById(appKey).map(ApplicationDoc::getId)
                .or(() -> applicationRepository.findByName(appKey).map(ApplicationDoc::getId));
    }
}
