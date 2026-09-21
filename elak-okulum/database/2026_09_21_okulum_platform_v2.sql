-- ELAK Okulum 0.9 / Platform v2
-- Ortak öğrenci-veli, bildirim ve okul akışı veri omurgası.
-- Mevcut tablo ve modülleri bozmaz; yeni merkezi mobil deneyim için ek tablolardır.

CREATE TABLE IF NOT EXISTS students (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT UNSIGNED NOT NULL,
    school_no VARCHAR(40) NOT NULL,
    full_name VARCHAR(180) NOT NULL,
    class_name VARCHAR(140) NULL,
    grade VARCHAR(20) NULL,
    school_level ENUM('middle','high','other') NOT NULL DEFAULT 'other',
    phone VARCHAR(40) NULL,
    photo_url VARCHAR(500) NULL,
    status TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_students_school_no (school_id,school_no),
    KEY idx_students_school_class (school_id,class_name,status),
    CONSTRAINT fk_students_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS guardians (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT UNSIGNED NOT NULL,
    full_name VARCHAR(180) NULL,
    phone VARCHAR(40) NOT NULL,
    relationship_default VARCHAR(60) NULL,
    password_hash VARCHAR(255) NULL,
    phone_verified_at DATETIME NULL,
    last_login_at DATETIME NULL,
    status TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_guardian_school_phone (school_id,phone),
    KEY idx_guardian_school_status (school_id,status),
    CONSTRAINT fk_guardian_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS student_guardians (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT UNSIGNED NOT NULL,
    guardian_id BIGINT UNSIGNED NOT NULL,
    relationship VARCHAR(60) NULL,
    is_primary TINYINT(1) NOT NULL DEFAULT 0,
    can_receive_notifications TINYINT(1) NOT NULL DEFAULT 1,
    can_approve TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_student_guardian (student_id,guardian_id),
    KEY idx_sg_guardian (guardian_id,student_id),
    CONSTRAINT fk_sg_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_sg_guardian FOREIGN KEY (guardian_id) REFERENCES guardians(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS mobile_devices (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT UNSIGNED NOT NULL,
    owner_type ENUM('user','guardian') NOT NULL,
    owner_id BIGINT UNSIGNED NOT NULL,
    platform ENUM('android','ios','web') NOT NULL DEFAULT 'android',
    device_id VARCHAR(190) NULL,
    push_token VARCHAR(500) NULL,
    app_version VARCHAR(40) NULL,
    last_seen_at DATETIME NULL,
    status TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_mobile_owner (owner_type,owner_id,status),
    KEY idx_mobile_school (school_id,status),
    CONSTRAINT fk_mobile_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS announcements (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT UNSIGNED NOT NULL,
    created_by BIGINT UNSIGNED NULL,
    type ENUM('announcement','news','urgent','document') NOT NULL DEFAULT 'announcement',
    title VARCHAR(240) NOT NULL,
    body TEXT NOT NULL,
    attachment_url VARCHAR(600) NULL,
    target_type ENUM('school','level','class','student','user') NOT NULL DEFAULT 'school',
    target_value VARCHAR(180) NULL,
    publish_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NULL,
    status TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_ann_school_date (school_id,publish_at,status),
    KEY idx_ann_target (school_id,target_type,target_value),
    CONSTRAINT fk_ann_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
    CONSTRAINT fk_ann_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT UNSIGNED NOT NULL,
    category ENUM('attendance','private','general','appointment','exam','permission','survey','trip','payment','system') NOT NULL DEFAULT 'general',
    title VARCHAR(240) NOT NULL,
    body TEXT NOT NULL,
    entity_type VARCHAR(80) NULL,
    entity_id BIGINT UNSIGNED NULL,
    student_id BIGINT UNSIGNED NULL,
    sender_user_id BIGINT UNSIGNED NULL,
    priority ENUM('normal','high','urgent') NOT NULL DEFAULT 'normal',
    deep_link VARCHAR(600) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_notif_school_date (school_id,created_at),
    KEY idx_notif_student (student_id,created_at),
    CONSTRAINT fk_notif_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
    CONSTRAINT fk_notif_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE SET NULL,
    CONSTRAINT fk_notif_sender FOREIGN KEY (sender_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS notification_deliveries (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    notification_id BIGINT UNSIGNED NOT NULL,
    recipient_type ENUM('user','guardian') NOT NULL,
    recipient_id BIGINT UNSIGNED NOT NULL,
    delivered_at DATETIME NULL,
    read_at DATETIME NULL,
    push_status ENUM('pending','sent','failed','not_registered') NOT NULL DEFAULT 'pending',
    error_message VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_notif_recipient (notification_id,recipient_type,recipient_id),
    KEY idx_delivery_recipient (recipient_type,recipient_id,read_at),
    CONSTRAINT fk_delivery_notification FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS attendance_records (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT UNSIGNED NOT NULL,
    student_id BIGINT UNSIGNED NOT NULL,
    attendance_date DATE NOT NULL,
    lesson_no SMALLINT NULL,
    status ENUM('present','absent','late','excused','duty') NOT NULL DEFAULT 'present',
    minutes_late SMALLINT NULL,
    teacher_user_id BIGINT UNSIGNED NULL,
    note VARCHAR(500) NULL,
    source VARCHAR(80) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_attendance_slot (student_id,attendance_date,lesson_no),
    KEY idx_attendance_school_date (school_id,attendance_date,status),
    CONSTRAINT fk_attendance_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_teacher FOREIGN KEY (teacher_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS teacher_comments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT UNSIGNED NOT NULL,
    student_id BIGINT UNSIGNED NOT NULL,
    teacher_user_id BIGINT UNSIGNED NOT NULL,
    subject_name VARCHAR(120) NULL,
    comment_text TEXT NOT NULL,
    visibility ENUM('guardian','staff') NOT NULL DEFAULT 'guardian',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_comment_student_date (student_id,created_at),
    CONSTRAINT fk_comment_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_teacher FOREIGN KEY (teacher_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS exam_calendar (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT UNSIGNED NOT NULL,
    class_name VARCHAR(140) NOT NULL,
    subject_name VARCHAR(140) NOT NULL,
    exam_title VARCHAR(200) NULL,
    exam_date DATE NOT NULL,
    start_time TIME NULL,
    description VARCHAR(600) NULL,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_exam_school_class_date (school_id,class_name,exam_date),
    CONSTRAINT fk_exam_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
    CONSTRAINT fk_exam_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS class_schedule_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT UNSIGNED NOT NULL,
    class_name VARCHAR(140) NOT NULL,
    weekday TINYINT NOT NULL,
    lesson_no SMALLINT NOT NULL,
    subject_name VARCHAR(140) NOT NULL,
    teacher_user_id BIGINT UNSIGNED NULL,
    start_time TIME NULL,
    end_time TIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_schedule_slot (school_id,class_name,weekday,lesson_no),
    KEY idx_schedule_teacher (teacher_user_id,weekday,lesson_no),
    CONSTRAINT fk_schedule_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_teacher FOREIGN KEY (teacher_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS surveys (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(240) NOT NULL,
    description TEXT NULL,
    target_type ENUM('school','level','class','student') NOT NULL DEFAULT 'school',
    target_value VARCHAR(180) NULL,
    starts_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ends_at DATETIME NULL,
    status TINYINT(1) NOT NULL DEFAULT 1,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_survey_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
    CONSTRAINT fk_survey_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS survey_questions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    survey_id BIGINT UNSIGNED NOT NULL,
    question_text TEXT NOT NULL,
    question_type ENUM('single','multiple','text','yes_no') NOT NULL DEFAULT 'single',
    sort_order INT NOT NULL DEFAULT 100,
    required TINYINT(1) NOT NULL DEFAULT 1,
    CONSTRAINT fk_question_survey FOREIGN KEY (survey_id) REFERENCES surveys(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS survey_options (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT UNSIGNED NOT NULL,
    option_text VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 100,
    CONSTRAINT fk_option_question FOREIGN KEY (question_id) REFERENCES survey_questions(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS survey_responses (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    survey_id BIGINT UNSIGNED NOT NULL,
    question_id BIGINT UNSIGNED NOT NULL,
    guardian_id BIGINT UNSIGNED NULL,
    user_id BIGINT UNSIGNED NULL,
    student_id BIGINT UNSIGNED NULL,
    option_id BIGINT UNSIGNED NULL,
    answer_text TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_survey_response_owner (survey_id,guardian_id,user_id),
    CONSTRAINT fk_response_survey FOREIGN KEY (survey_id) REFERENCES surveys(id) ON DELETE CASCADE,
    CONSTRAINT fk_response_question FOREIGN KEY (question_id) REFERENCES survey_questions(id) ON DELETE CASCADE,
    CONSTRAINT fk_response_guardian FOREIGN KEY (guardian_id) REFERENCES guardians(id) ON DELETE CASCADE,
    CONSTRAINT fk_response_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_response_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE SET NULL,
    CONSTRAINT fk_response_option FOREIGN KEY (option_id) REFERENCES survey_options(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS trips (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(240) NOT NULL,
    trip_date DATE NOT NULL,
    destination VARCHAR(300) NULL,
    fee DECIMAL(10,2) NULL,
    description TEXT NULL,
    target_type ENUM('school','level','class','student') NOT NULL DEFAULT 'class',
    target_value VARCHAR(180) NULL,
    response_deadline DATETIME NULL,
    created_by BIGINT UNSIGNED NULL,
    status TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_trip_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS trip_responses (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT UNSIGNED NOT NULL,
    student_id BIGINT UNSIGNED NOT NULL,
    guardian_id BIGINT UNSIGNED NOT NULL,
    response ENUM('accepted','rejected') NOT NULL,
    note VARCHAR(500) NULL,
    responded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_trip_student (trip_id,student_id),
    CONSTRAINT fk_trip_response_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_response_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_response_guardian FOREIGN KEY (guardian_id) REFERENCES guardians(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS conversations (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT UNSIGNED NOT NULL,
    student_id BIGINT UNSIGNED NULL,
    subject VARCHAR(240) NULL,
    status ENUM('open','closed') NOT NULL DEFAULT 'open',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_conversation_student (student_id,updated_at),
    CONSTRAINT fk_conversation_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS conversation_members (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT UNSIGNED NOT NULL,
    member_type ENUM('user','guardian') NOT NULL,
    member_id BIGINT UNSIGNED NOT NULL,
    last_read_at DATETIME NULL,
    UNIQUE KEY uq_conversation_member (conversation_id,member_type,member_id),
    CONSTRAINT fk_member_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT UNSIGNED NOT NULL,
    sender_type ENUM('user','guardian') NOT NULL,
    sender_id BIGINT UNSIGNED NOT NULL,
    body TEXT NULL,
    attachment_url VARCHAR(600) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_message_conversation_date (conversation_id,created_at),
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS app_events (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT UNSIGNED NOT NULL,
    student_id BIGINT UNSIGNED NULL,
    event_type VARCHAR(80) NOT NULL,
    title VARCHAR(240) NOT NULL,
    summary VARCHAR(700) NULL,
    event_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_module VARCHAR(80) NULL,
    source_entity_id BIGINT UNSIGNED NULL,
    deep_link VARCHAR(600) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_event_school_date (school_id,event_at),
    KEY idx_event_student_date (student_id,event_at),
    CONSTRAINT fk_event_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
    CONSTRAINT fk_event_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE SET NULL
) ENGINE=InnoDB;
