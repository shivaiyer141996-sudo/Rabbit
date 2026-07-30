CREATE TABLE pilot_check_results (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    check_key VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL,
    tester_name VARCHAR(150),
    evidence_url VARCHAR(1000),
    defect_id VARCHAR(100),
    notes VARCHAR(2000),
    executed_at TIMESTAMPTZ,
    updated_by UUID REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (organisation_id, check_key),
    CONSTRAINT pilot_check_status_valid
        CHECK (status IN ('NOT_RUN', 'PASS', 'FAIL', 'BLOCKED'))
);

CREATE INDEX idx_pilot_check_tenant_status
    ON pilot_check_results(organisation_id, status, check_key);

CREATE TABLE pilot_sign_offs (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL UNIQUE REFERENCES organisations(id),
    release_version VARCHAR(50) NOT NULL,
    authorised_by VARCHAR(150) NOT NULL,
    authoriser_title VARCHAR(150) NOT NULL,
    support_contact VARCHAR(200) NOT NULL,
    rollback_owner VARCHAR(150) NOT NULL,
    notes VARCHAR(2000),
    signed_at TIMESTAMPTZ NOT NULL,
    signed_by_user_id UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE OR REPLACE FUNCTION rabbit_assert_pilot_actor_tenant()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    actor_id UUID;
BEGIN
    actor_id := CASE TG_TABLE_NAME
        WHEN 'pilot_check_results' THEN NEW.updated_by
        WHEN 'pilot_sign_offs' THEN NEW.signed_by_user_id
        ELSE NULL
    END;

    IF actor_id IS NULL AND TG_TABLE_NAME = 'pilot_check_results' THEN
        RETURN NEW;
    END IF;

    IF actor_id IS NULL OR NOT EXISTS (
        SELECT 1
        FROM organisation_memberships membership
        WHERE membership.organisation_id = NEW.organisation_id
          AND membership.user_id = actor_id
          AND membership.status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'Pilot actor is not a member of the record organisation'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tenant_guard_pilot_check_actor
    BEFORE INSERT OR UPDATE ON pilot_check_results
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_pilot_actor_tenant();

CREATE TRIGGER tenant_guard_pilot_sign_off_actor
    BEFORE INSERT OR UPDATE ON pilot_sign_offs
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_pilot_actor_tenant();
