USE elak_okulum;

ALTER TABLE users
    MODIFY COLUMN role ENUM('school_admin','manager','teacher','security','dormitory','guardian','student','staff') NOT NULL DEFAULT 'teacher',
    ADD COLUMN school_scope ENUM('middle','high','both') NOT NULL DEFAULT 'both' AFTER role;

CREATE TABLE IF NOT EXISTS user_roles (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    role_key ENUM('school_admin','manager','teacher','security','dormitory','guardian','student','staff') NOT NULL,
    is_primary TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_user_role (user_id, role_key),
    KEY idx_user_roles_user (user_id,is_primary),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

INSERT IGNORE INTO user_roles(user_id, role_key, is_primary)
SELECT id, role, 1 FROM users;

CREATE TABLE IF NOT EXISTS user_permissions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    permission_key VARCHAR(120) NOT NULL,
    is_allowed TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_user_permission (user_id, permission_key),
    KEY idx_user_permissions_user (user_id,is_allowed),
    CONSTRAINT fk_user_permissions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_links (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    link_type ENUM('student','class','teacher','guardian','staff') NOT NULL,
    external_id VARCHAR(100) NOT NULL,
    label VARCHAR(180) NULL,
    school_scope ENUM('middle','high','both') NOT NULL DEFAULT 'both',
    meta_json TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_user_link (user_id, link_type, external_id),
    KEY idx_user_links_user_type (user_id,link_type),
    CONSTRAINT fk_user_links_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS auth_sessions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    access_token_hash CHAR(64) NOT NULL UNIQUE,
    refresh_token_hash CHAR(64) NOT NULL UNIQUE,
    access_expires_at DATETIME NOT NULL,
    refresh_expires_at DATETIME NOT NULL,
    device_name VARCHAR(180) NULL,
    app_version VARCHAR(40) NULL,
    last_used_at DATETIME NULL,
    revoked_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_auth_sessions_access (access_token_hash,access_expires_at,revoked_at),
    KEY idx_auth_sessions_refresh (refresh_token_hash,refresh_expires_at,revoked_at),
    KEY idx_auth_sessions_user (user_id,revoked_at),
    CONSTRAINT fk_auth_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

INSERT IGNORE INTO modules(module_key,title,description,category,icon,default_color,module_type,sort_order) VALUES
('veli_randevu','Veli Randevu','Öğretmen görüşme saatleri ve randevular','İletişim','◷','#7C3AED','web',45),
('akilli_tahta','Akıllı Tahta','QR, kilit ve cihaz yönetimi','Teknoloji','QR','#2563EB','web',55),
('anketler','Anket & Onay','Veli görüşü, izin ve tercih anketleri','İletişim','☑','#059669','internal',75),
('etkinlik_gezi','Etkinlik & Gezi','Katılım ve veli onay süreçleri','Etkinlik','⌖','#EA580C','internal',85);
