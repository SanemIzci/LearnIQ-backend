package com.learniq.common.tenant.hibernate;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class HibernateMultiTenancyConfig implements HibernatePropertiesCustomizer {

    private final TenantIdentifierResolver tenantIdentifierResolver;
    private final SchemaMultiTenantConnectionProvider multiTenantConnectionProvider;

    public HibernateMultiTenancyConfig(TenantIdentifierResolver tenantIdentifierResolver,
                                       SchemaMultiTenantConnectionProvider multiTenantConnectionProvider) {
        this.tenantIdentifierResolver = tenantIdentifierResolver;
        this.multiTenantConnectionProvider = multiTenantConnectionProvider;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, multiTenantConnectionProvider);
    }
}
