ALTER TABLE medication_schedules ADD COLUMN dose_size BIGINT NULL;
ALTER TABLE medication_schedules ADD COLUMN dose_size_verified BOOLEAN NOT NULL DEFAULT FALSE;
