-- Copy of user-db/init.sql from the repository root, which docker-compose mounts into the user-db
-- container. The chart keeps its own copy because Helm cannot read files outside the chart
-- directory; `make k3d-up` installs the chart on its own.

-- Create schema
CREATE SCHEMA IF NOT EXISTS user_profile;

-- Users table
CREATE TABLE IF NOT EXISTS user_profile.users (
    id VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    PRIMARY KEY (id)
);

-- User roles table
CREATE TABLE IF NOT EXISTS user_profile.user_roles (
    user_id VARCHAR(255) NOT NULL,
    role VARCHAR(50) CHECK (role IN ('ROLE_USER', 'ROLE_MANAGER', 'ROLE_ADMIN')),
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id)
        REFERENCES user_profile.users(id) ON DELETE CASCADE
);
