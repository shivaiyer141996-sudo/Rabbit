CREATE TABLE pilot_release_decisions (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    outcome VARCHAR(30) NOT NULL,
    release_version VARCHAR(50) NOT NULL,
    release_commit VARCHAR(40) NOT NULL,
    institution_name VARCHAR(200) NOT NULL,
    authorised_by VARCHAR(150) NOT NULL,
    authoriser_title VARCHAR(150) NOT NULL,
    uat_lead VARCHAR(150) NOT NULL,
    technical_owner VARCHAR(150) NOT NULL,
    support_contact VARCHAR(200) NOT NULL,
    monitoring_owner VARCHAR(150) NOT NULL,
    backup_restore_owner VARCHAR(150) NOT NULL,
    incident_owner VARCHAR(150) NOT NULL,
    rollback_owner VARCHAR(150) NOT NULL,
    data_privacy_owner VARCHAR(150) NOT NULL,
    handover_recipient VARCHAR(150) NOT NULL,
    evidence_reference VARCHAR(1000) NOT NULL,
    evidence_sha256 VARCHAR(64) NOT NULL,
    known_issue_count INTEGER NOT NULL,
    known_issues_reference VARCHAR(1000),
    decision_reason VARCHAR(2000) NOT NULL,
    retest_by TIMESTAMPTZ,
    local_data_confirmed BOOLEAN NOT NULL,
    local_only_confirmed BOOLEAN NOT NULL,
    ownership_accepted BOOLEAN NOT NULL,
    scope_freeze_accepted BOOLEAN NOT NULL,
    mandatory_checks_passed BOOLEAN NOT NULL,
    passed_checks INTEGER NOT NULL,
    failed_checks INTEGER NOT NULL,
    blocked_checks INTEGER NOT NULL,
    not_run_checks INTEGER NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    decided_by_user_id UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pilot_decision_outcome_valid
        CHECK (outcome IN ('GO', 'CONDITIONAL_RETEST', 'NO_GO')),
    CONSTRAINT pilot_decision_retest_valid
        CHECK (
            (outcome = 'CONDITIONAL_RETEST' AND retest_by IS NOT NULL)
            OR (outcome <> 'CONDITIONAL_RETEST' AND retest_by IS NULL)
        ),
    CONSTRAINT pilot_decision_counts_valid
        CHECK (
            known_issue_count >= 0
            AND passed_checks >= 0
            AND failed_checks >= 0
            AND blocked_checks >= 0
            AND not_run_checks >= 0
        ),
    CONSTRAINT pilot_decision_evidence_sha_valid
        CHECK (evidence_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_pilot_release_decision_tenant_time
    ON pilot_release_decisions(organisation_id, decided_at DESC);

CREATE OR REPLACE FUNCTION rabbit_assert_pilot_actor_tenant()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    actor_id UUID;
BEGIN
    IF TG_TABLE_NAME = 'pilot_check_results' THEN
        actor_id := NEW.updated_by;
        IF actor_id IS NULL THEN
            RETURN NEW;
        END IF;
    ELSIF TG_TABLE_NAME = 'pilot_sign_offs' THEN
        actor_id := NEW.signed_by_user_id;
    ELSIF TG_TABLE_NAME = 'pilot_release_decisions' THEN
        actor_id := NEW.decided_by_user_id;
    ELSE
        RAISE EXCEPTION 'Unsupported pilot tenant-guard table: %', TG_TABLE_NAME;
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

CREATE TRIGGER tenant_guard_pilot_release_decision_actor
    BEFORE INSERT ON pilot_release_decisions
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_pilot_actor_tenant();

CREATE OR REPLACE FUNCTION rabbit_reject_pilot_decision_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Pilot release decisions are immutable'
        USING ERRCODE = '55000';
    RETURN NULL;
END;
$$;

CREATE TRIGGER immutable_pilot_release_decision
    BEFORE UPDATE OR DELETE ON pilot_release_decisions
    FOR EACH ROW EXECUTE FUNCTION rabbit_reject_pilot_decision_mutation();
