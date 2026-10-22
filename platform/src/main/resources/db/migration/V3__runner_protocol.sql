-- Symmetry schema version 3: publisher runners, job leases and signed results.
--
-- The control plane is the source of truth for runner and job state. A runner
-- authenticates with a one-time-issued bearer token (only its SHA-256 hash is
-- stored), leases one contract at a time, and returns a result signed with the
-- Ed25519 key registered with it.
--
-- Design rules mirror V1/V2: UUID keys, TIMESTAMPTZ, TEXT + named CHECKs, JSONB
-- shape checks, explicit constraint/index names, and no seed data.

CREATE TABLE runner (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name              TEXT        NOT NULL,
    hostname          TEXT        NOT NULL,
    org_id            UUID        NULL,

    -- The runner's Ed25519 public key, X.509/SubjectPublicKeyInfo, base64.
    public_key        TEXT        NOT NULL,
    -- SHA-256 of the bearer token issued at registration. The raw token is
    -- returned once and never stored.
    access_token_hash TEXT        NOT NULL,

    version           TEXT        NOT NULL,
    capabilities      JSONB       NOT NULL DEFAULT '{}'::jsonb,
    status            TEXT        NOT NULL DEFAULT 'ACTIVE',

    last_seen_at      TIMESTAMPTZ NULL,
    registered_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at        TIMESTAMPTZ NULL,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_runner_org FOREIGN KEY (org_id)
        REFERENCES organization (id) ON DELETE SET NULL,
    CONSTRAINT uq_runner_public_key UNIQUE (public_key),
    CONSTRAINT uq_runner_access_token_hash UNIQUE (access_token_hash),
    CONSTRAINT chk_runner_name_not_blank
        CHECK (length(btrim(name)) > 0),
    CONSTRAINT chk_runner_hostname_not_blank
        CHECK (length(btrim(hostname)) > 0),
    CONSTRAINT chk_runner_version_not_blank
        CHECK (length(btrim(version)) > 0),
    CONSTRAINT chk_runner_public_key_not_blank
        CHECK (length(btrim(public_key)) > 0),
    CONSTRAINT chk_runner_access_token_hash_not_blank
        CHECK (length(btrim(access_token_hash)) > 0),
    CONSTRAINT chk_runner_capabilities_is_object
        CHECK (jsonb_typeof(capabilities) = 'object'),
    CONSTRAINT chk_runner_status
        CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT chk_runner_revoked_consistency
        CHECK ((status = 'ACTIVE' AND revoked_at IS NULL)
            OR (status = 'REVOKED' AND revoked_at IS NOT NULL))
);

CREATE INDEX idx_runner_status_seen ON runner (status, last_seen_at DESC);
CREATE INDEX idx_runner_org ON runner (org_id);

CREATE TRIGGER trg_runner_set_updated_at
    BEFORE UPDATE ON runner
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

-- A lease is the deadline-bound permission for exactly one runner to work on
-- exactly one contract. Expiry is a normal, expected state: the control plane
-- expires stale leases and retries the work on another runner.
CREATE TABLE job_lease (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    runner_id    UUID        NOT NULL,
    contract_id  UUID        NOT NULL,

    -- Random value that binds a result to this lease. It is not a secret; the
    -- result signature binds it to the runner key.
    nonce        TEXT        NOT NULL,
    status       TEXT        NOT NULL DEFAULT 'ACTIVE',

    leased_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NULL,

    CONSTRAINT fk_job_lease_runner FOREIGN KEY (runner_id)
        REFERENCES runner (id) ON DELETE RESTRICT,
    CONSTRAINT fk_job_lease_contract FOREIGN KEY (contract_id)
        REFERENCES contract (id) ON DELETE CASCADE,
    CONSTRAINT uq_job_lease_nonce UNIQUE (nonce),
    CONSTRAINT chk_job_lease_nonce_not_blank
        CHECK (length(btrim(nonce)) > 0),
    CONSTRAINT chk_job_lease_status
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT chk_job_lease_expiry_after_lease
        CHECK (expires_at > leased_at),
    CONSTRAINT chk_job_lease_completed_consistency
        CHECK ((status = 'COMPLETED' AND completed_at IS NOT NULL)
            OR (status <> 'COMPLETED' AND completed_at IS NULL))
);

-- At most one live lease per contract, even if two runners ask at once. Expired
-- and revoked leases are ignored, so the work can be leased again.
CREATE UNIQUE INDEX uq_job_lease_active_contract
    ON job_lease (contract_id)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_job_lease_runner_status ON job_lease (runner_id, status);
CREATE INDEX idx_job_lease_due ON job_lease (status, expires_at);

-- The runner's signed verdict. One row per lease: submitting the same result
-- again is idempotent and returns the stored row. Revoking a runner quarantines
-- its results; quarantined results no longer block a re-run on another runner.
CREATE TABLE runner_result (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    lease_id        UUID        NOT NULL,
    runner_id       UUID        NOT NULL,
    contract_id     UUID        NOT NULL,
    nonce           TEXT        NOT NULL,

    -- The exact JSON text the runner signed. payload_hash is SHA-256 of that
    -- text, so verifiers do not depend on the database's JSONB normalization.
    payload         JSONB       NOT NULL,
    payload_hash    TEXT        NOT NULL,
    signature       TEXT        NOT NULL,

    passed          BOOLEAN     NOT NULL,
    artifact_digest TEXT        NULL,
    logs_digest     TEXT        NULL,
    quarantined     BOOLEAN     NOT NULL DEFAULT FALSE,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_runner_result_lease FOREIGN KEY (lease_id)
        REFERENCES job_lease (id) ON DELETE RESTRICT,
    CONSTRAINT fk_runner_result_runner FOREIGN KEY (runner_id)
        REFERENCES runner (id) ON DELETE RESTRICT,
    CONSTRAINT fk_runner_result_contract FOREIGN KEY (contract_id)
        REFERENCES contract (id) ON DELETE CASCADE,
    CONSTRAINT uq_runner_result_lease UNIQUE (lease_id),
    CONSTRAINT chk_runner_result_nonce_not_blank
        CHECK (length(btrim(nonce)) > 0),
    CONSTRAINT chk_runner_result_payload_hash_not_blank
        CHECK (length(btrim(payload_hash)) > 0),
    CONSTRAINT chk_runner_result_signature_not_blank
        CHECK (length(btrim(signature)) > 0),
    CONSTRAINT chk_runner_result_payload_is_object
        CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX idx_runner_result_contract_live
    ON runner_result (contract_id)
    WHERE quarantined = FALSE;
CREATE INDEX idx_runner_result_runner ON runner_result (runner_id, received_at DESC);

COMMENT ON TABLE runner IS
    'A registered publisher appliance; stores its Ed25519 key and a hash of its bearer token.';
COMMENT ON TABLE job_lease IS
    'Deadline-bound permission for one runner to work on one contract.';
COMMENT ON TABLE runner_result IS
    'A runner-signed, sanitized verdict bound to one lease; quarantined results do not count.';
COMMENT ON COLUMN runner_result.payload_hash IS
    'SHA-256 of the exact JSON text signed by the runner.';
