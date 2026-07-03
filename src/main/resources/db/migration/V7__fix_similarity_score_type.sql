-- Align column type with entity mapping (Double -> double precision)
ALTER TABLE face_verification_log ALTER COLUMN similarity_score TYPE DOUBLE PRECISION;
