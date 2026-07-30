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
