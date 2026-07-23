-- Инициализация схемы user_profile для production
-- Создает схему и таблицы, если они не существуют

CREATE SCHEMA IF NOT EXISTS user_profile;

-- Таблица пользователей (проекция без пароля)
CREATE TABLE IF NOT EXISTS user_profile.users (
    id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Таблица ролей пользователей
CREATE TABLE IF NOT EXISTS user_profile.user_roles (
    user_id VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL CHECK (role IN ('ROLE_USER','ROLE_MANAGER','ROLE_ADMIN')),
    PRIMARY KEY (user_id, role)
);

-- Индексы для улучшения производительности
CREATE INDEX IF NOT EXISTS idx_user_profile_users_username ON user_profile.users(username);
CREATE INDEX IF NOT EXISTS idx_user_profile_users_email ON user_profile.users(email);
CREATE INDEX IF NOT EXISTS idx_user_profile_user_roles_user_id ON user_profile.user_roles(user_id);
