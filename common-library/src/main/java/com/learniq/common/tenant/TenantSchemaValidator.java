package com.learniq.common.tenant;

import java.util.regex.Pattern;

/**
 * Kiracı şema adlarını güvenli şekilde doğrular.
 *
 * "SET search_path TO ..." gibi string-birleştirmeli SQL ifadelerinde kullanılmadan
 * ÖNCE çağrılmalıdır; aksi halde kiracı kimliği üzerinden SQL enjeksiyonu mümkündür.
 *
 * Kural: küçük harf ile başlar, küçük harf/rakam/alt çizgi, maksimum 63 karakter.
 */
public final class TenantSchemaValidator {

    private static final Pattern SCHEMA_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

    private TenantSchemaValidator() {}

    /** Geçerli ise shema adını döner; geçersizse IllegalArgumentException fırlatır. */
    public static String requireValid(String tenantSchema) {
        if (tenantSchema == null || !SCHEMA_PATTERN.matcher(tenantSchema).matches()) {
            throw new IllegalArgumentException("Geçersiz kiracı şema adı: " + tenantSchema);
        }
        return tenantSchema;
    }

    public static boolean isValid(String tenantSchema) {
        return tenantSchema != null && SCHEMA_PATTERN.matcher(tenantSchema).matches();
    }
}
