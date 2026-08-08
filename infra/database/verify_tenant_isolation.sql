\set ON_ERROR_STOP on

BEGIN;

INSERT INTO customer_accounts (
    id, code, name, status, created_at, updated_at
) VALUES (
    '10101010-1010-1010-1010-101010101010',
    'CA-ISOLATION',
    'Isolation Contract Account',
    'ACTIVE',
    now(),
    now()
);

INSERT INTO organisations (
    id, customer_account_id, code, name, timezone, status, created_at, updated_at
) VALUES (
    '12121212-1212-1212-1212-121212121212',
    '10101010-1010-1010-1010-101010101010',
    'ISOLATION',
    'Isolation Contract Academy',
    'Asia/Kolkata',
    'ACTIVE',
    now(),
    now()
);

INSERT INTO subjects (
    id, organisation_id, code, name, active, created_at, updated_at
) VALUES (
    '23232323-2323-2323-2323-232323232323',
    '12121212-1212-1212-1212-121212121212',
    'ISO',
    'Isolation Subject',
    TRUE,
    now(),
    now()
);

INSERT INTO topics (
    id, organisation_id, subject_id, name, active, created_at, updated_at
) VALUES (
    '24242424-2424-2424-2424-242424242424',
    '12121212-1212-1212-1212-121212121212',
    '23232323-2323-2323-2323-232323232323',
    'Isolation Topic',
    TRUE,
    now(),
    now()
);

INSERT INTO academic_years (
    id, organisation_id, name, start_date, end_date, active
) VALUES (
    '31313131-3131-3131-3131-313131313131',
    '12121212-1212-1212-1212-121212121212',
    '2026-27 Isolation', DATE '2026-04-01', DATE '2027-03-31', TRUE
);

INSERT INTO academic_programmes (
    id, organisation_id, code, name
) VALUES (
    '32323232-3232-3232-3232-323232323232',
    '12121212-1212-1212-1212-121212121212',
    'ISO-PRG', 'Isolation Programme'
);

INSERT INTO academic_batches (
    id, organisation_id, programme_id, academic_year_id, name
) VALUES (
    '33333333-3232-3232-3232-323232323232',
    '12121212-1212-1212-1212-121212121212',
    '32323232-3232-3232-3232-323232323232',
    '31313131-3131-3131-3131-313131313131',
    'Isolation Batch'
);

INSERT INTO questions (
    id, organisation_id, code, stem, type, subject_id, topic_id,
    difficulty, bloom_level, marks, negative_marks, status, version,
    language, author_user_id, created_at, updated_at
) VALUES (
    '25252525-2525-2525-2525-252525252525',
    '12121212-1212-1212-1212-121212121212',
    'ISO-001',
    'This question belongs to another tenant.',
    'SINGLE_CORRECT',
    '23232323-2323-2323-2323-232323232323',
    '24242424-2424-2424-2424-242424242424',
    'EASY',
    'REMEMBER',
    1,
    0,
    'APPROVED',
    1,
    'en',
    '33333333-3333-3333-3333-333333333301',
    now(),
    now()
);

DO $$
BEGIN
    BEGIN
        INSERT INTO assessment_question_ids (
            assessment_id, question_id, display_order
        ) VALUES (
            '77777777-7777-7777-7777-777777777701',
            '25252525-2525-2525-2525-252525252525',
            99
        );
        RAISE EXCEPTION 'Tenant guard accepted a cross-tenant assessment question';
    EXCEPTION
        WHEN check_violation THEN
            RAISE NOTICE 'Cross-tenant assessment question correctly rejected';
    END;
END;
$$;

DO $$
BEGIN
    BEGIN
        INSERT INTO sections (
            id, organisation_id, department_id, name, active,
            programme_id, academic_year_id, batch_id, status
        )
        SELECT gen_random_uuid(), organisation_id, department_id, lower(name), TRUE,
               programme_id, academic_year_id, batch_id, 'ACTIVE'
        FROM sections
        WHERE organisation_id = '11111111-1111-1111-1111-111111111111'
        LIMIT 1;
        RAISE EXCEPTION 'Section uniqueness accepted a case-insensitive duplicate';
    EXCEPTION
        WHEN unique_violation THEN
            RAISE NOTICE 'Case-insensitive section duplicate correctly rejected';
    END;
END;
$$;

DO $$
BEGIN
    BEGIN
        INSERT INTO assessment_subject_ids (assessment_id, subject_id, display_order)
        VALUES (
            '77777777-7777-7777-7777-777777777701',
            '23232323-2323-2323-2323-232323232323',
            99
        );
        RAISE EXCEPTION 'Tenant guard accepted a cross-tenant assessment subject';
    EXCEPTION
        WHEN check_violation THEN
            RAISE NOTICE 'Cross-tenant assessment subject correctly rejected';
    END;
END;
$$;

DO $$
BEGIN
    BEGIN
        INSERT INTO sections (
            id, organisation_id, name, active, programme_id,
            academic_year_id, batch_id, status
        ) VALUES (
            '34343434-3434-3434-3434-343434343434',
            '11111111-1111-1111-1111-111111111111',
            'Cross Tenant Section', TRUE,
            '32323232-3232-3232-3232-323232323232',
            '31313131-3131-3131-3131-313131313131',
            '33333333-3232-3232-3232-323232323232',
            'ACTIVE'
        );
        RAISE EXCEPTION 'Section guard accepted cross-tenant academic masters';
    EXCEPTION
        WHEN check_violation THEN
            RAISE NOTICE 'Cross-tenant section masters correctly rejected';
    END;
END;
$$;

DO $$
BEGIN
    BEGIN
        INSERT INTO response_selected_options (response_id, option_id)
        VALUES (
            '99999999-9999-9999-9999-999999999911',
            '66666666-6666-6666-6666-666666666611'
        );
        RAISE EXCEPTION 'Parent guard accepted an option from another question';
    EXCEPTION
        WHEN check_violation THEN
            RAISE NOTICE 'Cross-question option correctly rejected';
    END;
END;
$$;

DO $$
BEGIN
    BEGIN
        INSERT INTO pilot_check_results (
            id, organisation_id, check_key, status, updated_by,
            created_at, updated_at
        ) VALUES (
            '26262626-2626-2626-2626-262626262626',
            '12121212-1212-1212-1212-121212121212',
            'ADMIN_LOGIN',
            'PASS',
            '33333333-3333-3333-3333-333333333301',
            now(),
            now()
        );
        RAISE EXCEPTION 'Pilot guard accepted an actor from another tenant';
    EXCEPTION
        WHEN check_violation THEN
            RAISE NOTICE 'Cross-tenant pilot actor correctly rejected';
    END;
END;
$$;

DO $$
BEGIN
    BEGIN
        INSERT INTO pilot_sign_offs (
            id, organisation_id, release_version, authorised_by,
            authoriser_title, support_contact, rollback_owner, signed_at,
            signed_by_user_id, created_at, updated_at
        ) VALUES (
            '27272727-2727-2727-2727-272727272727',
            '12121212-1212-1212-1212-121212121212',
            '1.0.0-pilot',
            'Isolation Tester',
            'Pilot Owner',
            'support@example.test',
            'Rollback Owner',
            now(),
            '33333333-3333-3333-3333-333333333301',
            now(),
            now()
        );
        RAISE EXCEPTION 'Pilot sign-off guard accepted an actor from another tenant';
    EXCEPTION
        WHEN check_violation THEN
            RAISE NOTICE 'Cross-tenant pilot sign-off actor correctly rejected';
    END;
END;
$$;

DO $$
BEGIN
    BEGIN
        INSERT INTO invitation_tokens (
            id, organisation_id, user_id, membership_id, token_hash,
            expires_at, created_by_user_id, created_at, updated_at
        ) VALUES (
            '28282828-2828-2828-2828-282828282828',
            '12121212-1212-1212-1212-121212121212',
            '33333333-3333-3333-3333-333333333301',
            '44444444-4444-4444-4444-444444444401',
            repeat('a', 64),
            now() + interval '24 hours',
            '33333333-3333-3333-3333-333333333301',
            now(),
            now()
        );
        RAISE EXCEPTION 'Invitation guard accepted a cross-tenant membership';
    EXCEPTION
        WHEN check_violation THEN
            RAISE NOTICE 'Cross-tenant invitation correctly rejected';
    END;
END;
$$;

DO $$
DECLARE
    flag_count INTEGER;
BEGIN
    SELECT count(*) INTO flag_count
    FROM feature_flags
    WHERE organisation_id = '11111111-1111-1111-1111-111111111111';
    IF flag_count <> 6 THEN
        RAISE EXCEPTION 'Expected six GA feature flags, found %', flag_count;
    END IF;
END;
$$;

DO $$
BEGIN
    BEGIN
        INSERT INTO organisation_subscriptions (
            id, organisation_id, plan_code, student_limit,
            selected_plan_code, selected_student_limit,
            monthly_price_paise, status, trial_enabled, trial_duration_days,
            trial_plan_code, trial_starts_at, trial_ends_at,
            payment_status, amount_paise, activation_date,
            created_by_user_id, updated_by_user_id, row_version,
            created_at, updated_at
        ) VALUES (
            '29292929-2929-2929-2929-292929292929',
            '12121212-1212-1212-1212-121212121212',
            'LEGEND',
            50,
            'LEGEND',
            50,
            149900,
            'TRIAL',
            TRUE,
            20,
            'LEGEND',
            TIMESTAMPTZ '2026-09-01 00:00:00+00',
            TIMESTAMPTZ '2026-09-21 00:00:00+00',
            'PENDING',
            149900,
            TIMESTAMPTZ '2026-09-01 00:00:00+00',
            '33333333-3333-3333-3333-333333333301',
            '33333333-3333-3333-3333-333333333301',
            0,
            now(),
            now()
        );
        RAISE EXCEPTION 'Commercial guard accepted an actor from another tenant';
    EXCEPTION
        WHEN check_violation THEN
            RAISE NOTICE 'Cross-tenant commercial actor correctly rejected';
    END;
END;
$$;

DO $$
BEGIN
    BEGIN
        INSERT INTO organisation_subscriptions (
            id, organisation_id, plan_code, student_limit,
            selected_plan_code, selected_student_limit,
            monthly_price_paise, status, trial_enabled,
            trial_starts_at, trial_ends_at, payment_status,
            amount_paise, activation_date,
            created_by_user_id, updated_by_user_id, row_version,
            created_at, updated_at
        ) VALUES (
            '30303030-3030-3030-3030-303030303030',
            '11111111-1111-1111-1111-111111111111',
            'BASIC',
            50,
            'BASIC',
            50,
            1,
            'EXPIRED',
            FALSE,
            NULL,
            NULL,
            'PENDING',
            1,
            now(),
            '33333333-3333-3333-3333-333333333301',
            '33333333-3333-3333-3333-333333333301',
            0,
            now(),
            now()
        );
        RAISE EXCEPTION 'Commercial price guard accepted an altered plan price';
    EXCEPTION
        WHEN check_violation THEN
            RAISE NOTICE 'Altered commercial plan price correctly rejected';
    END;
END;
$$;

ROLLBACK;
