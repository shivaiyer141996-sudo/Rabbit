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
  submittedAt: string;
  score: number;
  maxScore: number;
  percentage: number;
  grade: string;
  trajectory: "IMPROVING" | "STABLE" | "DECLINING";
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
