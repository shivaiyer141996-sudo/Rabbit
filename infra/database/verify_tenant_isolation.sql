\set ON_ERROR_STOP on

BEGIN;

INSERT INTO organisations (
    id, code, name, timezone, status, created_at, updated_at
) VALUES (
    '12121212-1212-1212-1212-121212121212',
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

ROLLBACK;
