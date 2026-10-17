-- Explicit publication, private by default. Only mapping lives in physical database zero.
USE yunanexus_file_0;
CREATE TABLE IF NOT EXISTS file_publication (
 file_id BIGINT NOT NULL PRIMARY KEY,
 user_id BIGINT NOT NULL,
 token CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_publication_owner(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
