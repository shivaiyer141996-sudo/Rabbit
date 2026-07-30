CREATE TABLE feature_flags (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    flag_key VARCHAR(60) NOT NULL,
    enabled BOOLEAN NOT NULL,
    rollout_percentage INTEGER NOT NULL,
    description VARCHAR(300) NOT NULL,
    updated_by UUID REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (organisation_id, flag_key),
    CONSTRAINT feature_flag_rollout_valid
        CHECK (rollout_percentage BETWEEN 0 AND 100)
);

INSERT INTO feature_flags (
    id, organisation_id, flag_key, enabled, rollout_percentage,
    description, updated_by, created_at, updated_at
) VALUES
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01',
        '11111111-1111-1111-1111-111111111111',
        'PDF_EXPORTS', TRUE, 100,
        'Allow styled PDF downloads for governed assessment reports.',
        NULL, now(), now()
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa02',
        '11111111-1111-1111-1111-111111111111',
        'EXCEL_EXPORTS', TRUE, 100,
        'Allow native XLSX downloads for governed assessment reports.',
        NULL, now(), now()
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa03',
        '11111111-1111-1111-1111-111111111111',
        'OPERATIONS_CONSOLE', TRUE, 100,
        'Expose tenant-scoped GA health, traffic, and workflow indicators.',
        NULL, now(), now()
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa04',
        '11111111-1111-1111-1111-111111111111',
        'PILOT_MODE', TRUE, 100,
        'Show pilot-readiness controls while Release 1.0 is introduced.',
        NULL, now(), now()
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa05',
        '11111111-1111-1111-1111-111111111111',
        'BULK_IMPORTS', FALSE, 0,
        'Enable guarded question and user bulk-import workflows.',
        NULL, now(), now()
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa06',
        '11111111-1111-1111-1111-111111111111',
        'EXTERNAL_DELIVERY', FALSE, 0,
        'Enable provider-backed email and SMS delivery adapters.',
        NULL, now(), now()
    );

CREATE INDEX idx_attempt_report_scope
    ON assessment_attempts(organisation_id, result_status, submitted_at DESC);
CREATE INDEX idx_attempt_live_scope
    ON assessment_attempts(organisation_id, status, expires_at);
CREATE INDEX idx_response_question
    ON attempt_responses(question_id, attempt_id);
CREATE INDEX idx_question_author
    ON questions(organisation_id, author_user_id, status);
CREATE INDEX idx_assessment_creator
    ON assessments(organisation_id, created_by, status);
CREATE INDEX idx_assessment_review_age
    ON assessments(organisation_id, status, updated_at);
CREATE INDEX idx_notification_tenant_delivery
    ON notifications(organisation_id, delivery_status);
CREATE INDEX idx_audit_filters
    ON audit_events(organisation_id, module, action, created_at DESC);

CREATE OR REPLACE FUNCTION rabbit_assert_tenant_integrity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    expected_scope UUID;
    actual_scope UUID;
BEGIN
    CASE TG_TABLE_NAME
        WHEN 'topics' THEN
            expected_scope := NEW.organisation_id;
            SELECT organisation_id INTO actual_scope
            FROM subjects WHERE id = NEW.subject_id;
        WHEN 'organisation_memberships' THEN
            IF NEW.section_id IS NULL THEN
                RETURN NEW;
            END IF;
            expected_scope := NEW.organisation_id;
            SELECT organisation_id INTO actual_scope
            FROM sections WHERE id = NEW.section_id;
        WHEN 'questions' THEN
            expected_scope := NEW.organisation_id;
            SELECT subject.organisation_id INTO actual_scope
            FROM subjects subject
            JOIN topics topic
              ON topic.id = NEW.topic_id
             AND topic.subject_id = subject.id
             AND topic.organisation_id = subject.organisation_id
            WHERE subject.id = NEW.subject_id;
        WHEN 'assessments' THEN
            expected_scope := NEW.organisation_id;
            SELECT organisation_id INTO actual_scope
            FROM subjects WHERE id = NEW.subject_id;
        WHEN 'assessment_question_ids' THEN
            SELECT organisation_id INTO expected_scope
            FROM assessments WHERE id = NEW.assessment_id;
            SELECT organisation_id INTO actual_scope
            FROM questions WHERE id = NEW.question_id;
        WHEN 'assessment_eligible_sections' THEN
            SELECT organisation_id INTO expected_scope
            FROM assessments WHERE id = NEW.assessment_id;
            SELECT organisation_id INTO actual_scope
            FROM sections WHERE id = NEW.section_id;
        WHEN 'assessment_attempts' THEN
            expected_scope := NEW.organisation_id;
            SELECT organisation_id INTO actual_scope
            FROM assessments WHERE id = NEW.assessment_id;
            IF NOT EXISTS (
                SELECT 1
                FROM organisation_memberships membership
                WHERE membership.organisation_id = NEW.organisation_id
                  AND membership.user_id = NEW.student_user_id
                  AND membership.role = 'STUDENT'
            ) THEN
                RAISE EXCEPTION 'Student is not a member of the attempt organisation'
                    USING ERRCODE = '23514';
            END IF;
        WHEN 'attempt_responses' THEN
            SELECT attempt.organisation_id INTO expected_scope
            FROM assessment_attempts attempt
            WHERE attempt.id = NEW.attempt_id;
            SELECT question.organisation_id INTO actual_scope
            FROM assessment_attempts attempt
            JOIN assessment_question_ids assessment_question
              ON assessment_question.assessment_id = attempt.assessment_id
             AND assessment_question.question_id = NEW.question_id
            JOIN questions question ON question.id = NEW.question_id
            WHERE attempt.id = NEW.attempt_id;
        WHEN 'response_selected_options' THEN
            SELECT question_id INTO expected_scope
            FROM attempt_responses WHERE id = NEW.response_id;
            SELECT question_id INTO actual_scope
            FROM question_options WHERE id = NEW.option_id;
        ELSE
            RAISE EXCEPTION 'Unsupported tenant integrity trigger table: %',
                TG_TABLE_NAME;
    END CASE;

    IF expected_scope IS NULL
       OR actual_scope IS NULL
       OR expected_scope <> actual_scope THEN
        RAISE EXCEPTION 'Cross-tenant or cross-parent reference rejected on %',
            TG_TABLE_NAME
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tenant_guard_topics
    BEFORE INSERT OR UPDATE ON topics
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_tenant_integrity();
CREATE TRIGGER tenant_guard_memberships
    BEFORE INSERT OR UPDATE ON organisation_memberships
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_tenant_integrity();
CREATE TRIGGER tenant_guard_questions
    BEFORE INSERT OR UPDATE ON questions
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_tenant_integrity();
CREATE TRIGGER tenant_guard_assessments
    BEFORE INSERT OR UPDATE ON assessments
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_tenant_integrity();
CREATE TRIGGER tenant_guard_assessment_questions
    BEFORE INSERT OR UPDATE ON assessment_question_ids
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_tenant_integrity();
CREATE TRIGGER tenant_guard_assessment_sections
    BEFORE INSERT OR UPDATE ON assessment_eligible_sections
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_tenant_integrity();
CREATE TRIGGER tenant_guard_attempts
    BEFORE INSERT OR UPDATE ON assessment_attempts
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_tenant_integrity();
CREATE TRIGGER tenant_guard_responses
    BEFORE INSERT OR UPDATE ON attempt_responses
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_tenant_integrity();
CREATE TRIGGER parent_guard_selected_options
    BEFORE INSERT OR UPDATE ON response_selected_options
    FOR EACH ROW EXECUTE FUNCTION rabbit_assert_tenant_integrity();
