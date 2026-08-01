export type UserRole =
  | "SUPER_ADMIN"
  | "ORG_ADMIN"
  | "ACADEMIC_HEAD"
  | "FACULTY"
  | "REVIEWER"
  | "STUDENT";

export type QuestionType = "SINGLE_CORRECT" | "MULTIPLE_CORRECT";
export type QuestionStatus =
  | "DRAFT"
  | "UNDER_REVIEW"
  | "APPROVED"
  | "PUBLISHED"
  | "RETIRED";
export type Difficulty = "EASY" | "MEDIUM" | "HARD";
export type BloomLevel =
  | "REMEMBER"
  | "UNDERSTAND"
  | "APPLY"
  | "ANALYSE"
  | "EVALUATE"
  | "CREATE";

export interface QuestionOption {
  id: string;
  label: string;
  text: string;
  correct: boolean;
}

export interface Question {
  id: string;
  code: string;
  stem: string;
  type: QuestionType;
  subject: string;
  topic: string;
  subTopic?: string;
  difficulty: Difficulty;
  bloomLevel: BloomLevel;
  marks: number;
  negativeMarks: number;
  status: QuestionStatus;
  version: number;
  options: QuestionOption[];
  explanation?: string;
  author: string;
  updatedAt: string;
}

export interface AssessmentQuestion {
  id: string;
  stem: string;
  type: QuestionType;
  options: Pick<QuestionOption, "id" | "label" | "text">[];
  marks: number;
}

export interface Assessment {
  id: string;
  title: string;
  code: string;
  type: string;
  subject: string;
  durationMinutes: number;
  questionCount: number;
  totalMarks: number;
  status:
    | "DRAFT"
    | "READY_FOR_REVIEW"
    | "APPROVED"
    | "PUBLISHED"
    | "SCHEDULED"
    | "COMPLETED"
    | "ARCHIVED";
  startAt?: string;
  endAt?: string;
}

export interface DashboardMetric {
  label: string;
  value: string;
  context: string;
  tone: "PRIMARY" | "SUCCESS" | "INFO" | "WARNING" | "DANGER" | "NEUTRAL";
  href: string;
}

export interface DashboardAttention {
  title: string;
  description: string;
  count: number;
  severity: "WARNING" | "INFO" | "DANGER" | "NEUTRAL";
  href: string;
}

export interface DashboardResponse {
  role: UserRole;
  workspaceTitle: string;
  greeting: string;
  description: string;
  metrics: DashboardMetric[];
  trend: Array<{ label: string; value: number }>;
  attention: DashboardAttention[];
  unreadNotifications: number;
}

export interface CountValue {
  label: string;
  value: number;
}

export interface AssessmentSnapshot {
  assessmentId: string;
  title: string;
  status: Assessment["status"];
  submissions: number;
  averagePercentage: number;
  passRate: number;
}

export interface IntelligenceOverview {
  publishedResults: number;
  averageScore: number;
  passRate: number;
  atRiskStudents: number;
  completionRate: number;
  scoreDistribution: CountValue[];
  performanceTrend: Array<{ label: string; value: number }>;
  recentAssessments: AssessmentSnapshot[];
}

export interface QuestionPerformance {
  questionId: string;
  code: string;
  stem: string;
  difficulty: Difficulty;
  usageCount: number;
  responseCount: number;
  correctRate: number;
  difficultyIndex: number;
  discriminationIndex: number;
  poorQuality: boolean;
}

export interface StudentResultPoint {
  attemptId: string;
  assessmentId: string;
  assessmentTitle: string;
  studentName: string;
  submittedAt: string;
  score: number;
  maxScore: number;
  percentage: number;
  grade: string;
  trajectory: "IMPROVING" | "STABLE" | "DECLINING";
}

export interface StudentPerformanceReport {
  studentUserId: string;
  studentName: string;
  averagePercentage: number;
  bestPercentage: number;
  trajectory: "IMPROVING" | "STABLE" | "DECLINING";
  atRisk: boolean;
  results: StudentResultPoint[];
}

export interface StudentReportRow {
  studentUserId: string;
  studentName: string;
  studentEmail: string;
  departmentId?: string;
  departmentName: string;
  sectionId?: string;
  sectionName: string;
  publishedResults: number;
  averagePercentage: number;
  bestPercentage: number;
  latestSubmissionAt?: string;
  trajectory: "IMPROVING" | "STABLE" | "DECLINING";
  atRisk: boolean;
}

export interface StudentGroupComparison {
  groupId?: string;
  label: string;
  studentCount: number;
  publishedResults: number;
  averagePercentage: number;
  passRate: number;
}

export interface StudentReport {
  totalStudents: number;
  studentsWithResults: number;
  publishedResults: number;
  averagePercentage: number;
  atRiskStudents: number;
  students: StudentReportRow[];
  departments: StudentGroupComparison[];
  sections: StudentGroupComparison[];
}

export interface StudentAnalysisBreakdown {
  key: string;
  label: string;
  questionCount: number;
  answeredQuestions: number;
  correctAnswers: number;
  awardedMarks: number;
  maxMarks: number;
  percentage: number;
  averageTimeSeconds: number;
  weak: boolean;
}

export interface StudentQuestionReview {
  attemptId: string;
  assessmentId: string;
  assessmentTitle: string;
  submittedAt: string;
  questionId: string;
  questionCode: string;
  stem: string;
  subjectName: string;
  topicName: string;
  difficulty: Difficulty;
  selectedOptions: Array<{ optionId: string; label: string; text: string }>;
  correctOptions: Array<{ optionId: string; label: string; text: string }>;
  awardedMarks: number;
  maxMarks: number;
  answered: boolean;
  correct: boolean;
  timeSpentSeconds: number;
  explanation?: string;
}

export interface StudentAnalyticsReport {
  studentUserId: string;
  studentName: string;
  publishedAttempts: number;
  analysedQuestions: number;
  averagePercentage: number;
  totalTimeSeconds: number;
  subjects: StudentAnalysisBreakdown[];
  topics: StudentAnalysisBreakdown[];
  difficulties: StudentAnalysisBreakdown[];
  timeAnalysis: Array<{
    attemptId: string;
    assessmentId: string;
    assessmentTitle: string;
    submittedAt: string;
    allowedSeconds: number;
    timeTakenSeconds: number;
    utilisationPercentage: number;
    averageQuestionSeconds: number;
    slowestQuestionSeconds: number;
  }>;
  questionReview: StudentQuestionReview[];
  generatedAt: string;
}

export interface TeacherAnalyticsReport {
  teacherUserId?: string;
  teacherName: string;
  assessmentCount: number;
  publishedSubmissions: number;
  averagePercentage: number;
  weakTopicCount: number;
  batches: Array<{
    sectionId?: string;
    batchName: string;
    studentCount: number;
    assessmentCount: number;
    submissionCount: number;
    studentsAttempted: number;
    completionRate: number;
    averagePercentage: number;
    passRate: number;
  }>;
  students: Array<{
    studentUserId: string;
    studentName: string;
    batchName: string;
    publishedAttempts: number;
    averagePercentage: number;
    bestPercentage: number;
    passRate: number;
    rank: number;
    trajectory: "IMPROVING" | "STABLE" | "DECLINING";
    atRisk: boolean;
  }>;
  weakTopics: Array<{
    subjectId: string;
    subjectName: string;
    topicId: string;
    topicName: string;
    questionCount: number;
    responseCount: number;
    averageMarksPercentage: number;
    correctRate: number;
    averageTimeSeconds: number;
    weak: boolean;
  }>;
  generatedAt: string;
}

export interface AssessmentReport {
  assessmentId: string;
  title: string;
  submissions: number;
  averagePercentage: number;
  highestPercentage: number;
  lowestPercentage: number;
  passRate: number;
  scoreDistribution: CountValue[];
  studentResults: StudentResultPoint[];
  questionAnalytics: QuestionPerformance[];
  generatedAt: string;
  generatedBy: string;
}

export interface EvaluationRow {
  attemptId: string;
  studentUserId: string;
  studentName: string;
  attemptStatus: "SUBMITTED" | "AUTO_SUBMITTED";
  publicationStatus: "PENDING_PUBLICATION" | "PUBLISHED";
  score: number;
  maxScore: number;
  percentage: number;
  grade: string;
  correctAnswers: number;
  wrongAnswers: number;
  unansweredAnswers: number;
  evaluationVersion: number;
  evaluatedAt: string;
  publishedAt?: string;
}

export interface AssessmentEvaluationSummary {
  assessmentId: string;
  assessmentTitle: string;
  evaluatedCount: number;
  pendingPublicationCount: number;
  publishedCount: number;
  averagePercentage: number;
  results: EvaluationRow[];
}

export interface ManualAttemptReview {
  attemptId: string;
  assessmentId: string;
  assessmentTitle: string;
  studentUserId: string;
  studentName: string;
  attemptStatus: "SUBMITTED" | "AUTO_SUBMITTED";
  publicationStatus: "PENDING_PUBLICATION" | "PUBLISHED";
  score: number;
  maxScore: number;
  percentage: number;
  grade: string;
  evaluationVersion: number;
  evaluatedAt: string;
  questions: Array<{
    questionId: string;
    code: string;
    stem: string;
    subjectName: string;
    topicName: string;
    difficulty: Difficulty;
    awardedMarks: number;
    minimumMarks: number;
    maximumMarks: number;
    answered: boolean;
    correct: boolean;
    timeSpentSeconds: number;
    options: Array<{
      optionId: string;
      label: string;
      text: string;
      selected: boolean;
      correct: boolean;
    }>;
    explanation?: string;
  }>;
  auditTrail: Array<{
    eventId: string;
    timestamp: string;
    actorEmail?: string;
    actorRole?: string;
    action: string;
    beforeValue?: string;
    afterValue?: string;
  }>;
}

export interface AssessmentMonitor {
  assessmentId: string;
  assessmentTitle: string;
  generatedAt: string;
  totalAttempts: number;
  inProgress: number;
  submitted: number;
  autoSubmitted: number;
  attempts: Array<{
    attemptId: string;
    studentUserId: string;
    studentName: string;
    attemptStatus: "IN_PROGRESS" | "SUBMITTED" | "AUTO_SUBMITTED";
    publicationStatus: "PENDING_PUBLICATION" | "PUBLISHED";
    startedAt: string;
    expiresAt: string;
    submittedAt?: string;
    answered: number;
    questionCount: number;
    progressPercentage: number;
    secondsRemaining: number;
  }>;
}

export interface FacultyPerformance {
  facultyUserId: string;
  facultyName: string;
  questionsAuthored: number;
  approvedQuestions: number;
  assessmentsCreated: number;
  studentSubmissions: number;
  averageStudentPercentage: number;
}

export interface NotificationItem {
  id: string;
  type:
    | "SYSTEM"
    | "WORKFLOW"
    | "ASSESSMENT_REMINDER"
    | "ASSESSMENT_SUBMITTED"
    | "RESULT_PUBLISHED"
    | "ANNOUNCEMENT"
    | "ALERT";
  title: string;
  message: string;
  actionUrl?: string;
  critical: boolean;
  deliveryStatus: "PENDING" | "DELIVERED" | "FAILED";
  read: boolean;
  createdAt: string;
}

export interface NotificationInbox {
  unreadCount: number;
  items: NotificationItem[];
}

export interface AuditEvent {
  id: string;
  timestamp: string;
  actorUserId: string;
  actorEmail?: string;
  actorRole?: UserRole;
  ipAddress?: string;
  module: string;
  action: string;
  entityType: string;
  entityId?: string;
  status: string;
  beforeValue?: string;
  afterValue?: string;
  traceId?: string;
}

export interface GradeBand {
  id?: string;
  code: string;
  label: string;
  minPercentage: number;
  maxPercentage: number;
  sortOrder?: number;
}

export interface GeneralSettings {
  timezone: string;
  language: string;
  passPercentage: number;
  atRiskThreshold: number;
  defaultDurationMinutes: number;
  defaultAttemptsAllowed: number;
  shuffleQuestions: boolean;
  shuffleOptions: boolean;
  emailNotificationsEnabled: boolean;
  smsNotificationsEnabled: boolean;
  auditRetentionDays: number;
  displayName: string;
  primaryColour: string;
}

export interface SettingsBundle {
  general: GeneralSettings;
  gradeBands: GradeBand[];
  subjects: Array<{ id: string; code: string; name: string; active: boolean }>;
  topics: Array<{ id: string; subjectId: string; name: string; active: boolean }>;
}

export type FeatureFlagKey =
  | "PDF_EXPORTS"
  | "EXCEL_EXPORTS"
  | "OPERATIONS_CONSOLE"
  | "PILOT_MODE"
  | "BULK_IMPORTS"
  | "EXTERNAL_DELIVERY";

export interface FeatureFlag {
  key: FeatureFlagKey;
  label: string;
  description: string;
  enabled: boolean;
  rolloutPercentage: number;
  activeForCurrentUser: boolean;
}

export interface DependencyCheck {
  name: string;
  status: "UP" | "DOWN";
  latencyMs: number;
  detail: string;
}

export interface OperationalSnapshot {
  overallStatus: "READY" | "READY_WITH_ACTIONS" | "NOT_READY";
  generatedAt: string;
  releaseVersion: string;
  environment: string;
  uptimeSeconds: number;
  dependencies: DependencyCheck[];
  traffic: {
    requests: number;
    serverErrors: number;
    errorRate: number;
    averageLatencyMs: number;
    rateLimitedRequests: number;
  };
  capacity: {
    databaseActiveConnections: number;
    databaseIdleConnections: number;
    databaseMaximumConnections: number;
    jvmUsedMemoryMb: number;
    jvmMaximumMemoryMb: number;
    availableProcessors: number;
  };
  workflows: {
    activeAssessmentAttempts: number;
    pendingResultPublications: number;
    pendingQuestionReviews: number;
    pendingAssessmentReviews: number;
    queuedNotifications: number;
    failedNotifications: number;
    overdueReviewItems: number;
  };
  readiness: Array<{
    key: string;
    label: string;
    status: "PASS" | "WARN" | "FAIL";
    detail: string;
  }>;
}
