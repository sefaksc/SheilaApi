package com.sheila.api.application.impl;

import com.sheila.api.application.RoomService;
import com.sheila.api.core.dto.Endpoint;
import com.sheila.api.core.dto.RoomJoinResult;
import com.sheila.api.core.exception.AppNotFoundException;
import com.sheila.api.core.exception.ApplicationCapacityExceededException;
import com.sheila.api.core.exception.RoomFullException;
import com.sheila.api.core.model.ApplicationDoc;
import com.sheila.api.core.model.ClientDoc;
import com.sheila.api.core.model.RoomDoc;
import com.sheila.api.infrastructure.repository.ApplicationRepository;
import com.sheila.api.infrastructure.repository.ClientRepository;
import com.sheila.api.infrastructure.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JOIN/LEAVE akışlarının iş kuralları.
 * - Application id veya name ile bulunabilir.
 * - Yeni oda oluştururken application kapasitesi kontrol edilir.
 * - Oda doluluğu kontrol edilir.
 * - Client upsert + lastSeen güncellenir (idempotent).
 */
@Service
public class RoomServiceImpl implements RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomServiceImpl.class);

    private final ApplicationRepository applicationRepository;
    private final RoomRepository roomRepository;
    private final ClientRepository clientRepository;
    private final MongoTemplate mongo;

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

    @Override
    public RoomJoinResult joinRoom(String appKey, String roomName, String ip, int port) {
        return joinRoom(appKey, roomName, ip, port, null);
    }

    @Override
    @Transactional
    public RoomJoinResult joinRoom(String appKey, String roomName, String ip, int port, Integer roomCapacity) {
        // 1) Application'ı bul (id veya name)
        String appId = resolveApplicationId(appKey)
                .orElseThrow(() -> new AppNotFoundException(appKey));

        // 2) Oda mevcut mu? (Application kapasite kuralı sadece YENİ oda için)
        boolean roomExists = roomRepository.findByApplicationIdAndName(appId, roomName).isPresent();

        if (!roomExists) {
            int newRoomCap = normalizeCapacity(roomCapacity);

            ApplicationDoc app = applicationRepository.findById(appId)
                    .orElseThrow(() -> new AppNotFoundException(appId));
            Integer appCap = app.getCapacity();

            if (appCap != null) {
                int currentTotal = sumAppRoomsCapacity(appId);
                if (currentTotal + newRoomCap > appCap) {
                    log.debug("joinRoom: app capacity exceeded (appKey={}, currentTotal={}, newRoomCap={}, appCap={})",
                            appKey, currentTotal, newRoomCap, appCap);
                    throw new ApplicationCapacityExceededException(appKey);
                }
            }
        } else if (roomCapacity != null) {
            // Mevcut oda için gönderilen kapasite yok sayılır
            log.debug("joinRoom: existing room, incoming capacity={} ignored (room={})", roomCapacity, roomName);
        }

        // 3) Odayı upsert et (capacity yalnızca ilk oluşturma anında set edilir)
        Query roomQ = new Query(Criteria.where("applicationId").is(appId).and("name").is(roomName));
        Update roomU = new Update()
                .setOnInsert("applicationId", appId)
                .setOnInsert("name", roomName)
                .setOnInsert("capacity", normalizeCapacity(roomCapacity));

        RoomDoc room = mongo.findAndModify(
                roomQ, roomU,
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                RoomDoc.class
        );

        // 4) Oda doluluk kontrolü
        long memberCount = clientRepository.countByRoomId(room.getId());
        Integer cap = room.getCapacity();
        if (cap != null && memberCount >= cap) {
            throw new RoomFullException(roomName);
        }

        // 5) Client upsert + lastSeen
        Query cQ = new Query(Criteria.where("roomId").is(room.getId())
                .and("ip").is(ip)
                .and("port").is(port));
        Update cU = new Update()
                .set("roomId", room.getId())
                .set("ip", ip)
                .set("port", port)
                .set("lastSeen", new Date());
        mongo.upsert(cQ, cU, ClientDoc.class);

        // 6) Katılımcıları döndür
        List<Endpoint> endpoints = clientRepository.findByRoomId(room.getId()).stream()
                .map(c -> new Endpoint(c.getIp(), c.getPort()))
                .collect(Collectors.toList());

        return new RoomJoinResult(roomName, endpoints, new Endpoint(ip, port));
    }

    @Override
    public List<String> listRoomNames(String appKey) {
        String appId = resolveApplicationId(appKey)
                .orElseThrow(() -> new AppNotFoundException(appKey));

        return roomRepository.findByApplicationId(appId).stream()
                .map(RoomDoc::getName)
                .sorted()
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional
    public void touchClient(String appKey, String roomName, String ip, int port) {
        String appId = resolveApplicationId(appKey)
                .orElseThrow(() -> new AppNotFoundException(appKey));
        var roomOpt = roomRepository.findByApplicationIdAndName(appId, roomName);
        if (roomOpt.isEmpty()) return;
        var room = roomOpt.get();

        clientRepository.findByRoomIdAndIpAndPort(room.getId(), ip, port).ifPresent(c -> {
            Query q = new Query(Criteria.where("id").is(c.getId()));
            Update u = new Update().set("lastSeen", new Date());
            mongo.updateFirst(q, u, ClientDoc.class);
        });
    }

    private int normalizeCapacity(Integer cap) {
        if (cap == null || cap < 1) return defaultRoomCapacity;
        return cap;
    }

    /** Application altındaki odaların kapasite toplamı. */
    private int sumAppRoomsCapacity(String appId) {
        return roomRepository.findByApplicationId(appId).stream()
                .map(r -> r.getCapacity() == null ? defaultRoomCapacity : r.getCapacity())
                .reduce(0, Integer::sum);
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

        RoomDoc room = roomRepository.findByApplicationIdAndName(appId, roomName).orElse(null);
        if (room == null) return; // oda yoksa yapılacak iş yok

        clientRepository.findByRoomIdAndIpAndPort(room.getId(), ip, port)
                .ifPresent(c -> clientRepository.deleteById(c.getId()));
    }

    /** appKey hem ID hem name olabilir. */
    private Optional<String> resolveApplicationId(String appKey) {
        return applicationRepository.findById(appKey).map(ApplicationDoc::getId)
                .or(() -> applicationRepository.findByName(appKey).map(ApplicationDoc::getId));
    }
}
