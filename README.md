# SheilaApi — UDP Lobby/Room Server (Spring Boot + Netty + MongoDB)

**SheilaApi**; C++ gibi UDP tabanlı istemcilerden gelen odalaşma isteklerini karşılayan, Netty ile yazılmış bir UDP sunucusudur.  
Uygulama/oda/katılımcı bilgileri MongoDB’de saklanır. Server-probe (SRV_PING→PONG) ile stay alive kontrolü yapar ve düşen istemcileri odadakilere bildirir.

---

## 🚀 Özellikler

- **UDP Protokolü (Netty):** `JOIN`, `LIST`, `LEAVE`, `PONG` komutları; `ROOM`, `NEW_CLIENT`, `CLIENT_LEFT`, `SRV_PING`, `ERR` yanıtları
- **Server-probe (stay alive):** Sunucu periyodik `SRV_PING` yollar, istemci `PONG` döner. Yanıt yoksa düşür ve broadcast et
- **Oda kapasitesi:** Oda oluşturulurken kapasite verilebilir; verilmezse varsayılan kullanılır
- **Uygulama kapasitesi:** Bir uygulamadaki tüm oda kapasitelerinin toplamı, uygulama kapasitesini geçemez
- **Idempotent JOIN:** Aynı ip:port tekrar JOIN atarsa kayıt yenilenir, çoğalmaz
- **Seed:** Başlangıçta örnek uygulamalar eklenebilir (dev/test kolaylığı)

---

## 🧱 Mimari

```
com.sheila.api
├─ core
│  ├─ model         # ApplicationDoc, RoomDoc, ClientDoc
│  ├─ dto           # Endpoint, RoomJoinResult
│  └─ exception     # AppNotFound, RoomFull, ApplicationCapacityExceeded...
├─ application
│  ├─ impl          # RoomServiceImpl (iş kuralları)
│  └─ ServerProber  # SRV_PING planlayıcısı (aktif)
├─ infrastructure
│  ├─ repository    # Spring Data Mongo repo'ları
│  └─ config        # Mongo/Scheduler/Seed konfigürasyonları
└─ transport
   └─ udp           # Netty UDP katmanı (UdpServer, UdpServerHandler, UdpMessenger, NetUtil)
```

### Bileşenler

- **UdpServer / Handler:** UDP paketlerini alır, RoomService’e yönlendirir, yanıt/broadcast gönderir
- **RoomServiceImpl:** Oda/uygulama kapasite kuralları, JOIN/LEAVE/LIST iş mantığı
- **ServerProber:** Periyodik `SRV_PING|<appName>|<roomName>`; `PONG` gelmeyeni düşürür
- **Mongo Repositories:** ApplicationRepository, RoomRepository, ClientRepository
- **SeedConfig:** İsteyene göre örnek uygulamalar ekler (idempotent)

---

## 🗃️ Veri Modeli (Özet)

- **ApplicationDoc:** `id`, `name` (benzersiz), `capacity` (opsiyonel; toplam oda kap. üst sınırı)
- **RoomDoc:** `id`, `applicationId`, `name` (app içinde benzersiz), `capacity`
- **ClientDoc:** `id`, `roomId`, `ip`, `port`, `lastSeen`

> **Not:** `ClientDoc.lastSeen` alanı, TTL index ile (örn. 24 saat) otomatik süpürülebilir. Biz anlık düşürme için server-probe kullanıyoruz.

---

## ⚙️ Konfigürasyon

**src/main/resources/application.yml** örneği:
```yaml
spring:
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017/sheila}

app:
  udp:
    port: 9876

  rooms:
    defaultCapacity: 100    # oda kapasitesi verilmezse kullanılır

  # Pasif presence kapalı; server-probe aktif
  presence:
    enabled: false
    checkIntervalMs: 10000
    timeoutMs: 30000

  probe:
    enabled: true
    intervalMs: 10000       # 10 saniyede bir SRV_PING
    maxMissed: 3            # üst üste 3 PING cevapsız → düşür

  seed:
    enabled: true           # demo-app vb. tohum verisi

logging:
  level:
    com.sheila.api: INFO
    # Gerekirse prober için:
    # com.sheila.api.application.ServerProber: DEBUG
```

**Ortam değişkeni ile Mongo Atlas:**
```sh
setx MONGODB_URI "mongodb+srv://<user>:<pass>@<cluster>/sheila?retryWrites=true&w=majority"
```

---

## 🛠️ Kurulum & Çalıştırma

Gereksinimler: Java 17+, Maven, MongoDB (lokal veya Atlas), Windows Firewall’da Java’ya izin

```sh
# Derle
.\mvnw.cmd clean verify

# Uygulamayı başlat
.\mvnw.cmd spring-boot:run
```

Log’da: `Netty UDP Server listening on port 9876` görmelisiniz.

Lokal testte IPv6/IPv4 karışıklığını önlemek için JVM’i IPv4’e zorlamak isterseniz:
```sh
.\mvnw.cmd spring-boot:run -Dspring-boot.run.jvmArguments="-Djava.net.preferIPv4Stack=true"
```

---

## 📡 UDP Protokolü

Metin tabanlı, UTF-8, alanlar `|` ile ayrılır.

### İstemci → Sunucu

- **JOIN:** `JOIN|<appName>|<roomName>|[capacity]`  
  `capacity` sadece oda ilk oluşturulurken dikkate alınır; mevcut odada yok sayılır.
- **LIST:** `LIST|<appName>|<roomName>`
- **LEAVE:** `LEAVE|<appName>|<roomName>`
- **PONG:** `PONG|<appName>|<roomName>` — sunucunun `SRV_PING`’ine yanıt  
  > İstemcinin IP/port’u payload’dan okunmaz; paketin kaynağından alınır.

### Sunucu → İstemci

- **ROOM:** `ROOM|<roomName>|clients=[ip:port,ip:port,...]`
- **NEW_CLIENT:** `NEW_CLIENT|ip:port` — odaya biri katıldı
- **CLIENT_LEFT:** `CLIENT_LEFT|ip:port` — odadan biri düştü/ayrıldı
- **SRV_PING:** `SRV_PING|<appName>|<roomName>` — hemen `PONG|...` dön
- **ERR:** `ERR|APP_NOT_FOUND|...` / `ERR|ROOM_FULL|...` / `ERR|APP_CAP_EXCEEDED|...` / `ERR|BAD_REQUEST|...`

---

### Davranışlar

- **JOIN:** Oda yoksa upsert ile oluşturulur (kapasite paramına göre, yoksa default).
- **Oda kapasitesi:** Üye sayısı >= room.capacity ise `ERR|ROOM_FULL`.
- **Uygulama kapasitesi:** toplam(oda.capacity) + yeni_oda.capacity > application.capacity ise yeni oda oluşturulmaz (`ERR|APP_CAP_EXCEEDED`).
- **Server-probe:** `intervalMs` aralığında `SRV_PING`; `maxMissed` kez yanıt gelmezse istemci düşürülür, odadakilere `CLIENT_LEFT` yayınlanır.

---

## 🧪 Nasıl Test Ederim?

1. **Sunucuyu açın**
    ```sh
    .\mvnw.cmd spring-boot:run
    ```

2. **Smoke Test Client (Java)**
    - Projede basit bir UDP istemci var: `com.sheila.api.tools.UdpSmokeClient`

    **Yöntem A — Java ile çalıştır**
    ```sh
    .\mvnw.cmd -q -DskipTests package
    java -cp target/classes com.sheila.api.tools.UdpSmokeClient
    # argümanlı:
    # java -cp target/classes com.sheila.api.tools.UdpSmokeClient 127.0.0.1 9876 demo-app room-alpha 50
    ```

    **Yöntem B — Maven Exec Plugin**
    - `pom.xml` (build→plugins) içerisine ekleyin:
      ```xml
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.2.0</version>
      </plugin>
      ```
    - Ardından:
      ```sh
      .\mvnw.cmd -q -DskipTests -Dexec.mainClass=com.sheila.api.tools.UdpSmokeClient exec:java
      ```

    **Client komutları (konsol):**
    ```
    LIST        → odayı listele
    LEAVE       → odadan ayrıl
    JOIN 50     → (opsiyonel cap ile) join
    QUIT        → client’ı kapat
    ```

    Çoklu istemci testi: Aynı client’ı iki terminalde çalıştırın.  
    İkinci join ile birincide `NEW_CLIENT|ip:port` görürsünüz.  
    LEAVE ile diğerinde `CLIENT_LEFT|ip:port` görünür.  
    Client’lardan birini kapatın, `SRV_PING → PONG` yok → diğeri `CLIENT_LEFT|...` alır.

---

## 🛠️ Sorun Giderme

- **Maven:** `Unknown lifecycle phase .cleanupDaemonThreads=false`  
  Tek satır çalıştırın; backtick/satır bölme kullanmayın.
  ```sh
  .\mvnw.cmd -q -DskipTests -Dexec.mainClass=com.sheila.api.tools.UdpSmokeClient exec:java
  ```

- **Sunucu SRV_PING göndermiyor**
  - `@EnableScheduling` açık mı? (SchedulerConfig)
  - UdpMessenger bind sonrası set ediliyor mu? (log)
  - IPv6 loopback sorunu olabilir: handler IP normalize ediliyor (`::1 → 127.0.0.1`).
  - Gerekirse IPv4’e zorlayın:  
    `-Djava.net.preferIPv4Stack=true`

- **APP_NOT_FOUND**
  - Seed kapalı olabilir (`app.seed.enabled: false`).  
    Mongo’da `applications` koleksiyonunu kontrol edin veya seed’i açın.

- **ROOM_FULL / APP_CAP_EXCEEDED**
  - Oda/uygulama kapasitelerini `application.yml` veya Mongo’da kontrol edin.

- **Port bağlanamıyor**
  - `9876` başka bir süreç tarafından kullanılıyor olabilir; `app.udp.port` değiştirin.

- **Firewall**
  - Windows güvenlik duvarı Java’ya UDP izni isteyebilir → **Allow**.

---

## 🧰 Üretim Notları

- **NAT uyumluluğu:** Aynı UDP soketini açık tutun; sunucu paketin kaynak IP:port’unu kullanır.
- **Paket boyutu:** UDP’de parçalanmayı önlemek için mesajları ~1.4 KB altında tutun.
- **Zamanlamalar:** `probe.intervalMs` ve `probe.maxMissed` değerlerini trafik/oyun tasarımına göre ayarlayın.
- **Kapasite yönetimi:** Yüksek yarış durumlarında daha katı kısıtlar gerekiyorsa, tek dokümanda sayaç tutma + Mongo transaction desenleri değerlendirilebilir.
- **Gözlemlenebilirlik:** Spring Actuator açık (`health/metrics`). Ek metrikler kolayca eklenebilir.
- **HTTP Admin (gelecek adım):** `/api/apps`, `/api/apps/{app}/rooms`, `/rooms/{room}/clients` read-only uçları ile izleme.

---

