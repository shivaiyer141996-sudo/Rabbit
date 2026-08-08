-- Multi-subject assessments, governed ranking, and complete section masters.

CREATE TABLE assessment_subject_ids (
    assessment_id UUID NOT NULL REFERENCES assessments(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES subjects(id),
    display_order INTEGER NOT NULL,
    PRIMARY KEY (assessment_id, subject_id),
    UNIQUE (assessment_id, display_order)
);

INSERT INTO assessment_subject_ids (assessment_id, subject_id, display_order)
SELECT id, subject_id, 0 FROM assessments;

CREATE INDEX idx_assessment_subject_lookup
    ON assessment_subject_ids(subject_id, assessment_id);

ALTER TABLE organisation_settings
    ADD COLUMN ranking_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE academic_programmes (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    code VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organisation_id, code)
);

INSERT INTO academic_programmes (id, organisation_id, code, name)
SELECT gen_random_uuid(), organisation_id,
       'PRG-' || upper(substr(md5(id::text), 1, 8)), name
FROM departments;

INSERT INTO academic_programmes (id, organisation_id, code, name)
SELECT gen_random_uuid(), section.organisation_id, 'GENERAL', 'General Programme'
FROM sections section
WHERE section.department_id IS NULL
GROUP BY section.organisation_id;

INSERT INTO academic_years (
    id, organisation_id, name, start_date, end_date, active
)
SELECT gen_random_uuid(), section.organisation_id, 'Migration Default',
       current_date, current_date + 365, TRUE
FROM sections section
WHERE NOT EXISTS (
    SELECT 1 FROM academic_years year
    WHERE year.organisation_id = section.organisation_id
)
GROUP BY section.organisation_id;

CREATE TABLE academic_batches (
    id UUID PRIMARY KEY,
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    programme_id UUID NOT NULL REFERENCES academic_programmes(id),
    academic_year_id UUID NOT NULL REFERENCES academic_years(id),
    name VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organisation_id, programme_id, academic_year_id, name)
);

INSERT INTO academic_batches (
    id, organisation_id, programme_id, academic_year_id, name
)
SELECT gen_random_uuid(), programme.organisation_id, programme.id, year.id,
       year.name || ' Default Batch'
FROM academic_programmes programme
JOIN LATERAL (
    SELECT id, name FROM academic_years
    WHERE organisation_id = programme.organisation_id
    ORDER BY active DESC, start_date DESC
    LIMIT 1
) year ON TRUE;

ALTER TABLE sections
    ADD COLUMN programme_id UUID REFERENCES academic_programmes(id),
    ADD COLUMN academic_year_id UUID REFERENCES academic_years(id),
    ADD COLUMN batch_id UUID REFERENCES academic_batches(id),
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN archived_at TIMESTAMPTZ;

UPDATE sections section
SET programme_id = programme.id
FROM academic_programmes programme, departments department
WHERE department.id = section.department_id
  AND programme.organisation_id = section.organisation_id
  AND programme.name = department.name;

UPDATE sections section
SET programme_id = programme.id
FROM academic_programmes programme
WHERE section.department_id IS NULL
  AND programme.organisation_id = section.organisation_id
  AND programme.code = 'GENERAL';

UPDATE sections section
SET academic_year_id = batch.academic_year_id,
    batch_id = batch.id
FROM academic_batches batch
WHERE batch.organisation_id = section.organisation_id
  AND batch.programme_id = section.programme_id;

UPDATE sections
SET status = CASE WHEN active THEN 'ACTIVE' ELSE 'INACTIVE' END;

ALTER TABLE sections
    ALTER COLUMN programme_id SET NOT NULL,
    ALTER COLUMN academic_year_id SET NOT NULL,
    ALTER COLUMN batch_id SET NOT NULL,
    ADD CONSTRAINT section_status_valid
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    ADD CONSTRAINT section_archive_state_valid
        CHECK ((status = 'ARCHIVED') = (archived_at IS NOT NULL));

ALTER TABLE sections DROP CONSTRAINT IF EXISTS sections_organisation_id_name_key;
CREATE UNIQUE INDEX section_name_per_programme_batch
    ON sections(organisation_id, programme_id, batch_id, lower(name));

CREATE OR REPLACE FUNCTION rabbit_assert_academic_master_tenant()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM academic_programmes programme
        WHERE programme.id = NEW.programme_id
          AND programme.organisation_id = NEW.organisation_id
    ) OR NOT EXISTS (
        SELECT 1 FROM academic_years year
        WHERE year.id = NEW.academic_year_id
          AND year.organisation_id = NEW.organisation_id
    ) OR NOT EXISTS (
        SELECT 1 FROM academic_batches batch
        WHERE batch.id = NEW.batch_id
          AND batch.organisation_id = NEW.organisation_id
          AND batch.programme_id = NEW.programme_id
          AND batch.academic_year_id = NEW.academic_year_id
    ) THEN
        RAISE EXCEPTION 'Section academic masters are outside the tenant or inconsistent'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER section_academic_master_tenant
BEFORE INSERT OR UPDATE ON sections
FOR EACH ROW EXECUTE FUNCTION rabbit_assert_academic_master_tenant();

CREATE OR REPLACE FUNCTION rabbit_assert_assessment_subject_tenant()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM assessments assessment
        JOIN subjects subject
          ON subject.organisation_id = assessment.organisation_id
        WHERE assessment.id = NEW.assessment_id
          AND subject.id = NEW.subject_id
    ) THEN
        RAISE EXCEPTION 'Assessment subject is outside the assessment tenant'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER assessment_subject_tenant
BEFORE INSERT OR UPDATE ON assessment_subject_ids
FOR EACH ROW EXECUTE FUNCTION rabbit_assert_assessment_subject_tenant();
