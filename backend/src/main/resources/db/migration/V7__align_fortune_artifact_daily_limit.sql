-- Align fortune artifacts with the product spec and enforce one fortune per user per KST date.
ALTER TABLE fortune_artifact
    ALTER COLUMN description TYPE JSONB
    USING description::jsonb;

ALTER TABLE fortune_artifact
    ADD COLUMN user_id UUID,
    ADD COLUMN fortune_date DATE;

UPDATE fortune_artifact fa
SET user_id = owner.user_id
FROM (
    SELECT artifact_id, MIN(user_id::TEXT)::UUID AS user_id
    FROM gallery
    WHERE deleted_at IS NULL
    GROUP BY artifact_id
) owner
WHERE fa.artifact_id = owner.artifact_id
  AND fa.user_id IS NULL;

UPDATE fortune_artifact fa
SET fortune_date = artifact.created_at::DATE
FROM artifact
WHERE fa.artifact_id = artifact.id
  AND fa.fortune_date IS NULL;

ALTER TABLE fortune_artifact
    ALTER COLUMN user_id SET NOT NULL,
    ALTER COLUMN fortune_date SET NOT NULL;

ALTER TABLE fortune_artifact
    ADD CONSTRAINT fk_fortune_artifact_user FOREIGN KEY (user_id) REFERENCES app_user (id);

CREATE UNIQUE INDEX uq_fortune_artifact_user_date
    ON fortune_artifact (user_id, fortune_date);
