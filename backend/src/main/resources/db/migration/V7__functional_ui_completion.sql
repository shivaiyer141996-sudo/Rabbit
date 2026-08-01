-- Milestone 4.2 read paths and server-enforced assessment expiry.

-- The expiry worker scans across every tenant and locks only due in-progress rows.
-- This partial index avoids a full-table scan without weakening tenant ownership.
CREATE INDEX idx_attempt_expiry_due
    ON assessment_attempts(expires_at, id)
    WHERE status = 'IN_PROGRESS';

-- Student history and assessment monitoring are now first-class UI journeys.
CREATE INDEX idx_attempt_student_history
    ON assessment_attempts(organisation_id, student_user_id, started_at DESC);

CREATE INDEX idx_attempt_assessment_monitor
    ON assessment_attempts(organisation_id, assessment_id, started_at DESC);
