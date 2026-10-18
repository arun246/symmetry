-- Symmetry schema, version 2.
-- Adds the seven-socket fields (work kind, lifecycle) and hardens acceptance
-- into a verified, agreement-backed record.

ALTER TABLE contract
    ADD COLUMN work_kind       TEXT NOT NULL DEFAULT 'COMPUTE',
    ADD COLUMN lifecycle_state TEXT NOT NULL DEFAULT 'DRAFT',
    ADD CONSTRAINT chk_contract_work_kind
        CHECK (work_kind IN ('COMPUTE', 'SERVICE', 'UI', 'DATA', 'SYSTEM')),
    ADD CONSTRAINT chk_contract_lifecycle_state
        CHECK (lifecycle_state IN ('DRAFT', 'NEGOTIATED', 'ACCEPTED', 'SUBMITTED', 'JUDGED', 'MERGED'));

ALTER TABLE acceptance
    ADD COLUMN worker_public_key TEXT    NOT NULL DEFAULT '',
    ADD COLUMN verified          BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN agreements        JSONB   NOT NULL DEFAULT '[]'::jsonb;
