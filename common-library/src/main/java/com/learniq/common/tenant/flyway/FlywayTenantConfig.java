package com.learniq.common.tenant.flyway;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class FlywayTenantConfig {

    /**
     * Custom Flyway Migration Strategy to handle Multi-Tenancy.
     * Spring Boot will invoke this automatically instead of the default behavior.
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(DataSource dataSource,
                                                           @Value("${spring.application.name}") String appName) {
        return flyway -> {
            // 1. Migrate the default (public) schema first
            // This ensures master tables like 'tenants' exist.
            Flyway publicFlyway = Flyway.configure()
                    .dataSource(dataSource)
                    .schemas("public")
                    .locations("classpath:db/migration/public")
                    .baselineOnMigrate(true)
                    .load();
            publicFlyway.migrate();

            // 2. We only want to run tenant migrations if we are in a service that needs them
            // (e.g., exam-service). Auth service primarily manages the public schema.
            if (!appName.equals("auth-service")) {
                // Fetch existing tenants from the master schema
                List<String> tenants = getExistingTenants(dataSource);

                // 3. Iterate over each tenant and run tenant-specific migrations
                for (String tenant : tenants) {
                    Flyway tenantFlyway = Flyway.configure()
                            .dataSource(dataSource)
                            .schemas(tenant) // Set schema to 'tenant_xyz'
                            .defaultSchema(tenant)
                            .locations("classpath:db/migration/tenant")
                            .baselineOnMigrate(true)
                            .load();
                    tenantFlyway.migrate();
                }
            }
        };
    }

    private List<String> getExistingTenants(DataSource dataSource) {
        List<String> tenants = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT id FROM public.tenants WHERE subscription_status = 'ACTIVE'")) {
            
            while (rs.next()) {
                tenants.add(rs.getString("id"));
            }
        } catch (Exception e) {
            // If table doesn't exist yet (very first run), just ignore.
            System.err.println("Could not fetch tenants: " + e.getMessage());
        }
        return tenants;
    }
}
