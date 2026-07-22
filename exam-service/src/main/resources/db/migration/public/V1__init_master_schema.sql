-- Create tenants table in the master public schema
CREATE TABLE tenants (
    id VARCHAR(50) PRIMARY KEY, -- e.g., "academy_1"
    name VARCHAR(255) NOT NULL,
    domain VARCHAR(255) UNIQUE,
    subscription_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create global users (System Admins) who can manage tenants
CREATE TABLE global_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    role VARCHAR(50) NOT NULL, -- e.g., SUPER_ADMIN
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
