\set ON_ERROR_STOP on

DO $$
DECLARE missing_accounts INTEGER;
BEGIN
    SELECT count(*) INTO missing_accounts
    FROM organisations WHERE customer_account_id IS NULL;
    IF missing_accounts <> 0 THEN
        RAISE EXCEPTION 'Existing Organisation migration left % rows without Customer Accounts', missing_accounts;
    END IF;
END;
$$;

DO $$
DECLARE entitlement_count INTEGER;
BEGIN
    SELECT count(*) INTO entitlement_count FROM commercial_plan_entitlements;
    IF entitlement_count <> 8 THEN
        RAISE EXCEPTION 'Expected 8 plan-entitlement rows, found %', entitlement_count;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM rabbit_platform_settings
        WHERE default_trial_days = 20 AND default_trial_plan_code = 'LEGEND'
          AND trial_reminder_days = ARRAY[7,3,1]
    ) THEN
        RAISE EXCEPTION 'Rabbit trial defaults are invalid';
    END IF;
END;
$$;

DO $$
BEGIN
    BEGIN
        UPDATE organisations
        SET logo_object_key = 'bad/object.exe',
            logo_content_type = 'application/octet-stream',
            logo_file_name = 'bad.exe',
            logo_size_bytes = 10,
            logo_updated_at = now()
        WHERE id = (SELECT id FROM organisations LIMIT 1);
        RAISE EXCEPTION 'Organisation logo metadata accepted an invalid type';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'Invalid Organisation logo metadata correctly rejected';
    END;
END;
$$;

DO $$
DECLARE organisation_record organisations%ROWTYPE;
DECLARE wrong_institute UUID;
BEGIN
    SELECT * INTO organisation_record FROM organisations ORDER BY id LIMIT 1;
    SELECT institute.id INTO wrong_institute FROM institutes institute
    WHERE institute.organisation_id <> organisation_record.id LIMIT 1;
    IF wrong_institute IS NOT NULL THEN
        BEGIN
            UPDATE departments SET institute_id = wrong_institute
            WHERE organisation_id = organisation_record.id;
            RAISE EXCEPTION 'Department accepted a cross-Organisation Institute';
        EXCEPTION WHEN check_violation THEN
            RAISE NOTICE 'Cross-Organisation Institute correctly rejected';
        END;
    END IF;
END;
$$;

DO $$
BEGIN
    BEGIN
        UPDATE commercial_plan_prices SET monthly_price_paise = -1
        WHERE id = (SELECT id FROM commercial_plan_prices LIMIT 1);
        RAISE EXCEPTION 'Commercial plan accepted a negative price';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'Negative plan price correctly rejected';
    END;
END;
$$;

DO $$
BEGIN
    BEGIN
        DELETE FROM customer_accounts WHERE id = (SELECT id FROM customer_accounts LIMIT 1);
        RAISE EXCEPTION 'Customer Account hard delete was accepted';
    EXCEPTION WHEN object_not_in_prerequisite_state THEN
        RAISE NOTICE 'Customer Account hard delete correctly rejected';
    END;
END;
$$;
