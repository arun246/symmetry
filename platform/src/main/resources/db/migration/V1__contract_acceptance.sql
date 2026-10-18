-- Symmetry schema, version 1.
-- M1 walking skeleton: contracts, task documents, and worker acceptance.

CREATE TABLE contract (
    id                UUID        PRIMARY KEY,
    module_id         TEXT        NOT NULL,
    version           TEXT        NOT NULL,
    task_kind         TEXT        NOT NULL CHECK (task_kind IN ('optimize', 'build')),
    language          TEXT        NOT NULL,
    disclosure_tier   TEXT        NOT NULL CHECK (disclosure_tier IN ('L0', 'L1', 'L2')),
    task_document     TEXT        NOT NULL,
    payload           JSONB       NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_contract_module_version UNIQUE (module_id, version)
);

CREATE TABLE acceptance (
    id                 UUID        PRIMARY KEY,
    contract_id        UUID        NOT NULL REFERENCES contract (id),
    worker_id          TEXT        NOT NULL,
    task_document_hash TEXT        NOT NULL,
    accepted_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    signature          TEXT        NOT NULL
);

CREATE INDEX idx_acceptance_contract ON acceptance (contract_id);
CREATE INDEX idx_acceptance_worker ON acceptance (worker_id);
