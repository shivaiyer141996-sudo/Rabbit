CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE organisations (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    timezone VARCHAR(80) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE academic_years (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    name VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT academic_year_dates_valid CHECK (start_date < end_date)
);
CREATE UNIQUE INDEX one_active_academic_year_per_org
    ON academic_years (organisation_id) WHERE active;

CREATE TABLE departments (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    name VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organisation_id, name)
);

CREATE TABLE sections (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    department_id UUID REFERENCES departments(id),
    name VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organisation_id, name)
);

CREATE TABLE subjects (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    code VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organisation_id, code)
);

CREATE TABLE topics (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    subject_id UUID NOT NULL REFERENCES subjects(id),
    name VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (subject_id, name)
);

CREATE TABLE user_accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    first_login BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE organisation_memberships (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    user_id UUID NOT NULL REFERENCES user_accounts(id),
    role VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    section_id UUID REFERENCES sections(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (organisation_id, user_id)
);
CREATE INDEX idx_membership_user ON organisation_memberships(user_id);
CREATE INDEX idx_membership_org ON organisation_memberships(organisation_id);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES user_accounts(id),
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    membership_id UUID NOT NULL REFERENCES organisation_memberships(id),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_refresh_token_user ON refresh_tokens(user_id);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    actor_user_id UUID NOT NULL REFERENCES user_accounts(id),
    module VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID,
    status VARCHAR(20) NOT NULL,
    before_value TEXT,
    after_value TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_audit_org_created ON audit_events(organisation_id, created_at DESC);

CREATE TABLE questions (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    code VARCHAR(50) NOT NULL,
    stem TEXT NOT NULL,
    type VARCHAR(30) NOT NULL,
    subject_id UUID NOT NULL REFERENCES subjects(id),
    topic_id UUID NOT NULL REFERENCES topics(id),
    sub_topic VARCHAR(200),
    difficulty VARCHAR(20) NOT NULL,
    bloom_level VARCHAR(30) NOT NULL,
    marks NUMERIC(10, 2) NOT NULL,
    negative_marks NUMERIC(10, 2) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    explanation TEXT,
    language VARCHAR(20) NOT NULL DEFAULT 'en',
    author_user_id UUID NOT NULL REFERENCES user_accounts(id),
    reviewed_by UUID REFERENCES user_accounts(id),
    approved_by UUID REFERENCES user_accounts(id),
    parent_question_id UUID REFERENCES questions(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (organisation_id, code, version),
    CONSTRAINT question_marks_positive CHECK (marks > 0),
    CONSTRAINT question_negative_marks_valid
        CHECK (negative_marks >= 0 AND negative_marks <= marks),
    CONSTRAINT question_type_release_1
        CHECK (type IN ('SINGLE_CORRECT', 'MULTIPLE_CORRECT'))
);
CREATE INDEX idx_question_tenant_status
    ON questions(organisation_id, status, updated_at DESC);
CREATE INDEX idx_question_subject_topic
    ON questions(organisation_id, subject_id, topic_id);

CREATE TABLE question_options (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    label VARCHAR(3) NOT NULL,
    text TEXT NOT NULL,
    correct BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (question_id, label),
    UNIQUE (question_id, sort_order)
);

CREATE TABLE assessments (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    title VARCHAR(200) NOT NULL,
    code VARCHAR(50) NOT NULL,
    type VARCHAR(40) NOT NULL,
    subject_id UUID NOT NULL REFERENCES subjects(id),
    duration_minutes INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_marks NUMERIC(10, 2) NOT NULL DEFAULT 0,
    question_count INTEGER NOT NULL DEFAULT 0,
    shuffle_questions BOOLEAN NOT NULL DEFAULT FALSE,
    shuffle_options BOOLEAN NOT NULL DEFAULT FALSE,
    partial_marking BOOLEAN NOT NULL DEFAULT FALSE,
    attempts_allowed INTEGER NOT NULL DEFAULT 1,
    start_at TIMESTAMPTZ,
    end_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    created_by UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (organisation_id, code),
    CONSTRAINT assessment_duration_positive CHECK (duration_minutes > 0),
    CONSTRAINT assessment_attempts_positive CHECK (attempts_allowed > 0),
    CONSTRAINT assessment_schedule_valid
        CHECK (start_at IS NULL OR end_at IS NULL OR start_at < end_at)
);
CREATE INDEX idx_assessment_tenant_status
    ON assessments(organisation_id, status, start_at, end_at);

CREATE TABLE assessment_question_ids (
    assessment_id UUID NOT NULL REFERENCES assessments(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES questions(id),
    display_order INTEGER NOT NULL,
    PRIMARY KEY (assessment_id, display_order),
    UNIQUE (assessment_id, question_id)
);

CREATE TABLE assessment_eligible_sections (
    assessment_id UUID NOT NULL REFERENCES assessments(id) ON DELETE CASCADE,
    section_id UUID NOT NULL REFERENCES sections(id),
    PRIMARY KEY (assessment_id, section_id)
);

CREATE TABLE assessment_attempts (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    assessment_id UUID NOT NULL REFERENCES assessments(id),
    student_user_id UUID NOT NULL REFERENCES user_accounts(id),
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ,
    score NUMERIC(10, 2),
    max_score NUMERIC(10, 2),
    percentage NUMERIC(7, 2),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_attempt_student
    ON assessment_attempts(organisation_id, student_user_id, assessment_id);
CREATE UNIQUE INDEX one_open_attempt_per_student_assessment
    ON assessment_attempts(assessment_id, student_user_id)
    WHERE status = 'IN_PROGRESS';

CREATE TABLE attempt_responses (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES assessment_attempts(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES questions(id),
    flagged BOOLEAN NOT NULL DEFAULT FALSE,
    time_spent_seconds INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (attempt_id, question_id)
);

CREATE TABLE response_selected_options (
    response_id UUID NOT NULL REFERENCES attempt_responses(id) ON DELETE CASCADE,
    option_id UUID NOT NULL REFERENCES question_options(id),
    PRIMARY KEY (response_id, option_id)
);
