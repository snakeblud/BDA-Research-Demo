\connect DB_NAME;

-- INSERT INTO roles (role_name) VALUES
--     ('admin'),
--     ('user');

SET datestyle = 'DMY';
COPY tbank_users_cleaned FROM '/tbank_users_cleaned.csv' DELIMITER ',' CSV HEADER;
COPY tbank_cleaned FROM '/tbank_cleaned.csv' DELIMITER ',' CSV HEADER;