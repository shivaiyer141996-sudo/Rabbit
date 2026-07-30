CREATE TABLE invitation_tokens (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    user_id UUID NOT NULL REFERENCES user_accounts(id),
    membership_id UUID NOT NULL UNIQUE REFERENCES organisation_memberships(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_by_user_id UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT invitation_token_hash_length CHECK (length(token_hash) = 64),
    CONSTRAINT invitation_token_expiry_valid CHECK (expires_at > created_at)
);

CREATE INDEX idx_invitation_token_expiry
    ON invitation_tokens(expires_at)
    WHERE consumed_at IS NULL;

CREATE OR REPLACE FUNCTION rabbit_assert_invitation_tenant()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM organisation_memberships membership
        WHERE membership.id = NEW.membership_id
          AND membership.organisation_id = NEW.organisation_id
          AND membership.user_id = NEW.user_id
    ) THEN
        RAISE EXCEPTION 'Invitation membership is outside the token tenant'
            USING ERRCODE = '23514';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM organisation_memberships creator
        WHERE creator.organisation_id = NEW.organisation_id
          AND creator.user_id = NEW.created_by_user_id
    ) THEN
        RAISE EXCEPTION 'Invitation creator is outside the token tenant'
            USING ERRCODE = '23514';
    END IF;

    IF TG_OP = 'INSERT' THEN
        IF NOT EXISTS (
            SELECT 1
            FROM organisation_memberships creator
            WHERE creator.organisation_id = NEW.organisation_id
              AND creator.user_id = NEW.created_by_user_id
              AND creator.status = 'ACTIVE'
        ) THEN
            RAISE EXCEPTION 'Invitation creator is not active in the token tenant'
                USING ERRCODE = '23514';
        END IF;
    ELSIF NEW.token_hash <> OLD.token_hash THEN
        IF NOT EXISTS (
            SELECT 1
            FROM organisation_memberships creator
            WHERE creator.organisation_id = NEW.organisation_id
              AND creator.user_id = NEW.created_by_user_id
              AND creator.status = 'ACTIVE'
        ) THEN
            RAISE EXCEPTION 'Invitation creator is not active in the token tenant'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tenant_guard_invitation_tokens
    BEFORE INSERT OR UPDATE ON invitation_tokens
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_invitation_tenant();
