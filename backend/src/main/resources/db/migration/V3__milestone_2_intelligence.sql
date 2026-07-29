ALTER TABLE audit_events
    ADD COLUMN actor_email VARCHAR(320),
    ADD COLUMN actor_role VARCHAR(30),
    ADD COLUMN ip_address VARCHAR(64),
    ADD COLUMN trace_id VARCHAR(64);

ALTER TABLE assessment_attempts
    ADD COLUMN evaluated_at TIMESTAMPTZ,
    ADD COLUMN grade VARCHAR(20),
    ADD COLUMN correct_answers INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN wrong_answers INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN unanswered_answers INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN result_status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PUBLICATION',
    ADD COLUMN result_published_at TIMESTAMPTZ,
    ADD COLUMN result_published_by UUID REFERENCES user_accounts(id),
    ADD COLUMN evaluation_version INTEGER NOT NULL DEFAULT 1;

UPDATE assessment_attempts
SET evaluated_at = submitted_at
WHERE submitted_at IS NOT NULL AND evaluated_at IS NULL;

ALTER TABLE attempt_responses
    ADD COLUMN awarded_marks NUMERIC(10, 2),
    ADD COLUMN correct BOOLEAN;

CREATE TABLE question_reviews (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    question_id UUID NOT NULL REFERENCES questions(id),
    reviewer_user_id UUID NOT NULL REFERENCES user_accounts(id),
    decision VARCHAR(30) NOT NULL,
    reason TEXT,
    checklist_items TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_question_review_queue
    ON question_reviews(organisation_id, question_id, created_at DESC);

CREATE TABLE assessment_reviews (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    assessment_id UUID NOT NULL REFERENCES assessments(id),
    reviewer_user_id UUID NOT NULL REFERENCES user_accounts(id),
    decision VARCHAR(30) NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_assessment_review_queue
    ON assessment_reviews(organisation_id, assessment_id, created_at DESC);

CREATE TABLE organisation_settings (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL UNIQUE REFERENCES organisations(id),
    timezone VARCHAR(80) NOT NULL,
    language VARCHAR(20) NOT NULL,
    pass_percentage NUMERIC(5, 2) NOT NULL,
    at_risk_threshold NUMERIC(5, 2) NOT NULL,
    default_duration_minutes INTEGER NOT NULL,
    default_attempts_allowed INTEGER NOT NULL,
    shuffle_questions BOOLEAN NOT NULL,
    shuffle_options BOOLEAN NOT NULL,
    email_notifications_enabled BOOLEAN NOT NULL,
    sms_notifications_enabled BOOLEAN NOT NULL,
    audit_retention_days INTEGER NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    primary_colour VARCHAR(7) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT settings_percentages_valid CHECK (
        pass_percentage BETWEEN 0 AND 100
        AND at_risk_threshold BETWEEN 0 AND 100
    ),
    CONSTRAINT settings_defaults_positive CHECK (
        default_duration_minutes > 0
        AND default_attempts_allowed > 0
        AND audit_retention_days >= 365
    )
);

CREATE TABLE grade_bands (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    code VARCHAR(20) NOT NULL,
    label VARCHAR(80) NOT NULL,
    min_percentage NUMERIC(5, 2) NOT NULL,
    max_percentage NUMERIC(5, 2) NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (organisation_id, code),
    UNIQUE (organisation_id, sort_order),
    CONSTRAINT grade_band_range_valid CHECK (
        min_percentage >= 0
        AND max_percentage <= 100
        AND min_percentage <= max_percentage
    )
);

CREATE TABLE notification_preferences (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    user_id UUID NOT NULL REFERENCES user_accounts(id),
    in_app_enabled BOOLEAN NOT NULL,
    email_enabled BOOLEAN NOT NULL,
    sms_enabled BOOLEAN NOT NULL,
    assessment_reminders BOOLEAN NOT NULL,
    workflow_updates BOOLEAN NOT NULL,
    result_updates BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (organisation_id, user_id)
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    recipient_user_id UUID NOT NULL REFERENCES user_accounts(id),
    type VARCHAR(40) NOT NULL,
    title VARCHAR(180) NOT NULL,
    message TEXT NOT NULL,
    action_url VARCHAR(500),
    critical BOOLEAN NOT NULL,
    delivery_status VARCHAR(30) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_notification_inbox
    ON notifications(organisation_id, recipient_user_id, created_at DESC);
CREATE INDEX idx_notification_delivery
    ON notifications(delivery_status, retry_count, created_at);

INSERT INTO organisation_settings (
    id, organisation_id, timezone, language, pass_percentage,
    at_risk_threshold, default_duration_minutes, default_attempts_allowed,
    shuffle_questions, shuffle_options, email_notifications_enabled,
    sms_notifications_enabled, audit_retention_days, display_name,
    primary_colour, created_at, updated_at
) VALUES (
    '88888888-8888-8888-8888-888888888801',
    '11111111-1111-1111-1111-111111111111',
    'Asia/Kolkata',
    'en',
    40,
    40,
    45,
    1,
    TRUE,
    FALSE,
    TRUE,
    FALSE,
    2555,
    'Rabbit Demo Academy',
    '#5936C8',
    now(),
    now()
);

INSERT INTO grade_bands (
    id, organisation_id, code, label, min_percentage, max_percentage,
    sort_order, created_at, updated_at
) VALUES
    ('88888888-8888-8888-8888-888888888811', '11111111-1111-1111-1111-111111111111', 'A', 'Excellent', 80, 100, 1, now(), now()),
    ('88888888-8888-8888-8888-888888888812', '11111111-1111-1111-1111-111111111111', 'B', 'Very Good', 65, 79.99, 2, now(), now()),
    ('88888888-8888-8888-8888-888888888813', '11111111-1111-1111-1111-111111111111', 'C', 'Good', 50, 64.99, 3, now(), now()),
    ('88888888-8888-8888-8888-888888888814', '11111111-1111-1111-1111-111111111111', 'D', 'Developing', 40, 49.99, 4, now(), now()),
    ('88888888-8888-8888-8888-888888888815', '11111111-1111-1111-1111-111111111111', 'F', 'Needs Support', 0, 39.99, 5, now(), now());

INSERT INTO notification_preferences (
    id, organisation_id, user_id, in_app_enabled, email_enabled, sms_enabled,
    assessment_reminders, workflow_updates, result_updates, created_at, updated_at
) VALUES
    ('88888888-8888-8888-8888-888888888821', '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333301', TRUE, TRUE, FALSE, TRUE, TRUE, TRUE, now(), now()),
    ('88888888-8888-8888-8888-888888888822', '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333302', TRUE, TRUE, FALSE, TRUE, TRUE, TRUE, now(), now()),
    ('88888888-8888-8888-8888-888888888823', '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333303', TRUE, TRUE, FALSE, TRUE, TRUE, TRUE, now(), now()),
    ('88888888-8888-8888-8888-888888888824', '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333304', TRUE, TRUE, FALSE, TRUE, TRUE, TRUE, now(), now());

INSERT INTO assessments (
    id, organisation_id, title, code, type, subject_id, duration_minutes,
    status, total_marks, question_count, shuffle_questions, shuffle_options,
    partial_marking, attempts_allowed, start_at, end_at, published_at,
    created_by, created_at, updated_at
) VALUES
    (
        '77777777-7777-7777-7777-777777777702',
        '11111111-1111-1111-1111-111111111111',
        'Physics Motion Diagnostic',
        'PHY-DIA-01',
        'CHAPTER_TEST',
        '22222222-2222-2222-2222-222222222201',
        30,
        'COMPLETED',
        8,
        2,
        FALSE,
        FALSE,
        TRUE,
        1,
        TIMESTAMPTZ '2026-06-15 10:00:00+05:30',
        TIMESTAMPTZ '2026-06-15 10:45:00+05:30',
        TIMESTAMPTZ '2026-06-10 09:00:00+05:30',
        '33333333-3333-3333-3333-333333333302',
        now(),
        now()
    ),
    (
        '77777777-7777-7777-7777-777777777703',
        '11111111-1111-1111-1111-111111111111',
        'Physics Motion Progress Check',
        'PHY-PRG-02',
        'CLASS_TEST',
        '22222222-2222-2222-2222-222222222201',
        30,
        'COMPLETED',
        8,
        2,
        FALSE,
        FALSE,
        TRUE,
        1,
        TIMESTAMPTZ '2026-07-15 10:00:00+05:30',
        TIMESTAMPTZ '2026-07-15 10:45:00+05:30',
        TIMESTAMPTZ '2026-07-10 09:00:00+05:30',
        '33333333-3333-3333-3333-333333333302',
        now(),
        now()
    );

INSERT INTO assessment_question_ids (assessment_id, question_id, display_order) VALUES
    ('77777777-7777-7777-7777-777777777702', '55555555-5555-5555-5555-555555555501', 0),
    ('77777777-7777-7777-7777-777777777702', '55555555-5555-5555-5555-555555555502', 1),
    ('77777777-7777-7777-7777-777777777703', '55555555-5555-5555-5555-555555555501', 0),
    ('77777777-7777-7777-7777-777777777703', '55555555-5555-5555-5555-555555555502', 1);

INSERT INTO assessment_eligible_sections (assessment_id, section_id) VALUES
    ('77777777-7777-7777-7777-777777777702', '11111111-1111-1111-1111-111111111140'),
    ('77777777-7777-7777-7777-777777777703', '11111111-1111-1111-1111-111111111140');

INSERT INTO assessment_attempts (
    id, organisation_id, assessment_id, student_user_id, status, started_at,
    expires_at, submitted_at, score, max_score, percentage, evaluated_at,
    grade, correct_answers, wrong_answers, unanswered_answers, result_status,
    result_published_at, result_published_by, evaluation_version,
    created_at, updated_at
) VALUES
    (
        '99999999-9999-9999-9999-999999999901',
        '11111111-1111-1111-1111-111111111111',
        '77777777-7777-7777-7777-777777777702',
        '33333333-3333-3333-3333-333333333304',
        'SUBMITTED',
        TIMESTAMPTZ '2026-06-15 10:02:00+05:30',
        TIMESTAMPTZ '2026-06-15 10:32:00+05:30',
        TIMESTAMPTZ '2026-06-15 10:26:00+05:30',
        3,
        8,
        37.50,
        TIMESTAMPTZ '2026-06-15 10:26:01+05:30',
        'F',
        1,
        1,
        0,
        'PUBLISHED',
        TIMESTAMPTZ '2026-06-15 12:00:00+05:30',
        '33333333-3333-3333-3333-333333333301',
        1,
        now(),
        now()
    ),
    (
        '99999999-9999-9999-9999-999999999902',
        '11111111-1111-1111-1111-111111111111',
        '77777777-7777-7777-7777-777777777703',
        '33333333-3333-3333-3333-333333333304',
        'SUBMITTED',
        TIMESTAMPTZ '2026-07-15 10:01:00+05:30',
        TIMESTAMPTZ '2026-07-15 10:31:00+05:30',
        TIMESTAMPTZ '2026-07-15 10:24:00+05:30',
        8,
        8,
        100,
        TIMESTAMPTZ '2026-07-15 10:24:01+05:30',
        'A',
        2,
        0,
        0,
        'PUBLISHED',
        TIMESTAMPTZ '2026-07-15 12:00:00+05:30',
        '33333333-3333-3333-3333-333333333301',
        1,
        now(),
        now()
    );

INSERT INTO attempt_responses (
    id, attempt_id, question_id, flagged, time_spent_seconds,
    awarded_marks, correct, created_at, updated_at
) VALUES
    ('99999999-9999-9999-9999-999999999911', '99999999-9999-9999-9999-999999999901', '55555555-5555-5555-5555-555555555501', FALSE, 420, 4, TRUE, now(), now()),
    ('99999999-9999-9999-9999-999999999912', '99999999-9999-9999-9999-999999999901', '55555555-5555-5555-5555-555555555502', FALSE, 510, -1, FALSE, now(), now()),
    ('99999999-9999-9999-9999-999999999913', '99999999-9999-9999-9999-999999999902', '55555555-5555-5555-5555-555555555501', FALSE, 310, 4, TRUE, now(), now()),
    ('99999999-9999-9999-9999-999999999914', '99999999-9999-9999-9999-999999999902', '55555555-5555-5555-5555-555555555502', FALSE, 370, 4, TRUE, now(), now());

INSERT INTO response_selected_options (response_id, option_id) VALUES
    ('99999999-9999-9999-9999-999999999911', '66666666-6666-6666-6666-666666666602'),
    ('99999999-9999-9999-9999-999999999912', '66666666-6666-6666-6666-666666666613'),
    ('99999999-9999-9999-9999-999999999913', '66666666-6666-6666-6666-666666666602'),
    ('99999999-9999-9999-9999-999999999914', '66666666-6666-6666-6666-666666666611'),
    ('99999999-9999-9999-9999-999999999914', '66666666-6666-6666-6666-666666666612'),
    ('99999999-9999-9999-9999-999999999914', '66666666-6666-6666-6666-666666666614');

INSERT INTO question_reviews (
    id, organisation_id, question_id, reviewer_user_id, decision, reason,
    checklist_items, created_at, updated_at
) VALUES
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
        '11111111-1111-1111-1111-111111111111',
        '55555555-5555-5555-5555-555555555501',
        '33333333-3333-3333-3333-333333333303',
        'APPROVE',
        NULL,
        'CLEAR_STEM,PLAUSIBLE_OPTIONS,ANSWER_KEY_VALID,METADATA_VALID,LANGUAGE_VALID,COPYRIGHT_CLEAR,BLOOM_VALID',
        now() - INTERVAL '20 days',
        now() - INTERVAL '20 days'
    );

INSERT INTO assessment_reviews (
    id, organisation_id, assessment_id, reviewer_user_id, decision, reason,
    created_at, updated_at
) VALUES
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaab1',
        '11111111-1111-1111-1111-111111111111',
        '77777777-7777-7777-7777-777777777701',
        '33333333-3333-3333-3333-333333333303',
        'APPROVE',
        NULL,
        now() - INTERVAL '10 days',
        now() - INTERVAL '10 days'
    );

INSERT INTO notifications (
    id, organisation_id, recipient_user_id, type, title, message, action_url,
    critical, delivery_status, retry_count, read_at, created_at, updated_at
) VALUES
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1',
        '11111111-1111-1111-1111-111111111111',
        '33333333-3333-3333-3333-333333333304',
        'RESULT_PUBLISHED',
        'Your result is ready',
        'Physics Motion Progress Check has been evaluated and published.',
        '/results/99999999-9999-9999-9999-999999999902',
        FALSE,
        'DELIVERED',
        0,
        NULL,
        now() - INTERVAL '14 days',
        now() - INTERVAL '14 days'
    ),
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2',
        '11111111-1111-1111-1111-111111111111',
        '33333333-3333-3333-3333-333333333303',
        'WORKFLOW',
        'Question waiting for review',
        'CHE-ORG-014 is ready for academic review.',
        '/approvals',
        TRUE,
        'DELIVERED',
        0,
        NULL,
        now() - INTERVAL '2 hours',
        now() - INTERVAL '2 hours'
    );

INSERT INTO audit_events (
    id, organisation_id, actor_user_id, module, action, entity_type, entity_id,
    status, before_value, after_value, actor_email, actor_role, ip_address,
    trace_id, created_at, updated_at
) VALUES
    (
        'cccccccc-cccc-cccc-cccc-ccccccccccc1',
        '11111111-1111-1111-1111-111111111111',
        '33333333-3333-3333-3333-333333333301',
        'EVL',
        'PUBLISH_RESULTS',
        'Assessment',
        '77777777-7777-7777-7777-777777777703',
        'SUCCESS',
        'PENDING_PUBLICATION',
        'PUBLISHED',
        'admin@demo.rabbit.local',
        'ORG_ADMIN',
        '127.0.0.1',
        'demo-trace-m2',
        TIMESTAMPTZ '2026-07-15 12:00:00+05:30',
        TIMESTAMPTZ '2026-07-15 12:00:00+05:30'
    );
