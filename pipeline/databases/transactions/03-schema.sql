\connect DB_NAME;

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS roles (
    role_id UUID NOT NULL DEFAULT uuid_generate_v4(),
    role_name VARCHAR(60) NOT NULL UNIQUE,
    PRIMARY KEY (role_id)
);

CREATE TABLE IF NOT EXISTS users (
    user_id UUID NOT NULL DEFAULT uuid_generate_v4(),
    role_id UUID NOT NULL,
    user_name VARCHAR(255) NOT NULL,
    user_email VARCHAR(100) NOT NULL UNIQUE,
    image_url VARCHAR(255) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    email_verified BOOLEAN NOT NULL,
    is_expired BOOLEAN NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_role_id FOREIGN KEY (role_id) REFERENCES roles(role_id)
);
