CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE medications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    dosage VARCHAR(120) NOT NULL,
    frequency VARCHAR(120) NOT NULL,
    instructions VARCHAR(2000),
    start_date DATE NOT NULL,
    end_date DATE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_medications_patient FOREIGN KEY (patient_id) REFERENCES users (id)
);
CREATE INDEX idx_medications_patient ON medications (patient_id);

CREATE TABLE medication_schedules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    medication_id BIGINT NOT NULL,
    frequency VARCHAR(24) NOT NULL,
    time_of_day TIME NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_schedule_medication FOREIGN KEY (medication_id) REFERENCES medications (id) ON DELETE CASCADE
);
CREATE INDEX idx_schedule_medication_active ON medication_schedules (medication_id, active);

CREATE TABLE dose_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    schedule_id BIGINT NOT NULL,
    scheduled_date DATE NOT NULL,
    scheduled_time TIME NOT NULL,
    status VARCHAR(16) NOT NULL,
    taken_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_dose_schedule_date_time UNIQUE (schedule_id, scheduled_date, scheduled_time),
    CONSTRAINT fk_dose_schedule FOREIGN KEY (schedule_id) REFERENCES medication_schedules (id) ON DELETE CASCADE
);
CREATE INDEX idx_dose_date_schedule ON dose_records (scheduled_date, schedule_id);

CREATE TABLE care_relationships (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    related_user_id BIGINT NOT NULL,
    relationship_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_relationship_patient FOREIGN KEY (patient_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_relationship_related_user FOREIGN KEY (related_user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_relationship_patient_status ON care_relationships (patient_id, status);
CREATE INDEX idx_relationship_related_status ON care_relationships (related_user_id, status);

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_id BIGINT NOT NULL,
    notification_type VARCHAR(32) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    related_entity_type VARCHAR(32) NOT NULL,
    related_entity_id BIGINT NOT NULL,
    deduplication_key VARCHAR(240) NOT NULL,
    is_read BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_recipient_type_event UNIQUE (recipient_id, notification_type, deduplication_key),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_notifications_recipient ON notifications (recipient_id);
CREATE INDEX idx_notifications_recipient_read ON notifications (recipient_id, is_read);
CREATE INDEX idx_notifications_recipient_created ON notifications (recipient_id, created_at);
