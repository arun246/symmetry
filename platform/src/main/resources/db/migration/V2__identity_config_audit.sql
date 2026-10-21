-- Symmetry schema version 2: identity, organizations, settings, config
-- requests, and the append-only audit ledger.
--
-- Additive only. V1 (contract + acceptance) stays untouched; contract gains an
-- optional owning organization and author so publisher data can be scoped.
--
-- Design rules mirror V1: UUID keys, TIMESTAMPTZ, TEXT + named CHECKs, JSONB
-- shape implied by the writer, explicit constraint/index names, and triggers for
-- updated_at. No seed data lives in migrations (dev seeds are code, not schema).

-- Generic updated_at trigger (V1's function is contract-specific by name).
CREATE FUNCTION set_row_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE organization (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    slug        TEXT        NOT NULL,
    name        TEXT        NOT NULL,
    status      TEXT        NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_organization_slug UNIQUE (slug),
    CONSTRAINT chk_organization_slug_not_blank
        CHECK (length(btrim(slug)) > 0),
    CONSTRAINT chk_organization_name_not_blank
        CHECK (length(btrim(name)) > 0),
    CONSTRAINT chk_organization_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TRIGGER trg_organization_set_updated_at
    BEFORE UPDATE ON organization
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE app_user (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Keycloak owns credentials; `subject` is the OIDC `sub`. Dev users use a
    -- synthetic `dev-...` subject.
    subject       TEXT        NOT NULL,
    email         TEXT        NOT NULL,
    display_name  TEXT        NOT NULL,
    role          TEXT        NOT NULL,
    org_id        UUID        NULL,
    status        TEXT        NOT NULL DEFAULT 'ACTIVE',

    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_app_user_subject UNIQUE (subject),
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT fk_app_user_org FOREIGN KEY (org_id)
        REFERENCES organization (id) ON DELETE SET NULL,
    CONSTRAINT chk_app_user_role
        CHECK (role IN ('WORKER', 'PUBLISHER', 'ADMIN')),
    CONSTRAINT chk_app_user_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT chk_app_user_email_not_blank
        CHECK (length(btrim(email)) > 0),
    CONSTRAINT chk_app_user_display_name_not_blank
        CHECK (length(btrim(display_name)) > 0)
);

CREATE INDEX idx_app_user_org ON app_user (org_id);

-- Publisher ownership on the job card. Nullable so V1 rows (and anonymous
-- legacy data) remain valid; new publisher-created rows always set it.
ALTER TABLE contract ADD COLUMN org_id UUID NULL;
ALTER TABLE contract ADD COLUMN created_by UUID NULL;

ALTER TABLE contract
    ADD CONSTRAINT fk_contract_org FOREIGN KEY (org_id)
        REFERENCES organization (id) ON DELETE SET NULL;
ALTER TABLE contract
    ADD CONSTRAINT fk_contract_created_by FOREIGN KEY (created_by)
        REFERENCES app_user (id) ON DELETE SET NULL;

CREATE INDEX idx_contract_org_created ON contract (org_id, created_at DESC);

-- Scoped configuration. Resolution order is USER > CONTRACT > ORG > GLOBAL and
-- is applied in the service, not here. `scope_id` is null only for GLOBAL.
CREATE TABLE setting (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    scope       TEXT        NOT NULL,
    scope_id    UUID        NULL,
    key         TEXT        NOT NULL,
    value       JSONB       NOT NULL,
    version     INTEGER     NOT NULL DEFAULT 1,
    updated_by  UUID        NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_setting_updated_by FOREIGN KEY (updated_by)
        REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT chk_setting_scope
        CHECK (scope IN ('GLOBAL', 'ORG', 'CONTRACT', 'USER')),
    CONSTRAINT chk_setting_scope_id
        CHECK ((scope = 'GLOBAL' AND scope_id IS NULL)
            OR (scope <> 'GLOBAL' AND scope_id IS NOT NULL)),
    CONSTRAINT chk_setting_key_not_blank
        CHECK (length(btrim(key)) > 0),
    CONSTRAINT chk_setting_version_positive
        CHECK (version > 0)
);

-- Non-global rows are unique per (scope, scope id, key). GLOBAL has a null
-- scope_id, and Postgres treats nulls as distinct, so it needs a partial index.
CREATE UNIQUE INDEX uq_setting_scope_key
    ON setting (scope, scope_id, key)
    WHERE scope <> 'GLOBAL';
CREATE UNIQUE INDEX uq_setting_global_key
    ON setting (key)
    WHERE scope = 'GLOBAL';

CREATE INDEX idx_setting_scope ON setting (scope, scope_id);

-- An organization asks for a task-specific tweak; an admin reviews it. Approval
-- writes a setting and an audit entry (never a silent override).
CREATE TABLE config_request (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       UUID        NOT NULL,
    contract_id  UUID        NULL,
    requested_by UUID        NOT NULL,
    key          TEXT        NOT NULL,
    value        JSONB       NOT NULL,
    reason       TEXT        NOT NULL,
    status       TEXT        NOT NULL DEFAULT 'PENDING',
    reviewed_by  UUID        NULL,
    reviewed_at  TIMESTAMPTZ NULL,
    review_note  TEXT        NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_config_request_org FOREIGN KEY (org_id)
        REFERENCES organization (id) ON DELETE CASCADE,
    CONSTRAINT fk_config_request_contract FOREIGN KEY (contract_id)
        REFERENCES contract (id) ON DELETE SET NULL,
    CONSTRAINT fk_config_request_requested_by FOREIGN KEY (requested_by)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_config_request_reviewed_by FOREIGN KEY (reviewed_by)
        REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT chk_config_request_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'APPLIED')),
    CONSTRAINT chk_config_request_key_not_blank
        CHECK (length(btrim(key)) > 0),
    CONSTRAINT chk_config_request_reason_not_blank
        CHECK (length(btrim(reason)) > 0),
    CONSTRAINT chk_config_request_reviewed
        CHECK (status = 'PENDING'
            OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL))
);

CREATE INDEX idx_config_request_status ON config_request (status, created_at);
CREATE INDEX idx_config_request_org ON config_request (org_id, created_at DESC);

-- Append-only ledger. The application only inserts and reads; there are no
-- UPDATE/DELETE endpoints and no grant for them in deploy.
CREATE TABLE audit_log (
    id          BIGSERIAL   PRIMARY KEY,
    actor_id    UUID        NULL,
    actor_email TEXT        NULL,
    actor_role  TEXT        NULL,
    action      TEXT        NOT NULL,
    target_type TEXT        NOT NULL,
    target_id   TEXT        NULL,
    before      JSONB       NULL,
    after       JSONB       NULL,
    ip          TEXT        NULL,
    user_agent  TEXT        NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id)
        REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT chk_audit_action_not_blank
        CHECK (length(btrim(action)) > 0),
    CONSTRAINT chk_audit_target_type_not_blank
        CHECK (length(btrim(target_type)) > 0)
);

CREATE INDEX idx_audit_created_at ON audit_log (created_at DESC);
CREATE INDEX idx_audit_actor ON audit_log (actor_id, created_at DESC);
CREATE INDEX idx_audit_action ON audit_log (action, created_at DESC);

COMMENT ON TABLE organization IS
    'A publisher company (or task committee) that owns contracts.';
COMMENT ON TABLE app_user IS
    'Local mirror of a Keycloak identity; Keycloak holds credentials, this holds role and org.';
COMMENT ON TABLE setting IS
    'Scoped configuration resolved as USER > CONTRACT > ORG > GLOBAL.';
COMMENT ON TABLE config_request IS
    'An organization request for a task-specific configuration tweak, reviewed by an admin.';
COMMENT ON TABLE audit_log IS
    'Append-only record of privileged changes (actor, action, before, after).';
COMMENT ON FUNCTION set_row_updated_at() IS
    'Sets updated_at = now() on UPDATE for tables that use it.';
