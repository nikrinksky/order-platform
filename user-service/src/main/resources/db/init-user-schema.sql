-- Инициализация схемы user_profile для user-service

-- Создание схемы user_profile
CREATE SCHEMA IF NOT EXISTS user_profile;

-- Таблица проекций пользователей в схеме user_profile (без пароля)
CREATE TABLE IF NOT EXISTS user_profile.users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Индексы для улучшения производительности
CREATE INDEX IF NOT EXISTS idx_user_profile_users_username ON user_profile.users(username);
CREATE INDEX IF NOT EXISTS idx_user_profile_users_email ON user_profile.users(email);
