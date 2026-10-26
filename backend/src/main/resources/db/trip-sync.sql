CREATE TABLE IF NOT EXISTS citygo_trip (
 trip_id CHAR(36) PRIMARY KEY,
 user_id BIGINT NOT NULL,
 version BIGINT NOT NULL,
 title VARCHAR(120) NOT NULL,
 document_json LONGTEXT NOT NULL,
 updated_at BIGINT NOT NULL,
 INDEX idx_citygo_trip_owner (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS citygo_trip_revision (
 trip_id CHAR(36) NOT NULL,
 version BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 mutation_id CHAR(36) NOT NULL,
 request_hash CHAR(64) NOT NULL,
 title VARCHAR(120) NOT NULL,
 document_json LONGTEXT NOT NULL,
 updated_at BIGINT NOT NULL,
 PRIMARY KEY(trip_id, version),
 UNIQUE KEY uk_citygo_trip_mutation(user_id, mutation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS citygo_trip_current (
 user_id BIGINT PRIMARY KEY,
 trip_id CHAR(36) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
