package com.learniq.common.tenant.flyway;

import com.learniq.common.tenant.TenantSchemaValidator;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

/**
 * Kiracı şeması migrasyonlarının tek noktadan koşulması.
 * Startup stratejisi (FlywayTenantConfig) ve platform provisioning
 * (auth-service) aynı sınıfı kullanır — migration SQL'leri tek kaynakta
 * (common-library: db/migration/tenant) tutulur, servis kopyası oluşmaz.
 */
public final class TenantSchemaMigrator {

    private TenantSchemaMigrator() {}

    /**
     * Veritabanı düzeyinde kiracı şemasına tenant migration'larını uygular.
     * Şema adı TenantSchemaValidator ile doğrulanır (SQL enjeksiyon kapısı yok).
     */
    public static void migrateTenant(DataSource dataSource, String tenantSchema) {
        String schema = TenantSchemaValidator.requireValid(tenantSchema);
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration/tenant")
                .baselineOnMigrate(true)
                .validateOnMigrate(false)
                .load();
        flyway.repair();
        flyway.migrate();
    }
}
