ALTER TABLE dose_records DROP FOREIGN KEY fk_dose_schedule;
ALTER TABLE dose_records
    ADD CONSTRAINT fk_dose_schedule
        FOREIGN KEY (schedule_id) REFERENCES medication_schedules (id) ON DELETE RESTRICT;

ALTER TABLE medication_schedules DROP FOREIGN KEY fk_schedule_medication;
ALTER TABLE medication_schedules
    ADD CONSTRAINT fk_schedule_medication
        FOREIGN KEY (medication_id) REFERENCES medications (id) ON DELETE RESTRICT;
