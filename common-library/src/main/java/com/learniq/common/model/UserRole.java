package com.learniq.common.model;

public enum UserRole {
    SUPER_ADMIN,  // Platform-level: manages all tenants (public.global_users)
    ADMIN,        // Tenant-level: manages one academy
    TEACHER,      // Tenant-level: sees own classroom students
    STUDENT       // Tenant-level: sees own data only
}
