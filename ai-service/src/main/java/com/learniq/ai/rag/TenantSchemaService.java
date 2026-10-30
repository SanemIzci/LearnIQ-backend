package com.learniq.ai.rag;

import com.learniq.common.tenant.TenantSchemaValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Tenant şeması geçişini tek noktadan yönetir.
 * Şema adı SQL string'ine birleştirilmeden ÖNCE TenantSchemaValidator ile doğrulanır;
 * böylece kiracı kimliği üzerinden search_path enjeksiyonu imkânsız hale gelir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantSchemaService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Geçerli tenant şeması için: SET search_path TO <schema>
     * Geçersiz şema adında IllegalArgumentException fırlatır (Kalıcı hata → DLT).
     */
    public void switchTo(String tenantSchema) {
        String validated = TenantSchemaValidator.requireValid(tenantSchema);
        jdbcTemplate.execute("SET search_path TO " + validated);
        log.debug("search_path geçişi: {} şemasına yapıldı", validated);
    }
}
