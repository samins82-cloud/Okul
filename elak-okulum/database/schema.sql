CREATE DATABASE IF NOT EXISTS elak_okulum CHARACTER SET utf8mb4 COLLATE utf8mb4_turkish_ci;
USE elak_okulum;

CREATE TABLE schools (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    school_code VARCHAR(32) NOT NULL UNIQUE,
    school_name VARCHAR(200) NOT NULL,
    short_name VARCHAR(100) NULL,
    logo_url VARCHAR(500) NULL,
    primary_color VARCHAR(20) NOT NULL DEFAULT '#183B66',
    secondary_color VARCHAR(20) NOT NULL DEFAULT '#FFFFFF',
    status TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE system_admins (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status TINYINT(1) NOT NULL DEFAULT 1,
    last_login_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE users (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(150) NOT NULL,
    username VARCHAR(80) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('school_admin','manager','teacher','security','dormitory') NOT NULL DEFAULT 'teacher',
    status TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_school_username (school_id, username),
    KEY idx_users_school_status (school_id,status),
    CONSTRAINT fk_users_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE modules (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    module_key VARCHAR(80) NOT NULL UNIQUE,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(255) NULL,
    category VARCHAR(80) NOT NULL DEFAULT 'Genel',
    icon VARCHAR(50) NULL,
    default_color VARCHAR(20) NULL,
    module_type ENUM('internal','web','external_app') NOT NULL DEFAULT 'web',
    web_url VARCHAR(500) NULL,
    android_package VARCHAR(180) NULL,
    sso_enabled TINYINT(1) NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 100,
    status TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE school_modules (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT UNSIGNED NOT NULL,
    module_id BIGINT UNSIGNED NOT NULL,
    custom_title VARCHAR(120) NULL,
    custom_color VARCHAR(20) NULL,
    custom_url VARCHAR(500) NULL,
    sort_order INT NOT NULL DEFAULT 100,
    is_enabled TINYINT(1) NOT NULL DEFAULT 1,
    UNIQUE KEY uq_school_module (school_id, module_id),
    KEY idx_sm_school_enabled (school_id,is_enabled,sort_order),
    CONSTRAINT fk_sm_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
    CONSTRAINT fk_sm_module FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE user_module_permissions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    module_id BIGINT UNSIGNED NOT NULL,
    can_view TINYINT(1) NOT NULL DEFAULT 1,
    can_manage TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uq_user_module (user_id, module_id),
    CONSTRAINT fk_ump_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ump_module FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE module_sso_tokens (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    school_id BIGINT UNSIGNED NOT NULL,
    module_id BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_sso_expiry (expires_at,used_at),
    CONSTRAINT fk_sso_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_sso_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
    CONSTRAINT fk_sso_module FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE auth_tokens (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    last_used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_auth_expiry (expires_at),
    CONSTRAINT fk_auth_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE audit_logs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    actor_type ENUM('system_admin','school_user','system') NOT NULL DEFAULT 'system',
    actor_id BIGINT UNSIGNED NULL,
    school_id BIGINT UNSIGNED NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(80) NULL,
    entity_id BIGINT UNSIGNED NULL,
    details TEXT NULL,
    ip_address VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_audit_school_date (school_id,created_at),
    KEY idx_audit_actor (actor_type,actor_id)
) ENGINE=InnoDB;

INSERT INTO modules (module_key,title,description,category,icon,default_color,module_type,sort_order) VALUES
('izin_takip','İzin Takip','Öğrenci çıkış ve dönüş işlemleri','Öğrenci','🪪','#E67E22','web',10),
('akilli_rehber','Akıllı Rehber','Öğrenci ve veli iletişim rehberi','İletişim','☎','#2E86C1','web',20),
('online_yoklama','Online Yoklama','Örgün, Açık Lise, DYK ve Sosyal Etkinlik yoklamaları','Yoklama','✓','#27AE60','internal',30),
('ders_programi','Ders Programı','Günlük ve haftalık ders programı','Akademik','▦','#8E44AD','web',40),
('duyurular','Duyurular','Okul ve personel duyuruları','İletişim','🔔','#C0392B','internal',50),
('belgeler','Belgeler','Sık kullanılan okul belge ve formları','Yönetim','▤','#566573','internal',60),
('lgs_yks','LGS / YKS','Deneme, performans ve akademik takip','Akademik','📊','#0F766E','web',70),
('ogretmen_islemleri','Öğretmen İşlemleri','Nöbet, devamsızlık ve personel işlemleri','Personel','👥','#7C3AED','internal',80);
