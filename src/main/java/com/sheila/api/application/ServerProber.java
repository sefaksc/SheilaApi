package com.sheila.api.application;

import com.sheila.api.core.model.ApplicationDoc;
import com.sheila.api.core.model.ClientDoc;
import com.sheila.api.core.model.RoomDoc;
import com.sheila.api.infrastructure.repository.ApplicationRepository;
import com.sheila.api.infrastructure.repository.ClientRepository;
import com.sheila.api.infrastructure.repository.RoomRepository;
import com.sheila.api.transport.udp.UdpMessenger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.sheila.api.application.ProbeTracker.key;

@Component
public class ServerProber {

    private final ClientRepository clientRepo;
    private final RoomRepository roomRepo;
    private final ApplicationRepository appRepo;
    private final UdpMessenger messenger;
    private final ProbeTracker tracker = new ProbeTracker();

    @Value("${app.probe.enabled:true}") private boolean enabled;
    @Value("${app.probe.intervalMs:10000}") private long intervalMs;
    @Value("${app.probe.maxMissed:3}") private int maxMissed;

    public ServerProber(ClientRepository clientRepo,
                        RoomRepository roomRepo,
                        ApplicationRepository appRepo,
                        UdpMessenger messenger) {
        this.clientRepo = clientRepo;
        this.roomRepo = roomRepo;
        this.appRepo = appRepo;
        this.messenger = messenger;
    }

    @Scheduled(fixedDelayString = "${app.probe.intervalMs:10000}")
    public void probeAll() {
        if (!enabled || !messenger.isReady()) return;

        List<ClientDoc> clients = clientRepo.findAll();
        for (ClientDoc c : clients) {
            RoomDoc room = roomRepo.findById(c.getRoomId()).orElse(null);
            if (room == null) continue;

            ApplicationDoc app = appRepo.findById(room.getApplicationId()).orElse(null);
            if (app == null) continue;

            String msg = "SRV_PING|" + app.getName() + "|" + room.getName();
            messenger.send(c.getIp(), c.getPort(), msg);

            String k = key(c.getIp(), c.getPort());
            tracker.onProbeSent(k);

            // Yeterince yanıt gelmediyse: düşür + odadakilere CLIENT_LEFT yayınla
            if (tracker.shouldDrop(k, maxMissed)) {
                clientRepo.deleteById(c.getId());
                List<ClientDoc> remain = clientRepo.findByRoomId(c.getRoomId());
                String leftMsg = "CLIENT_LEFT|" + c.getIp() + ":" + c.getPort();
                for (ClientDoc r : remain) {
                    messenger.send(r.getIp(), r.getPort(), leftMsg);
                }
                tracker.clear(k);
            }
        }
    }

    /** Handler PONG gördüğünde burayı çağıracak. */
    public void onPong(String ip, int port) {
        tracker.onPong(key(ip, port));
    }
}
