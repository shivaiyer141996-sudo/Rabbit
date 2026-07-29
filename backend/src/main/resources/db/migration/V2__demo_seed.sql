INSERT INTO organisations (
    id, code, name, timezone, status, created_at, updated_at
) VALUES (
    '11111111-1111-1111-1111-111111111111',
    'DEMO',
    'Rabbit Demo Academy',
    'Asia/Kolkata',
    'ACTIVE',
    now(),
    now()
);

INSERT INTO academic_years (
    id, organisation_id, name, start_date, end_date, active
) VALUES (
    '11111111-1111-1111-1111-111111111120',
    '11111111-1111-1111-1111-111111111111',
    '2026-27',
    DATE '2026-04-01',
    DATE '2027-03-31',
    TRUE
);

INSERT INTO departments (id, organisation_id, name) VALUES
    ('11111111-1111-1111-1111-111111111130', '11111111-1111-1111-1111-111111111111', 'Science'),
    ('11111111-1111-1111-1111-111111111131', '11111111-1111-1111-1111-111111111111', 'Medical'),
    ('11111111-1111-1111-1111-111111111132', '11111111-1111-1111-1111-111111111111', 'Management');

INSERT INTO sections (id, organisation_id, department_id, name) VALUES
    ('11111111-1111-1111-1111-111111111140', '11111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111130', 'JEE 2027 - Batch A'),
    ('11111111-1111-1111-1111-111111111141', '11111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111130', 'JEE 2027 - Batch B'),
    ('11111111-1111-1111-1111-111111111142', '11111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111131', 'NEET 2027 - Batch A');

INSERT INTO subjects (id, organisation_id, code, name) VALUES
    ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111111', 'PHY', 'Physics'),
    ('22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111111', 'CHE', 'Chemistry'),
    ('22222222-2222-2222-2222-222222222203', '11111111-1111-1111-1111-111111111111', 'MAT', 'Mathematics'),
    ('22222222-2222-2222-2222-222222222204', '11111111-1111-1111-1111-111111111111', 'BIO', 'Biology');

INSERT INTO topics (id, organisation_id, subject_id, name) VALUES
    ('22222222-2222-2222-2222-222222222211', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222201', 'Motion in a Straight Line'),
    ('22222222-2222-2222-2222-222222222212', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222202', 'Organic Chemistry'),
    ('22222222-2222-2222-2222-222222222213', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222203', 'Differential Calculus'),
    ('22222222-2222-2222-2222-222222222214', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222204', 'Cell Biology');

INSERT INTO user_accounts (
    id, email, password_hash, first_name, last_name, status,
    failed_attempts, first_login, created_at, updated_at
) VALUES
    ('33333333-3333-3333-3333-333333333301', 'admin@demo.rabbit.local', crypt('Rabbit@123', gen_salt('bf', 12)), 'Ananya', 'Rao', 'ACTIVE', 0, FALSE, now(), now()),
    ('33333333-3333-3333-3333-333333333302', 'faculty@demo.rabbit.local', crypt('Rabbit@123', gen_salt('bf', 12)), 'Sanjay', 'Mehta', 'ACTIVE', 0, FALSE, now(), now()),
    ('33333333-3333-3333-3333-333333333303', 'reviewer@demo.rabbit.local', crypt('Rabbit@123', gen_salt('bf', 12)), 'Priya', 'Menon', 'ACTIVE', 0, FALSE, now(), now()),
    ('33333333-3333-3333-3333-333333333304', 'student@demo.rabbit.local', crypt('Rabbit@123', gen_salt('bf', 12)), 'Rohan', 'Iyer', 'ACTIVE', 0, FALSE, now(), now());

INSERT INTO organisation_memberships (
    id, organisation_id, user_id, role, status, section_id, created_at, updated_at
) VALUES
    ('44444444-4444-4444-4444-444444444401', '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333301', 'ORG_ADMIN', 'ACTIVE', NULL, now(), now()),
    ('44444444-4444-4444-4444-444444444402', '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333302', 'FACULTY', 'ACTIVE', NULL, now(), now()),
    ('44444444-4444-4444-4444-444444444403', '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333303', 'REVIEWER', 'ACTIVE', NULL, now(), now()),
    ('44444444-4444-4444-4444-444444444404', '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333304', 'STUDENT', 'ACTIVE', '11111111-1111-1111-1111-111111111140', now(), now());

INSERT INTO questions (
    id, organisation_id, code, stem, type, subject_id, topic_id, sub_topic,
    difficulty, bloom_level, marks, negative_marks, status, version,
    explanation, language, author_user_id, reviewed_by, approved_by,
    created_at, updated_at
) VALUES
    (
        '55555555-5555-5555-5555-555555555501',
        '11111111-1111-1111-1111-111111111111',
        'PHY-MEC-001',
        'A body starts from rest and moves with constant acceleration. Which graph correctly represents its displacement against time?',
        'SINGLE_CORRECT',
        '22222222-2222-2222-2222-222222222201',
        '22222222-2222-2222-2222-222222222211',
        'Uniform acceleration',
        'MEDIUM',
        'APPLY',
        4,
        1,
        'APPROVED',
        1,
        'For constant acceleration from rest, s = 1/2 at^2.',
        'en',
        '33333333-3333-3333-3333-333333333302',
        '33333333-3333-3333-3333-333333333303',
        '33333333-3333-3333-3333-333333333303',
        now(),
        now()
    ),
    (
        '55555555-5555-5555-5555-555555555502',
        '11111111-1111-1111-1111-111111111111',
        'PHY-MEC-002',
        'Which statements are true for a body moving with uniform velocity?',
        'MULTIPLE_CORRECT',
        '22222222-2222-2222-2222-222222222201',
        '22222222-2222-2222-2222-222222222211',
        NULL,
        'MEDIUM',
        'UNDERSTAND',
        4,
        1,
        'APPROVED',
        1,
        'Uniform velocity has constant magnitude and direction, hence zero acceleration.',
        'en',
        '33333333-3333-3333-3333-333333333302',
        '33333333-3333-3333-3333-333333333303',
        '33333333-3333-3333-3333-333333333303',
        now(),
        now()
    ),
    (
        '55555555-5555-5555-5555-555555555503',
        '11111111-1111-1111-1111-111111111111',
        'CHE-ORG-014',
        'Which of the following compounds can exhibit geometrical isomerism?',
        'MULTIPLE_CORRECT',
        '22222222-2222-2222-2222-222222222202',
        '22222222-2222-2222-2222-222222222212',
        'Isomerism',
        'HARD',
        'ANALYSE',
        4,
        1,
        'UNDER_REVIEW',
        1,
        'Each double-bond carbon must have two different substituents.',
        'en',
        '33333333-3333-3333-3333-333333333302',
        NULL,
        NULL,
        now(),
        now()
    );

INSERT INTO question_options (
    id, question_id, label, text, correct, sort_order, created_at, updated_at
) VALUES
    ('66666666-6666-6666-6666-666666666601', '55555555-5555-5555-5555-555555555501', 'A', 'A straight line through the origin', FALSE, 0, now(), now()),
    ('66666666-6666-6666-6666-666666666602', '55555555-5555-5555-5555-555555555501', 'B', 'A parabola opening upward', TRUE, 1, now(), now()),
    ('66666666-6666-6666-6666-666666666603', '55555555-5555-5555-5555-555555555501', 'C', 'A horizontal line', FALSE, 2, now(), now()),
    ('66666666-6666-6666-6666-666666666604', '55555555-5555-5555-5555-555555555501', 'D', 'A parabola opening downward', FALSE, 3, now(), now()),
    ('66666666-6666-6666-6666-666666666611', '55555555-5555-5555-5555-555555555502', 'A', 'Its acceleration is zero', TRUE, 0, now(), now()),
    ('66666666-6666-6666-6666-666666666612', '55555555-5555-5555-5555-555555555502', 'B', 'It covers equal displacement in equal intervals', TRUE, 1, now(), now()),
    ('66666666-6666-6666-6666-666666666613', '55555555-5555-5555-5555-555555555502', 'C', 'Its speed must continuously increase', FALSE, 2, now(), now()),
    ('66666666-6666-6666-6666-666666666614', '55555555-5555-5555-5555-555555555502', 'D', 'Its velocity-time graph is horizontal', TRUE, 3, now(), now()),
    ('66666666-6666-6666-6666-666666666621', '55555555-5555-5555-5555-555555555503', 'A', '2-butene', TRUE, 0, now(), now()),
    ('66666666-6666-6666-6666-666666666622', '55555555-5555-5555-5555-555555555503', 'B', '1-butene', FALSE, 1, now(), now()),
    ('66666666-6666-6666-6666-666666666623', '55555555-5555-5555-5555-555555555503', 'C', '1,2-dichloroethene', TRUE, 2, now(), now()),
    ('66666666-6666-6666-6666-666666666624', '55555555-5555-5555-5555-555555555503', 'D', '2-methylpropene', FALSE, 3, now(), now());

INSERT INTO assessments (
    id, organisation_id, title, code, type, subject_id, duration_minutes,
    status, total_marks, question_count, shuffle_questions, shuffle_options,
    partial_marking, attempts_allowed, start_at, end_at, published_at,
    created_by, created_at, updated_at
) VALUES (
    '77777777-7777-7777-7777-777777777701',
    '11111111-1111-1111-1111-111111111111',
    'JEE Physics - Kinematics Foundation',
    'JEE-PHY-04',
    'CHAPTER_TEST',
    '22222222-2222-2222-2222-222222222201',
    45,
    'SCHEDULED',
    8,
    2,
    FALSE,
    FALSE,
    TRUE,
    1,
    TIMESTAMPTZ '2026-01-01 00:00:00+05:30',
    TIMESTAMPTZ '2027-12-31 23:59:59+05:30',
    now(),
    '33333333-3333-3333-3333-333333333302',
    now(),
    now()
);

INSERT INTO assessment_question_ids (
    assessment_id, question_id, display_order
) VALUES
    ('77777777-7777-7777-7777-777777777701', '55555555-5555-5555-5555-555555555501', 0),
    ('77777777-7777-7777-7777-777777777701', '55555555-5555-5555-5555-555555555502', 1);

INSERT INTO assessment_eligible_sections (assessment_id, section_id) VALUES (
    '77777777-7777-7777-7777-777777777701',
    '11111111-1111-1111-1111-111111111140'
);
