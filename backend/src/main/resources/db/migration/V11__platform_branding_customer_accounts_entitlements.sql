-- Platform branding, customer ownership, configurable plans and subscription lifecycle.

CREATE TABLE customer_accounts (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    archived_at TIMESTAMPTZ,
    created_by_user_id UUID REFERENCES user_accounts(id),
    updated_by_user_id UUID REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT customer_account_status_valid
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    CONSTRAINT customer_account_archive_valid
        CHECK ((status = 'ARCHIVED') = (archived_at IS NOT NULL))
);
CREATE UNIQUE INDEX customer_account_code_unique
    ON customer_accounts(lower(code));

-- Preserve each existing tenant as an independently owned commercial account.
INSERT INTO customer_accounts (id, code, name, status, archived_at, created_at, updated_at)
SELECT gen_random_uuid(),
       left('CA-' || organisation.code, 50),
       organisation.name || ' Account',
       CASE WHEN organisation.status = 'ARCHIVED' THEN 'ARCHIVED'
            WHEN organisation.status = 'SUSPENDED' THEN 'SUSPENDED'
            ELSE 'ACTIVE' END,
       CASE WHEN organisation.status = 'ARCHIVED' THEN organisation.updated_at ELSE NULL END,
       organisation.created_at,
       organisation.updated_at
FROM organisations organisation;

ALTER TABLE organisations
    ADD COLUMN customer_account_id UUID REFERENCES customer_accounts(id),
    ADD COLUMN logo_object_key VARCHAR(500),
    ADD COLUMN logo_content_type VARCHAR(50),
    ADD COLUMN logo_file_name VARCHAR(255),
    ADD COLUMN logo_size_bytes BIGINT,
    ADD COLUMN logo_updated_at TIMESTAMPTZ;

UPDATE organisations organisation
SET customer_account_id = account.id
FROM customer_accounts account
WHERE account.code = left('CA-' || organisation.code, 50);

ALTER TABLE organisations
    ALTER COLUMN customer_account_id SET NOT NULL,
    ADD CONSTRAINT organisation_logo_metadata_complete CHECK (
        (logo_object_key IS NULL
            AND logo_content_type IS NULL
            AND logo_file_name IS NULL
            AND logo_size_bytes IS NULL
            AND logo_updated_at IS NULL)
        OR (logo_object_key IS NOT NULL
            AND logo_content_type IN ('image/png', 'image/jpeg', 'image/webp')
            AND logo_file_name IS NOT NULL
            AND logo_size_bytes BETWEEN 1 AND 2097152
            AND logo_updated_at IS NOT NULL)
    );
CREATE INDEX idx_organisation_customer_account
    ON organisations(customer_account_id, status, name);

CREATE TABLE institutes (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    code VARCHAR(30) NOT NULL,
    name VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organisation_id, code)
);
INSERT INTO institutes (id, organisation_id, code, name)
SELECT gen_random_uuid(), id, 'PRIMARY', name FROM organisations;

ALTER TABLE departments ADD COLUMN institute_id UUID REFERENCES institutes(id);
UPDATE departments department
SET institute_id = institute.id
FROM institutes institute
WHERE institute.organisation_id = department.organisation_id
  AND institute.code = 'PRIMARY';
ALTER TABLE departments ALTER COLUMN institute_id SET NOT NULL;
CREATE INDEX idx_department_institute ON departments(institute_id, active, name);

CREATE TABLE commercial_plan_definitions (
    code VARCHAR(20) PRIMARY KEY,
    label VARCHAR(80) NOT NULL,
    description VARCHAR(500) NOT NULL,
    display_order INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO commercial_plan_definitions (code, label, description, display_order) VALUES
    ('BASIC', 'Rabbit Basic', 'Assessment functionality.', 1),
    ('PRO', 'Rabbit Pro', 'Assessment and student evaluation.', 2),
    ('LEGEND', 'Rabbit Legend', 'Full student, teacher and analytics functionality.', 3);

CREATE TABLE commercial_plan_entitlements (
    plan_code VARCHAR(20) NOT NULL REFERENCES commercial_plan_definitions(code),
    feature_code VARCHAR(60) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (plan_code, feature_code),
    CONSTRAINT commercial_feature_code_valid CHECK (feature_code IN (
        'ASSESSMENT_DELIVERY',
        'STUDENT_EVALUATION',
        'INSTITUTION_ANALYTICS',
        'TEACHER_ANALYTICS',
        'REPORT_EXPORTS'
    ))
);
INSERT INTO commercial_plan_entitlements (plan_code, feature_code) VALUES
    ('BASIC', 'ASSESSMENT_DELIVERY'),
    ('PRO', 'ASSESSMENT_DELIVERY'),
    ('PRO', 'STUDENT_EVALUATION'),
    ('LEGEND', 'ASSESSMENT_DELIVERY'),
    ('LEGEND', 'STUDENT_EVALUATION'),
    ('LEGEND', 'INSTITUTION_ANALYTICS'),
    ('LEGEND', 'TEACHER_ANALYTICS'),
    ('LEGEND', 'REPORT_EXPORTS');

CREATE TABLE commercial_plan_prices (
    id UUID PRIMARY KEY,
    plan_code VARCHAR(20) NOT NULL REFERENCES commercial_plan_definitions(code),
    student_limit INTEGER NOT NULL,
    monthly_price_paise BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (plan_code, student_limit),
    CONSTRAINT commercial_capacity_positive CHECK (student_limit > 0),
    CONSTRAINT commercial_price_non_negative CHECK (monthly_price_paise >= 0)
);
INSERT INTO commercial_plan_prices (id, plan_code, student_limit, monthly_price_paise) VALUES
    (gen_random_uuid(), 'BASIC', 50, 59900),
    (gen_random_uuid(), 'BASIC', 150, 99900),
    (gen_random_uuid(), 'BASIC', 500, 149900),
    (gen_random_uuid(), 'PRO', 50, 89900),
    (gen_random_uuid(), 'PRO', 150, 139900),
    (gen_random_uuid(), 'PRO', 500, 189900),
    (gen_random_uuid(), 'LEGEND', 50, 149900),
    (gen_random_uuid(), 'LEGEND', 150, 199900),
    (gen_random_uuid(), 'LEGEND', 500, 249900);

CREATE TABLE rabbit_platform_settings (
    id UUID PRIMARY KEY,
    default_trial_days INTEGER NOT NULL DEFAULT 20,
    default_trial_plan_code VARCHAR(20) NOT NULL REFERENCES commercial_plan_definitions(code),
    trial_reminder_days INTEGER[] NOT NULL DEFAULT ARRAY[7,3,1],
    updated_by_user_id UUID REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT default_trial_days_valid CHECK (default_trial_days BETWEEN 1 AND 365),
    CONSTRAINT reminder_days_valid CHECK (
        cardinality(trial_reminder_days) BETWEEN 1 AND 10
        AND 0 < ALL(trial_reminder_days)
    )
);
INSERT INTO rabbit_platform_settings (
    id, default_trial_days, default_trial_plan_code, trial_reminder_days
) VALUES (
    '00000000-0000-0000-0000-000000000011', 20, 'LEGEND', ARRAY[7,3,1]
);

ALTER TABLE organisation_subscriptions
    DROP CONSTRAINT IF EXISTS commercial_subscription_price_valid,
    DROP CONSTRAINT IF EXISTS commercial_trial_window_valid,
    DROP CONSTRAINT IF EXISTS commercial_subscription_status_valid,
    DROP CONSTRAINT IF EXISTS commercial_pending_change_complete,
    ADD COLUMN trial_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN trial_duration_days INTEGER,
    ADD COLUMN trial_plan_code VARCHAR(20) REFERENCES commercial_plan_definitions(code),
    ADD COLUMN selected_plan_code VARCHAR(20),
    ADD COLUMN selected_student_limit INTEGER,
    ADD COLUMN grace_ends_at TIMESTAMPTZ,
    ADD COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN payment_reference VARCHAR(120),
    ADD COLUMN payment_remarks VARCHAR(1000),
    ADD COLUMN amount_paise BIGINT,
    ADD COLUMN activation_date TIMESTAMPTZ;

UPDATE organisation_subscriptions
SET status = CASE
        WHEN status = 'TRIALING' THEN 'TRIAL'
        WHEN status = 'EXPIRED' AND trial_ends_at IS NOT NULL
            AND period_ends_at IS NULL THEN 'TRIAL_EXPIRED'
        ELSE status
    END,
    trial_enabled = trial_starts_at IS NOT NULL,
    trial_duration_days = CASE WHEN trial_starts_at IS NOT NULL
        THEN GREATEST(1, round(extract(epoch FROM (trial_ends_at - trial_starts_at)) / 86400)::integer)
        ELSE NULL END,
    trial_plan_code = CASE WHEN trial_starts_at IS NOT NULL THEN plan_code ELSE NULL END,
    selected_plan_code = plan_code,
    selected_student_limit = student_limit,
    amount_paise = monthly_price_paise,
    activation_date = COALESCE(trial_starts_at, period_starts_at, created_at),
    payment_status = CASE WHEN period_starts_at IS NOT NULL THEN 'PAID' ELSE 'PENDING' END;

ALTER TABLE organisation_subscriptions
    ALTER COLUMN selected_plan_code SET NOT NULL,
    ALTER COLUMN selected_student_limit SET NOT NULL,
    ADD CONSTRAINT commercial_subscription_status_valid CHECK (status IN (
        'TRIAL', 'ACTIVE', 'TRIAL_EXPIRED', 'GRACE_PERIOD',
        'SUSPENDED', 'CANCELLED', 'EXPIRED'
    )),
    ADD CONSTRAINT commercial_trial_window_valid CHECK (
        (trial_enabled = FALSE AND trial_starts_at IS NULL AND trial_ends_at IS NULL
            AND trial_duration_days IS NULL AND trial_plan_code IS NULL)
        OR (trial_enabled = TRUE AND trial_starts_at IS NOT NULL
            AND trial_duration_days BETWEEN 1 AND 365
            AND trial_ends_at = trial_starts_at + make_interval(days => trial_duration_days)
            AND trial_plan_code IS NOT NULL)
    ),
    ADD CONSTRAINT commercial_pending_change_complete CHECK (
        (pending_plan_code IS NULL
            AND pending_student_limit IS NULL
            AND pending_monthly_price_paise IS NULL
            AND pending_period_starts_at IS NULL
            AND pending_period_ends_at IS NULL
            AND pending_source_invoice_id IS NULL)
        OR (pending_plan_code IS NOT NULL
            AND pending_student_limit IS NOT NULL
            AND pending_monthly_price_paise IS NOT NULL
            AND pending_period_starts_at IS NOT NULL
            AND pending_period_ends_at IS NOT NULL
            AND pending_period_starts_at < pending_period_ends_at)
    ),
    ADD CONSTRAINT commercial_grace_window_valid CHECK (
        grace_ends_at IS NULL OR grace_ends_at > COALESCE(period_ends_at, trial_ends_at)
    ),
    ADD CONSTRAINT commercial_manual_payment_status_valid
        CHECK (payment_status IN ('PENDING', 'PAID', 'WAIVED')),
    ADD CONSTRAINT commercial_manual_amount_valid
        CHECK (amount_paise IS NULL OR amount_paise >= 0);

ALTER TABLE commercial_invoices
    DROP CONSTRAINT IF EXISTS commercial_invoice_amount_valid,
    ADD CONSTRAINT commercial_invoice_amount_valid CHECK (
        subtotal_paise >= 0
        AND tax_paise >= 0
        AND tax_paise <= GREATEST(subtotal_paise, tax_paise)
        AND total_paise = subtotal_paise + tax_paise
    );

ALTER TABLE commercial_subscription_events
    DROP CONSTRAINT IF EXISTS commercial_subscription_event_type_valid,
    ADD CONSTRAINT commercial_subscription_event_type_valid CHECK (event_type IN (
        'SUBSCRIPTION_CREATED', 'TRIAL_STARTED', 'TRIAL_EXTENDED', 'TRIAL_EXPIRED',
        'PLAN_ACTIVATED', 'PLAN_CHANGE_SCHEDULED', 'PLAN_CHANGE_APPLIED',
        'SUBSCRIPTION_RENEWED', 'GRACE_PERIOD_STARTED', 'SUBSCRIPTION_EXPIRED',
        'SUBSCRIPTION_SUSPENDED', 'SUBSCRIPTION_RESTORED',
        'SUBSCRIPTION_CANCELLED', 'PAYMENT_STATUS_CHANGED'
    ));

CREATE TABLE commercial_trial_reminder_log (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    subscription_id UUID NOT NULL REFERENCES organisation_subscriptions(id),
    reminder_days INTEGER NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (subscription_id, reminder_days),
    CONSTRAINT trial_reminder_days_positive CHECK (reminder_days > 0)
);

CREATE OR REPLACE FUNCTION rabbit_assert_customer_account_assignment()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM customer_accounts account
        WHERE account.id = NEW.customer_account_id
          AND account.status <> 'ARCHIVED'
    ) THEN
        RAISE EXCEPTION 'Organisation customer account is unavailable'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER organisation_customer_account_guard
    BEFORE INSERT OR UPDATE OF customer_account_id ON organisations
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_customer_account_assignment();

CREATE OR REPLACE FUNCTION rabbit_assert_department_institute_tenant()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM institutes institute
        WHERE institute.id = NEW.institute_id
          AND institute.organisation_id = NEW.organisation_id
    ) THEN
        RAISE EXCEPTION 'Department institute is outside the organisation tenant'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER department_institute_tenant_guard
    BEFORE INSERT OR UPDATE OF organisation_id, institute_id ON departments
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_department_institute_tenant();

CREATE OR REPLACE FUNCTION rabbit_assert_configured_plan_price()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE expected_price BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'organisation_subscriptions' THEN
        SELECT monthly_price_paise INTO expected_price
        FROM commercial_plan_prices
        WHERE plan_code = NEW.plan_code AND student_limit = NEW.student_limit AND active;
        IF expected_price IS NULL OR expected_price <> NEW.monthly_price_paise THEN
            RAISE EXCEPTION 'Subscription plan/capacity price is not active in the catalogue'
                USING ERRCODE = '23514';
        END IF;
        IF NEW.pending_plan_code IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM commercial_plan_prices
            WHERE plan_code = NEW.pending_plan_code
              AND student_limit = NEW.pending_student_limit
              AND monthly_price_paise = NEW.pending_monthly_price_paise
              AND active
        ) THEN
            RAISE EXCEPTION 'Pending plan/capacity price is not active in the catalogue'
                USING ERRCODE = '23514';
        END IF;
    ELSE
        SELECT monthly_price_paise INTO expected_price
        FROM commercial_plan_prices
        WHERE plan_code = NEW.plan_code AND student_limit = NEW.student_limit AND active;
        IF expected_price IS NULL OR expected_price <> NEW.subtotal_paise THEN
            RAISE EXCEPTION 'Invoice plan/capacity price is not active in the catalogue'
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER configured_subscription_price_guard
    BEFORE INSERT OR UPDATE OF plan_code, student_limit, monthly_price_paise,
        pending_plan_code, pending_student_limit, pending_monthly_price_paise
    ON organisation_subscriptions
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_configured_plan_price();
CREATE TRIGGER configured_invoice_price_guard
    BEFORE INSERT OR UPDATE OF plan_code, student_limit, subtotal_paise
    ON commercial_invoices
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_configured_plan_price();

CREATE OR REPLACE FUNCTION rabbit_enforce_commercial_student_limit()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    permitted_students INTEGER;
    subscription_status VARCHAR;
    access_ends_at TIMESTAMPTZ;
    occupied_students INTEGER;
BEGIN
    IF NEW.role <> 'STUDENT' OR NEW.status NOT IN ('ACTIVE', 'INVITED') THEN
        RETURN NEW;
    END IF;
    SELECT LEAST(subscription.student_limit,
                 COALESCE(subscription.pending_student_limit, subscription.student_limit)),
           subscription.status,
           CASE WHEN subscription.status = 'TRIAL' THEN subscription.trial_ends_at
                WHEN subscription.status = 'GRACE_PERIOD' THEN subscription.grace_ends_at
                ELSE subscription.period_ends_at END
    INTO permitted_students, subscription_status, access_ends_at
    FROM organisation_subscriptions subscription
    WHERE subscription.organisation_id = NEW.organisation_id
    FOR UPDATE;
    IF NOT FOUND THEN RETURN NEW; END IF;
    IF subscription_status NOT IN ('TRIAL', 'ACTIVE', 'GRACE_PERIOD')
        OR access_ends_at IS NULL OR access_ends_at <= now() THEN
        RAISE EXCEPTION 'Cannot activate or invite a Student without commercial access'
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

CREATE TRIGGER immutable_trial_reminder_log
    BEFORE UPDATE OR DELETE ON commercial_trial_reminder_log
    FOR EACH ROW EXECUTE FUNCTION rabbit_reject_commercial_immutable_mutation();

CREATE OR REPLACE FUNCTION rabbit_reject_customer_account_delete()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Customer Accounts must be archived, not deleted'
        USING ERRCODE = '55000';
    RETURN NULL;
END;
$$;
CREATE TRIGGER protected_customer_account_delete
    BEFORE DELETE ON customer_accounts
    FOR EACH ROW EXECUTE FUNCTION rabbit_reject_customer_account_delete();

-- Commercial changes remain tenant-bound. A platform SUPER_ADMIN may cross the
-- tenant boundary only through an authenticated server-side flow.
CREATE OR REPLACE FUNCTION rabbit_assert_commercial_tenant_integrity()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE actor_id UUID;
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
            SELECT 1 FROM commercial_payments payment
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
    IF actor_id IS NULL OR NOT (
        EXISTS (
            SELECT 1 FROM organisation_memberships membership
            WHERE membership.organisation_id = NEW.organisation_id
              AND membership.user_id = actor_id
              AND membership.status = 'ACTIVE'
        ) OR EXISTS (
            SELECT 1 FROM organisation_memberships membership
            WHERE membership.user_id = actor_id
              AND membership.role = 'SUPER_ADMIN'
              AND membership.status = 'ACTIVE'
        )
    ) THEN
        RAISE EXCEPTION 'Commercial actor lacks tenant or platform authority'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION rabbit_assert_trial_reminder_tenant()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM organisation_subscriptions subscription
        WHERE subscription.id = NEW.subscription_id
          AND subscription.organisation_id = NEW.organisation_id
          AND subscription.trial_ends_at IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'Cross-tenant trial reminder rejected'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trial_reminder_tenant_guard
    BEFORE INSERT ON commercial_trial_reminder_log
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_trial_reminder_tenant();
