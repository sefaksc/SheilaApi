package com.sheila.api.infrastructure.config;

import com.sheila.api.core.model.ApplicationDoc;
import com.sheila.api.infrastructure.repository.ApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Uygulama başlarken mock Application kayıtları ekler.
 * - Idempotent (aynı isim varsa tekrar oluşturmaz)
 * - app.seed.enabled=false ile kapatılabilir (prod için önerilir)
 */
@Configuration
public class SeedConfig {

    private static final Logger log = LoggerFactory.getLogger(SeedConfig.class);

    @Value("${app.seed.enabled:true}")
    private boolean seedEnabled;

    @Bean
    public ApplicationRunner seedApplications(ApplicationRepository appRepo) {
        return args -> {
            if (!seedEnabled) {
                log.info("[Seed] app.seed.enabled=false → seed atlandı");
                return;
            }

            List<ApplicationDoc> seeds = List.of(
                    new ApplicationDoc("demo-app", 1000),
                    new ApplicationDoc("game-app", 500),
                    new ApplicationDoc("video-app", 300)
            );

            int created = 0;
            for (ApplicationDoc seed : seeds) {
                boolean exists = appRepo.findByName(seed.getName()).isPresent();
                if (!exists) {
                    appRepo.save(seed);
                    created++;
                    log.info("[Seed] Application eklendi: name='{}', capacity={}", seed.getName(), seed.getCapacity());
                } else {
                    log.debug("[Seed] Zaten mevcut: name='{}' → atlandı", seed.getName());
                }
            }

            log.info("[Seed] Tamamlandı. Yeni oluşturulan Application sayısı: {}", created);
        };
    }
}
