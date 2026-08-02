CREATE OR REPLACE FUNCTION rabbit_commercial_price_valid(
    selected_plan VARCHAR,
    selected_limit INTEGER,
    selected_price BIGINT
)
RETURNS BOOLEAN
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT CASE
        WHEN selected_plan = 'BASIC' AND selected_limit = 50 THEN selected_price = 59900
        WHEN selected_plan = 'BASIC' AND selected_limit = 150 THEN selected_price = 99900
        WHEN selected_plan = 'BASIC' AND selected_limit = 500 THEN selected_price = 149900
        WHEN selected_plan = 'PRO' AND selected_limit = 50 THEN selected_price = 89900
        WHEN selected_plan = 'PRO' AND selected_limit = 150 THEN selected_price = 139900
        WHEN selected_plan = 'PRO' AND selected_limit = 500 THEN selected_price = 189900
        WHEN selected_plan = 'LEGEND' AND selected_limit = 50 THEN selected_price = 149900
        WHEN selected_plan = 'LEGEND' AND selected_limit = 150 THEN selected_price = 199900
        WHEN selected_plan = 'LEGEND' AND selected_limit = 500 THEN selected_price = 249900
        ELSE FALSE
    END
$$;

CREATE TABLE organisation_subscriptions (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL UNIQUE REFERENCES organisations(id),
    plan_code VARCHAR(20) NOT NULL,
    student_limit INTEGER NOT NULL,
    monthly_price_paise BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    trial_starts_at TIMESTAMPTZ,
    trial_ends_at TIMESTAMPTZ,
    period_starts_at TIMESTAMPTZ,
    period_ends_at TIMESTAMPTZ,
    pending_plan_code VARCHAR(20),
    pending_student_limit INTEGER,
    pending_monthly_price_paise BIGINT,
    pending_period_starts_at TIMESTAMPTZ,
    pending_period_ends_at TIMESTAMPTZ,
    source_invoice_id UUID,
    pending_source_invoice_id UUID,
    note VARCHAR(1000),
    created_by_user_id UUID NOT NULL REFERENCES user_accounts(id),
    updated_by_user_id UUID NOT NULL REFERENCES user_accounts(id),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT commercial_subscription_plan_valid
        CHECK (plan_code IN ('BASIC', 'PRO', 'LEGEND')),
    CONSTRAINT commercial_subscription_status_valid
        CHECK (status IN ('TRIALING', 'ACTIVE', 'EXPIRED', 'SUSPENDED')),
    CONSTRAINT commercial_subscription_price_valid
        CHECK (rabbit_commercial_price_valid(plan_code, student_limit, monthly_price_paise)),
    CONSTRAINT commercial_trial_window_valid CHECK (
        (status = 'TRIALING'
            AND plan_code = 'LEGEND'
            AND trial_starts_at IS NOT NULL
            AND trial_ends_at = trial_starts_at + INTERVAL '20 days')
        OR status <> 'TRIALING'
    ),
    CONSTRAINT commercial_paid_window_valid CHECK (
        (period_starts_at IS NULL AND period_ends_at IS NULL)
        OR (period_starts_at IS NOT NULL
            AND period_ends_at IS NOT NULL
            AND period_starts_at < period_ends_at)
    ),
    CONSTRAINT commercial_pending_change_complete CHECK (
        (pending_plan_code IS NULL
            AND pending_student_limit IS NULL
            AND pending_monthly_price_paise IS NULL
            AND pending_period_starts_at IS NULL
            AND pending_period_ends_at IS NULL
            AND pending_source_invoice_id IS NULL)
        OR (pending_plan_code IN ('BASIC', 'PRO', 'LEGEND')
            AND pending_student_limit IS NOT NULL
            AND pending_monthly_price_paise IS NOT NULL
            AND pending_period_starts_at IS NOT NULL
            AND pending_period_ends_at IS NOT NULL
            AND pending_period_starts_at < pending_period_ends_at
            AND pending_source_invoice_id IS NOT NULL
            AND rabbit_commercial_price_valid(
                pending_plan_code,
                pending_student_limit,
                pending_monthly_price_paise
            ))
    )
);

CREATE TABLE commercial_subscription_events (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    subscription_id UUID NOT NULL REFERENCES organisation_subscriptions(id),
    event_type VARCHAR(40) NOT NULL,
    before_value TEXT,
    after_value TEXT NOT NULL,
    actor_user_id UUID NOT NULL REFERENCES user_accounts(id),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT commercial_subscription_event_type_valid CHECK (
        event_type IN (
            'TRIAL_STARTED',
            'TRIAL_EXPIRED',
            'PLAN_ACTIVATED',
            'PLAN_CHANGE_SCHEDULED',
            'PLAN_CHANGE_APPLIED',
            'SUBSCRIPTION_EXPIRED',
            'SUBSCRIPTION_SUSPENDED',
            'SUBSCRIPTION_RESTORED'
        )
    )
);
CREATE INDEX idx_commercial_subscription_event_tenant_time
    ON commercial_subscription_events(organisation_id, occurred_at DESC);

CREATE TABLE commercial_invoices (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    invoice_number VARCHAR(80) NOT NULL,
    plan_code VARCHAR(20) NOT NULL,
    student_limit INTEGER NOT NULL,
    period_starts_at TIMESTAMPTZ NOT NULL,
    period_ends_at TIMESTAMPTZ NOT NULL,
    subtotal_paise BIGINT NOT NULL,
    tax_paise BIGINT NOT NULL DEFAULT 0,
    total_paise BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    paid_at TIMESTAMPTZ,
    note VARCHAR(1000),
    created_by_user_id UUID NOT NULL REFERENCES user_accounts(id),
    updated_by_user_id UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (organisation_id, invoice_number),
    CONSTRAINT commercial_invoice_plan_valid
        CHECK (plan_code IN ('BASIC', 'PRO', 'LEGEND')),
    CONSTRAINT commercial_invoice_status_valid
        CHECK (status IN ('ISSUED', 'PAID', 'VOID')),
    CONSTRAINT commercial_invoice_amount_valid CHECK (
        rabbit_commercial_price_valid(plan_code, student_limit, subtotal_paise)
        AND tax_paise >= 0
        AND tax_paise <= subtotal_paise
        AND total_paise = subtotal_paise + tax_paise
    ),
    CONSTRAINT commercial_invoice_period_valid CHECK (
        period_ends_at - period_starts_at >= INTERVAL '27 days'
        AND period_ends_at - period_starts_at <= INTERVAL '32 days'
    ),
    CONSTRAINT commercial_invoice_dates_valid
        CHECK (issued_at <= due_at),
    CONSTRAINT commercial_invoice_paid_state_valid CHECK (
        (status = 'PAID' AND paid_at IS NOT NULL)
        OR (status <> 'PAID' AND paid_at IS NULL)
    )
);
CREATE INDEX idx_commercial_invoice_tenant_time
    ON commercial_invoices(organisation_id, issued_at DESC);

ALTER TABLE organisation_subscriptions
    ADD CONSTRAINT fk_commercial_subscription_invoice
        FOREIGN KEY (source_invoice_id) REFERENCES commercial_invoices(id),
    ADD CONSTRAINT fk_commercial_subscription_pending_invoice
        FOREIGN KEY (pending_source_invoice_id) REFERENCES commercial_invoices(id);

CREATE TABLE commercial_payments (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    invoice_id UUID NOT NULL REFERENCES commercial_invoices(id),
    payment_reference VARCHAR(120) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    amount_paise BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    paid_at TIMESTAMPTZ NOT NULL,
    recorded_by_user_id UUID NOT NULL REFERENCES user_accounts(id),
    note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (organisation_id, payment_reference),
    UNIQUE (invoice_id),
    CONSTRAINT commercial_payment_method_valid CHECK (
        payment_method IN ('BANK_TRANSFER', 'UPI', 'CHEQUE', 'CASH', 'OTHER')
    ),
    CONSTRAINT commercial_payment_status_valid
        CHECK (status IN ('RECORDED')),
    CONSTRAINT commercial_payment_amount_positive CHECK (amount_paise > 0)
);
CREATE INDEX idx_commercial_payment_tenant_time
    ON commercial_payments(organisation_id, paid_at DESC);

CREATE TABLE commercial_receipts (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    payment_id UUID NOT NULL UNIQUE REFERENCES commercial_payments(id),
    invoice_id UUID NOT NULL REFERENCES commercial_invoices(id),
    receipt_number VARCHAR(80) NOT NULL,
    amount_paise BIGINT NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    issued_by_user_id UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (organisation_id, receipt_number),
    CONSTRAINT commercial_receipt_amount_positive CHECK (amount_paise > 0)
);
CREATE INDEX idx_commercial_receipt_tenant_time
    ON commercial_receipts(organisation_id, issued_at DESC);

CREATE TABLE commercial_support_cases (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    case_number VARCHAR(80) NOT NULL,
    severity VARCHAR(10) NOT NULL,
    category VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    summary VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    requester_user_id UUID NOT NULL REFERENCES user_accounts(id),
    assigned_to VARCHAR(200),
    response_due_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    resolution TEXT,
    created_by_user_id UUID NOT NULL REFERENCES user_accounts(id),
    updated_by_user_id UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (organisation_id, case_number),
    CONSTRAINT commercial_support_severity_valid
        CHECK (severity IN ('S1', 'S2', 'S3', 'S4')),
    CONSTRAINT commercial_support_category_valid CHECK (
        category IN ('ACCESS', 'ASSESSMENT', 'REPORTING', 'BILLING', 'DATA', 'OTHER')
    ),
    CONSTRAINT commercial_support_status_valid CHECK (
        status IN ('OPEN', 'IN_PROGRESS', 'WAITING_FOR_INSTITUTION', 'RESOLVED', 'CLOSED')
    ),
    CONSTRAINT commercial_support_resolution_valid CHECK (
        (status IN ('RESOLVED', 'CLOSED')
            AND resolved_at IS NOT NULL
            AND resolution IS NOT NULL
            AND length(trim(resolution)) > 0)
        OR (status NOT IN ('RESOLVED', 'CLOSED') AND resolved_at IS NULL)
    )
);
CREATE INDEX idx_commercial_support_tenant_status
    ON commercial_support_cases(organisation_id, status, created_at DESC);

CREATE OR REPLACE FUNCTION rabbit_assert_commercial_tenant_integrity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    actor_id UUID;
BEGIN
    IF TG_TABLE_NAME = 'organisation_subscriptions' THEN
        actor_id := NEW.updated_by_user_id;
        IF NEW.source_invoice_id IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM commercial_invoices invoice
            WHERE invoice.id = NEW.source_invoice_id
              AND invoice.organisation_id = NEW.organisation_id
        ) THEN
            RAISE EXCEPTION 'Cross-tenant subscription invoice rejected'
                USING ERRCODE = '23514';
        END IF;
        IF NEW.pending_source_invoice_id IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM commercial_invoices invoice
            WHERE invoice.id = NEW.pending_source_invoice_id
              AND invoice.organisation_id = NEW.organisation_id
        ) THEN
            RAISE EXCEPTION 'Cross-tenant pending subscription invoice rejected'
                USING ERRCODE = '23514';
        END IF;
    ELSIF TG_TABLE_NAME = 'commercial_subscription_events' THEN
        actor_id := NEW.actor_user_id;
        IF NOT EXISTS (
            SELECT 1 FROM organisation_subscriptions subscription
            WHERE subscription.id = NEW.subscription_id
              AND subscription.organisation_id = NEW.organisation_id
        ) THEN
            RAISE EXCEPTION 'Cross-tenant subscription event rejected'
                USING ERRCODE = '23514';
        END IF;
    ELSIF TG_TABLE_NAME = 'commercial_payments' THEN
        actor_id := NEW.recorded_by_user_id;
        IF NOT EXISTS (
            SELECT 1 FROM commercial_invoices invoice
            WHERE invoice.id = NEW.invoice_id
              AND invoice.organisation_id = NEW.organisation_id
              AND invoice.total_paise = NEW.amount_paise
              AND invoice.status = 'ISSUED'
        ) THEN
            RAISE EXCEPTION 'Cross-tenant or invalid invoice payment rejected'
                USING ERRCODE = '23514';
        END IF;
    ELSIF TG_TABLE_NAME = 'commercial_receipts' THEN
        actor_id := NEW.issued_by_user_id;
        IF NOT EXISTS (
            SELECT 1
            FROM commercial_payments payment
            JOIN commercial_invoices invoice ON invoice.id = NEW.invoice_id
            WHERE payment.id = NEW.payment_id
              AND payment.invoice_id = NEW.invoice_id
              AND payment.organisation_id = NEW.organisation_id
              AND invoice.organisation_id = NEW.organisation_id
              AND payment.amount_paise = NEW.amount_paise
              AND invoice.total_paise = NEW.amount_paise
              AND invoice.status = 'PAID'
        ) THEN
            RAISE EXCEPTION 'Cross-tenant or invalid receipt rejected'
                USING ERRCODE = '23514';
        END IF;
    ELSIF TG_TABLE_NAME = 'commercial_invoices' THEN
        actor_id := NEW.updated_by_user_id;
    ELSIF TG_TABLE_NAME = 'commercial_support_cases' THEN
        actor_id := NEW.updated_by_user_id;
    ELSE
        RAISE EXCEPTION 'Unsupported commercial tenant-guard table: %', TG_TABLE_NAME;
    END IF;
    IF actor_id IS NULL OR NOT EXISTS (
        SELECT 1 FROM organisation_memberships membership
        WHERE membership.organisation_id = NEW.organisation_id
          AND membership.user_id = actor_id
          AND membership.status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'Commercial actor is not an active member of the record organisation'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tenant_guard_organisation_subscriptions
    BEFORE INSERT OR UPDATE ON organisation_subscriptions
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_commercial_tenant_integrity();
CREATE TRIGGER tenant_guard_commercial_subscription_events
    BEFORE INSERT OR UPDATE ON commercial_subscription_events
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_commercial_tenant_integrity();
CREATE TRIGGER tenant_guard_commercial_payments
    BEFORE INSERT OR UPDATE ON commercial_payments
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_commercial_tenant_integrity();
CREATE TRIGGER tenant_guard_commercial_receipts
    BEFORE INSERT OR UPDATE ON commercial_receipts
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_commercial_tenant_integrity();
CREATE TRIGGER tenant_guard_commercial_invoices
    BEFORE INSERT OR UPDATE ON commercial_invoices
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_commercial_tenant_integrity();
CREATE TRIGGER tenant_guard_commercial_support_cases
    BEFORE INSERT OR UPDATE ON commercial_support_cases
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_commercial_tenant_integrity();

CREATE OR REPLACE FUNCTION rabbit_enforce_commercial_student_limit()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    permitted_students INTEGER;
    subscription_status VARCHAR;
    access_ends_at TIMESTAMPTZ;
    occupied_students INTEGER;
BEGIN
    IF NEW.role <> 'STUDENT' OR NEW.status NOT IN ('ACTIVE', 'INVITED') THEN
        RETURN NEW;
    END IF;

    SELECT
        LEAST(
            subscription.student_limit,
            COALESCE(subscription.pending_student_limit, subscription.student_limit)
        ),
        subscription.status,
        CASE
            WHEN subscription.status = 'TRIALING' THEN subscription.trial_ends_at
            ELSE subscription.period_ends_at
        END
    INTO permitted_students, subscription_status, access_ends_at
    FROM organisation_subscriptions subscription
    WHERE subscription.organisation_id = NEW.organisation_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN NEW;
    END IF;
    IF subscription_status NOT IN ('TRIALING', 'ACTIVE')
        OR access_ends_at IS NULL
        OR access_ends_at <= now()
    THEN
        RAISE EXCEPTION 'Cannot activate or invite a Student without an active subscription'
            USING ERRCODE = '23514';
    END IF;

    SELECT count(*) INTO occupied_students
    FROM organisation_memberships membership
    WHERE membership.organisation_id = NEW.organisation_id
      AND membership.role = 'STUDENT'
      AND membership.status IN ('ACTIVE', 'INVITED')
      AND membership.id <> NEW.id;

    IF occupied_students >= permitted_students THEN
        RAISE EXCEPTION 'Commercial Student capacity % has been reached', permitted_students
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER enforce_commercial_student_limit
    BEFORE INSERT OR UPDATE OF role, status, organisation_id
    ON organisation_memberships
    FOR EACH ROW EXECUTE FUNCTION rabbit_enforce_commercial_student_limit();

CREATE OR REPLACE FUNCTION rabbit_reject_commercial_immutable_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Commercial record % is immutable', TG_TABLE_NAME
        USING ERRCODE = '55000';
    RETURN NULL;
END;
$$;

CREATE TRIGGER immutable_commercial_subscription_event
    BEFORE UPDATE OR DELETE ON commercial_subscription_events
    FOR EACH ROW EXECUTE FUNCTION rabbit_reject_commercial_immutable_mutation();
CREATE TRIGGER immutable_commercial_payment
    BEFORE UPDATE OR DELETE ON commercial_payments
    FOR EACH ROW EXECUTE FUNCTION rabbit_reject_commercial_immutable_mutation();
CREATE TRIGGER immutable_commercial_receipt
    BEFORE UPDATE OR DELETE ON commercial_receipts
    FOR EACH ROW EXECUTE FUNCTION rabbit_reject_commercial_immutable_mutation();
CREATE TRIGGER protected_commercial_subscription_delete
    BEFORE DELETE ON organisation_subscriptions
    FOR EACH ROW EXECUTE FUNCTION rabbit_reject_commercial_immutable_mutation();
CREATE TRIGGER protected_commercial_invoice_delete
    BEFORE DELETE ON commercial_invoices
    FOR EACH ROW EXECUTE FUNCTION rabbit_reject_commercial_immutable_mutation();
CREATE TRIGGER protected_commercial_support_case_delete
    BEFORE DELETE ON commercial_support_cases
    FOR EACH ROW EXECUTE FUNCTION rabbit_reject_commercial_immutable_mutation();

CREATE OR REPLACE FUNCTION rabbit_validate_commercial_invoice_update()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status <> 'ISSUED'
        OR NEW.status NOT IN ('PAID', 'VOID')
        OR NEW.organisation_id IS DISTINCT FROM OLD.organisation_id
        OR NEW.invoice_number IS DISTINCT FROM OLD.invoice_number
        OR NEW.plan_code IS DISTINCT FROM OLD.plan_code
        OR NEW.student_limit IS DISTINCT FROM OLD.student_limit
        OR NEW.period_starts_at IS DISTINCT FROM OLD.period_starts_at
        OR NEW.period_ends_at IS DISTINCT FROM OLD.period_ends_at
        OR NEW.subtotal_paise IS DISTINCT FROM OLD.subtotal_paise
        OR NEW.tax_paise IS DISTINCT FROM OLD.tax_paise
        OR NEW.total_paise IS DISTINCT FROM OLD.total_paise
        OR NEW.issued_at IS DISTINCT FROM OLD.issued_at
        OR NEW.due_at IS DISTINCT FROM OLD.due_at
        OR NEW.note IS DISTINCT FROM OLD.note
        OR NEW.created_by_user_id IS DISTINCT FROM OLD.created_by_user_id
        OR (NEW.status = 'PAID' AND NEW.paid_at IS NULL)
        OR (NEW.status = 'VOID' AND NEW.paid_at IS NOT NULL)
        OR (NEW.status = 'PAID' AND NOT EXISTS (
            SELECT 1 FROM commercial_payments payment
            WHERE payment.invoice_id = NEW.id
              AND payment.organisation_id = NEW.organisation_id
              AND payment.amount_paise = NEW.total_paise
        ))
        OR (NEW.status = 'VOID' AND EXISTS (
            SELECT 1 FROM commercial_payments payment
            WHERE payment.invoice_id = NEW.id
        ))
    THEN
        RAISE EXCEPTION 'Commercial invoice permits only ISSUED to PAID or VOID transitions'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER commercial_invoice_state_guard
    BEFORE UPDATE ON commercial_invoices
    FOR EACH ROW EXECUTE FUNCTION rabbit_validate_commercial_invoice_update();
