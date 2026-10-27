-- A worker can nullify an acceptance when the task cannot be completed.
--
-- The row is never deleted: the signed acceptance is proof and belongs in the
-- audit history. Instead it moves ACTIVE -> WITHDRAWN, records why the worker
-- could not finish, and stays out of the worker's active work list.
ALTER TABLE acceptance ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE acceptance ADD COLUMN withdrawal_reasons JSONB;
ALTER TABLE acceptance ADD COLUMN withdrawal_comment TEXT;
ALTER TABLE acceptance ADD COLUMN withdrawn_at TIMESTAMPTZ;

ALTER TABLE acceptance ADD CONSTRAINT chk_acceptance_status
    CHECK (status IN ('ACTIVE', 'WITHDRAWN'));

-- Active rows carry no withdrawal data; withdrawn rows must carry all of it.
ALTER TABLE acceptance ADD CONSTRAINT chk_acceptance_withdrawal_shape
    CHECK (
        (status = 'ACTIVE'
            AND withdrawal_reasons IS NULL
            AND withdrawal_comment IS NULL
            AND withdrawn_at IS NULL)
        OR
        (status = 'WITHDRAWN'
            AND withdrawal_reasons IS NOT NULL
            AND jsonb_typeof(withdrawal_reasons) = 'array'
            AND jsonb_array_length(withdrawal_reasons) > 0
            AND withdrawn_at IS NOT NULL)
    );

-- "Other" is only useful when the worker explains it.
ALTER TABLE acceptance ADD CONSTRAINT chk_acceptance_other_needs_comment
    CHECK (
        status = 'ACTIVE'
        OR withdrawal_reasons IS NULL
        OR NOT (withdrawal_reasons ? 'other')
        OR (withdrawal_comment IS NOT NULL AND length(btrim(withdrawal_comment)) > 0)
    );

-- Supports "this worker's active work" queries without scanning history.
CREATE INDEX idx_acceptance_worker_status ON acceptance (worker_id, status);

COMMENT ON COLUMN acceptance.status IS
    'ACTIVE while the acceptance stands; WITHDRAWN after the worker nullifies it.';
COMMENT ON COLUMN acceptance.withdrawal_reasons IS
    'Reason codes chosen when the worker nullifies the acceptance, stored as a JSON array.';
COMMENT ON COLUMN acceptance.withdrawal_comment IS
    'Optional free-text explanation; required when the reason list contains "other".';
