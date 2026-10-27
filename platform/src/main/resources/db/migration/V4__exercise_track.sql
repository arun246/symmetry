-- Symmetry schema version 4: the stub/exercise authoring track.
--
-- Exercises are a separate bounded context from contracts. A publisher imports a
-- file from a public GitHub repository, removes one or more symbol bodies, adds
-- public and hidden tests, and publishes a fixed-price task. Workers accept the
-- task document and receive a workspace. Execution and judging are intentionally
-- out of scope for this version; submissions are stored and statically checked.

CREATE TABLE exercise (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID        NOT NULL,
    created_by      UUID        NOT NULL,

    title           TEXT        NOT NULL,
    summary         TEXT        NOT NULL,
    language        TEXT        NOT NULL DEFAULT 'java',
    status          TEXT        NOT NULL DEFAULT 'DRAFT',
    version         INTEGER     NOT NULL DEFAULT 1,
    complexity      TEXT        NOT NULL DEFAULT 'MEDIUM',

    price_cents     BIGINT      NOT NULL DEFAULT 0,
    currency        TEXT        NOT NULL DEFAULT 'USD',
    deadline_days   INTEGER     NOT NULL DEFAULT 7,
    task_document   TEXT        NOT NULL DEFAULT '',

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ NULL,

    CONSTRAINT fk_exercise_org FOREIGN KEY (org_id)
        REFERENCES organization (id) ON DELETE CASCADE,
    CONSTRAINT fk_exercise_created_by FOREIGN KEY (created_by)
        REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT chk_exercise_title_not_blank
        CHECK (length(btrim(title)) > 0),
    CONSTRAINT chk_exercise_summary_not_blank
        CHECK (length(btrim(summary)) > 0),
    CONSTRAINT chk_exercise_language_not_blank
        CHECK (length(btrim(language)) > 0),
    CONSTRAINT chk_exercise_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED', 'ARCHIVED')),
    CONSTRAINT chk_exercise_version_positive
        CHECK (version > 0),
    CONSTRAINT chk_exercise_complexity
        CHECK (complexity IN ('TRIVIAL', 'EASY', 'MEDIUM', 'HARD', 'EXPERT')),
    CONSTRAINT chk_exercise_price_non_negative
        CHECK (price_cents >= 0),
    CONSTRAINT chk_exercise_currency
        CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_exercise_deadline_days
        CHECK (deadline_days BETWEEN 1 AND 30),
    CONSTRAINT chk_exercise_published_consistency
        CHECK ((status = 'PUBLISHED' AND published_at IS NOT NULL)
            OR (status <> 'PUBLISHED'))
);

CREATE INDEX idx_exercise_public ON exercise (status, published_at DESC);
CREATE INDEX idx_exercise_org ON exercise (org_id, created_at DESC);
CREATE INDEX idx_exercise_creator ON exercise (created_by, created_at DESC);

CREATE TRIGGER trg_exercise_set_updated_at
    BEFORE UPDATE ON exercise
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE exercise_source (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    exercise_id       UUID        NOT NULL,
    repo_url          TEXT        NOT NULL,
    repo_owner        TEXT        NOT NULL,
    repo_name         TEXT        NOT NULL,
    commit_sha        TEXT        NOT NULL,
    file_path         TEXT        NOT NULL,
    blob_sha          TEXT        NULL,
    license           TEXT        NULL,
    language          TEXT        NOT NULL,
    original_content  TEXT        NOT NULL,
    content_hash      TEXT        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_exercise_source_exercise FOREIGN KEY (exercise_id)
        REFERENCES exercise (id) ON DELETE CASCADE,
    CONSTRAINT uq_exercise_source_exercise UNIQUE (exercise_id),
    CONSTRAINT chk_exercise_source_repo_url_not_blank
        CHECK (length(btrim(repo_url)) > 0),
    CONSTRAINT chk_exercise_source_commit_not_blank
        CHECK (length(btrim(commit_sha)) > 0),
    CONSTRAINT chk_exercise_source_path_not_blank
        CHECK (length(btrim(file_path)) > 0),
    CONSTRAINT chk_exercise_source_content_not_blank
        CHECK (length(btrim(original_content)) > 0)
);

CREATE TABLE exercise_stub (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    exercise_id       UUID        NOT NULL,
    symbol_name       TEXT        NOT NULL,
    symbol_kind       TEXT        NOT NULL,
    start_line        INTEGER     NOT NULL,
    end_line          INTEGER     NOT NULL,
    stub_content      TEXT        NOT NULL,
    original_hash     TEXT        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_exercise_stub_exercise FOREIGN KEY (exercise_id)
        REFERENCES exercise (id) ON DELETE CASCADE,
    CONSTRAINT chk_exercise_stub_symbol_not_blank
        CHECK (length(btrim(symbol_name)) > 0),
    CONSTRAINT chk_exercise_stub_kind
        CHECK (symbol_kind IN ('METHOD', 'CONSTRUCTOR', 'FUNCTION')),
    CONSTRAINT chk_exercise_stub_range
        CHECK (start_line > 0 AND end_line >= start_line),
    CONSTRAINT chk_exercise_stub_content_not_blank
        CHECK (length(btrim(stub_content)) > 0),
    CONSTRAINT uq_exercise_stub_range UNIQUE (exercise_id, start_line, end_line)
);

CREATE INDEX idx_exercise_stub_exercise ON exercise_stub (exercise_id, start_line);

CREATE TABLE exercise_test (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    exercise_id   UUID        NOT NULL,
    stub_id       UUID        NULL,
    name          TEXT        NOT NULL,
    visibility    TEXT        NOT NULL DEFAULT 'PUBLIC',
    content       TEXT        NOT NULL,
    timeout_ms    INTEGER     NOT NULL DEFAULT 5000,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_exercise_test_exercise FOREIGN KEY (exercise_id)
        REFERENCES exercise (id) ON DELETE CASCADE,
    CONSTRAINT fk_exercise_test_stub FOREIGN KEY (stub_id)
        REFERENCES exercise_stub (id) ON DELETE SET NULL,
    CONSTRAINT chk_exercise_test_name_not_blank
        CHECK (length(btrim(name)) > 0),
    CONSTRAINT chk_exercise_test_visibility
        CHECK (visibility IN ('PUBLIC', 'HIDDEN')),
    CONSTRAINT chk_exercise_test_content_not_blank
        CHECK (length(btrim(content)) > 0),
    CONSTRAINT chk_exercise_test_timeout
        CHECK (timeout_ms BETWEEN 100 AND 60000)
);

CREATE INDEX idx_exercise_test_exercise ON exercise_test (exercise_id, visibility, created_at);

CREATE TABLE exercise_side_case (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    exercise_id   UUID        NOT NULL,
    test_id       UUID        NULL,
    name          TEXT        NOT NULL,
    visibility    TEXT        NOT NULL DEFAULT 'PUBLIC',
    input_json    JSONB       NOT NULL,
    expected_json JSONB       NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_exercise_side_case_exercise FOREIGN KEY (exercise_id)
        REFERENCES exercise (id) ON DELETE CASCADE,
    CONSTRAINT fk_exercise_side_case_test FOREIGN KEY (test_id)
        REFERENCES exercise_test (id) ON DELETE SET NULL,
    CONSTRAINT chk_exercise_side_case_name_not_blank
        CHECK (length(btrim(name)) > 0),
    CONSTRAINT chk_exercise_side_case_visibility
        CHECK (visibility IN ('PUBLIC', 'HIDDEN'))
);

CREATE INDEX idx_exercise_side_case_exercise ON exercise_side_case (exercise_id, created_at);

CREATE TABLE exercise_acceptance (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    exercise_id         UUID        NOT NULL,
    worker_id           TEXT        NOT NULL,
    worker_public_key   TEXT        NOT NULL,
    task_document_hash  TEXT        NOT NULL,
    signature           TEXT        NOT NULL,
    agreements          JSONB       NOT NULL DEFAULT '[]'::jsonb,
    verified            BOOLEAN     NOT NULL DEFAULT FALSE,
    status              TEXT        NOT NULL DEFAULT 'ACTIVE',
    accepted_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    deadline_at         TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_exercise_acceptance_exercise FOREIGN KEY (exercise_id)
        REFERENCES exercise (id) ON DELETE CASCADE,
    CONSTRAINT uq_exercise_acceptance_worker UNIQUE (exercise_id, worker_id),
    CONSTRAINT chk_exercise_acceptance_worker_not_blank
        CHECK (length(btrim(worker_id)) > 0),
    CONSTRAINT chk_exercise_acceptance_hash_not_blank
        CHECK (length(btrim(task_document_hash)) > 0),
    CONSTRAINT chk_exercise_acceptance_signature_not_blank
        CHECK (length(btrim(signature)) > 0),
    CONSTRAINT chk_exercise_acceptance_status
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'SUBMITTED', 'WITHDRAWN')),
    CONSTRAINT chk_exercise_acceptance_deadline
        CHECK (deadline_at > accepted_at),
    CONSTRAINT chk_exercise_acceptance_agreements_is_array
        CHECK (jsonb_typeof(agreements) = 'array')
);

CREATE INDEX idx_exercise_acceptance_worker
    ON exercise_acceptance (worker_id, accepted_at DESC);
CREATE INDEX idx_exercise_acceptance_due
    ON exercise_acceptance (status, deadline_at);

CREATE TABLE exercise_submission (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    exercise_id     UUID        NOT NULL,
    acceptance_id   UUID        NOT NULL,
    worker_id       TEXT        NOT NULL,
    content         TEXT        NOT NULL,
    content_hash    TEXT        NOT NULL,
    status          TEXT        NOT NULL DEFAULT 'SUBMITTED',
    static_findings JSONB       NOT NULL DEFAULT '[]'::jsonb,
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_exercise_submission_exercise FOREIGN KEY (exercise_id)
        REFERENCES exercise (id) ON DELETE CASCADE,
    CONSTRAINT fk_exercise_submission_acceptance FOREIGN KEY (acceptance_id)
        REFERENCES exercise_acceptance (id) ON DELETE CASCADE,
    CONSTRAINT uq_exercise_submission_acceptance UNIQUE (acceptance_id),
    CONSTRAINT chk_exercise_submission_worker_not_blank
        CHECK (length(btrim(worker_id)) > 0),
    CONSTRAINT chk_exercise_submission_content_not_blank
        CHECK (length(btrim(content)) > 0),
    CONSTRAINT chk_exercise_submission_status
        CHECK (status IN ('SUBMITTED', 'STATIC_FAILED', 'REVIEWED'))
);

CREATE INDEX idx_exercise_submission_exercise
    ON exercise_submission (exercise_id, submitted_at DESC);

CREATE TABLE exercise_workspace_session (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    exercise_id    UUID        NOT NULL,
    acceptance_id  UUID        NOT NULL,
    worker_id      TEXT        NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_exercise_workspace_exercise FOREIGN KEY (exercise_id)
        REFERENCES exercise (id) ON DELETE CASCADE,
    CONSTRAINT fk_exercise_workspace_acceptance FOREIGN KEY (acceptance_id)
        REFERENCES exercise_acceptance (id) ON DELETE CASCADE,
    CONSTRAINT chk_exercise_workspace_worker_not_blank
        CHECK (length(btrim(worker_id)) > 0)
);

CREATE INDEX idx_exercise_workspace_acceptance
    ON exercise_workspace_session (acceptance_id);

COMMENT ON TABLE exercise IS
    'A published stub task: a public GitHub source file with removed symbol bodies and tests.';
COMMENT ON TABLE exercise_source IS
    'The pinned source blob and GitHub provenance for an exercise. Never exposed publicly.';
COMMENT ON TABLE exercise_stub IS
    'One removed symbol and the stub text a worker must implement.';
COMMENT ON TABLE exercise_test IS
    'Publisher-authored tests. HIDDEN tests are never returned by public endpoints.';
COMMENT ON TABLE exercise_side_case IS
    'Publisher-authored edge cases associated with a test.';
COMMENT ON TABLE exercise_acceptance IS
    'Signed record that a worker accepted an exercise and its deadline.';
COMMENT ON TABLE exercise_submission IS
    'Stored worker implementation plus static-check findings; no execution in this version.';
