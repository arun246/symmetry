-- Symmetry baseline schema (version 1).
--
-- This is a squash of the first two migrations into one clean, well-designed
-- baseline. It was safe to squash because no persistent database had ever
-- applied the earlier versions (see docs/past_final_commit_comments.md).
--
-- Design rules applied here:
--   * UUID primary keys, defaulted with gen_random_uuid() (built into PG 13+).
--   * TIMESTAMPTZ everywhere (never naive TIMESTAMP); the app always writes UTC.
--   * Enumerations are TEXT + CHECK (readable, and easy to extend in a migration).
--   * Every constraint and index is explicitly named, for stable future migrations.
--   * NOT NULL plus "not blank" CHECKs, so bad data cannot be stored by any client.
--   * JSONB is shape-checked (object / array) so the column type means something.
--   * Foreign keys cascade on delete, so acceptances can never be orphaned.
--   * updated_at is maintained by a trigger, not by application code.
--   * Only indexes that match real query patterns are created (no dead weight).

CREATE TABLE contract (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Identity of the module being worked on. (module_id, version) is the
    -- human-facing key; `id` exists so other tables can reference it cheaply.
    module_id         TEXT        NOT NULL,
    version           TEXT        NOT NULL,

    -- Job-card attributes. Values are validated both here and in the service.
    task_kind         TEXT        NOT NULL,
    language          TEXT        NOT NULL,
    disclosure_tier   TEXT        NOT NULL,
    work_kind         TEXT        NOT NULL DEFAULT 'COMPUTE',
    lifecycle_state   TEXT        NOT NULL DEFAULT 'DRAFT',

    -- The signed task document and the free-form JSON payload (signature,
    -- invariants, budgets, oracle, scoring).
    task_document     TEXT        NOT NULL,
    payload           JSONB       NOT NULL DEFAULT '{}'::jsonb,

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- One row per module at a given version. Also serves lookups by module_id
    -- (leading column), so no separate module_id index is needed.
    CONSTRAINT uq_contract_module_version UNIQUE (module_id, version),

    CONSTRAINT chk_contract_task_kind
        CHECK (task_kind IN ('optimize', 'build')),
    CONSTRAINT chk_contract_disclosure_tier
        CHECK (disclosure_tier IN ('L0', 'L1', 'L2')),
    CONSTRAINT chk_contract_work_kind
        CHECK (work_kind IN ('COMPUTE', 'SERVICE', 'UI', 'DATA', 'SYSTEM')),
    CONSTRAINT chk_contract_lifecycle_state
        CHECK (lifecycle_state IN ('DRAFT', 'NEGOTIATED', 'ACCEPTED', 'SUBMITTED', 'JUDGED', 'MERGED')),

    CONSTRAINT chk_contract_module_id_not_blank
        CHECK (length(btrim(module_id)) > 0),
    CONSTRAINT chk_contract_version_not_blank
        CHECK (length(btrim(version)) > 0),
    CONSTRAINT chk_contract_language_not_blank
        CHECK (length(btrim(language)) > 0),
    CONSTRAINT chk_contract_task_document_not_blank
        CHECK (length(btrim(task_document)) > 0),
    CONSTRAINT chk_contract_payload_is_object
        CHECK (jsonb_typeof(payload) = 'object')
);

-- Makes containment/path lookups inside the JSON payload fast without a large
-- generic GIN index. Added now because the payload is the main queryable field;
-- at this data size the write cost is negligible.
CREATE INDEX idx_contract_payload ON contract USING gin (payload jsonb_path_ops);

CREATE TABLE acceptance (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    contract_id        UUID        NOT NULL,
    worker_id          TEXT        NOT NULL,

    -- Proof: the hash of the accepted task document and the worker's Ed25519
    -- signature over the canonical accept message.
    task_document_hash TEXT        NOT NULL,
    signature          TEXT        NOT NULL,
    worker_public_key  TEXT        NOT NULL,
    verified           BOOLEAN     NOT NULL DEFAULT FALSE,
    agreements         JSONB       NOT NULL DEFAULT '[]'::jsonb,

    accepted_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Deleting a contract removes its acceptances; nothing is left dangling.
    CONSTRAINT fk_acceptance_contract FOREIGN KEY (contract_id)
        REFERENCES contract (id) ON DELETE CASCADE,

    -- A worker may accept a given task document only once. This also serves the
    -- findByContractId lookup (leading column), so no separate FK index is needed.
    CONSTRAINT uq_acceptance_contract_worker UNIQUE (contract_id, worker_id),

    CONSTRAINT chk_acceptance_worker_id_not_blank
        CHECK (length(btrim(worker_id)) > 0),
    CONSTRAINT chk_acceptance_agreements_is_array
        CHECK (jsonb_typeof(agreements) = 'array')
);

-- Supports "everything this worker has accepted" queries.
CREATE INDEX idx_acceptance_worker_id ON acceptance (worker_id);

-- Keep updated_at truthful at the database level, so no client can forget it.
CREATE FUNCTION set_contract_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_contract_set_updated_at
    BEFORE UPDATE ON contract
    FOR EACH ROW
    EXECUTE FUNCTION set_contract_updated_at();

COMMENT ON TABLE contract IS
    'Machine-readable job card: the signed task document plus budgets, oracle, and scoring payload.';
COMMENT ON COLUMN contract.payload IS
    'Free-form JSON card (signature, invariants, budgets, oracle, scoring); shape-checked to be an object.';
COMMENT ON COLUMN contract.lifecycle_state IS
    'Where the contract is in its life: DRAFT -> NEGOTIATED -> ACCEPTED -> SUBMITTED -> JUDGED -> MERGED.';

COMMENT ON TABLE acceptance IS
    'A signed record that a worker accepted a task document before working; anyone who submits without one is ineligible.';
COMMENT ON COLUMN acceptance.agreements IS
    'The agreements (honesty, scope, ai-declaration, ...) the worker accepted, stored as a JSON array.';
