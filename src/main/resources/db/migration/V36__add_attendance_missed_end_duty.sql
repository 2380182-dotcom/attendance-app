-- The 11:59 PM job closes shifts where End Duty was never pressed. It used to
-- replace the status with MISSED_END_DUTY, which erased LATE. From now on the
-- status is kept (IN / LATE / ...) and this flag records the missed End Duty,
-- so a shift can be both late and missing End Duty.
-- Old rows are not changed: their missed End Duty is still the status
-- MISSED_END_DUTY (readers check either).
ALTER TABLE attendance ADD COLUMN missed_end_duty BOOLEAN NOT NULL DEFAULT FALSE;
