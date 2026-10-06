UPDATE dose_records
SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP(6)
WHERE status = 'PENDING'
  AND schedule_id IN (
      SELECT s.id
      FROM medication_schedules s
      JOIN medications m ON m.id = s.medication_id
      WHERE s.active = FALSE OR m.active = FALSE
  );

ALTER TABLE dose_records
    ADD CONSTRAINT chk_dose_records_status
        CHECK (status IN ('PENDING', 'TAKEN', 'MISSED', 'SKIPPED', 'CANCELLED'));
