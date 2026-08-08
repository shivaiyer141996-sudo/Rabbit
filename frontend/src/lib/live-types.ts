import type {
  Assessment,
  AssessmentQuestion,
  BloomLevel,
  Difficulty,
  Question,
  QuestionStatus,
  QuestionType,
  UserRole,
} from "./types";

export interface MeProfile {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  organisationId: string;
  organisationCode: string;
  organisationName: string;
  timezone: string;
  role: UserRole;
}

export interface OrganisationSummary {
  id: string;
  code: string;
  name: string;
  timezone: string;
  status: "INVITED" | "ACTIVE" | "SUSPENDED" | "ARCHIVED";
}

export interface AcademicCatalog {
  academicYears: Array<{
    id: string;
    name: string;
    startDate: string;
    endDate: string;
    active: boolean;
  }>;
  departments: Array<{
    id: string;
    name: string;
    active: boolean;
    sectionCount: number;
  }>;
  programmes: Array<{
    id: string;
    code: string;
    name: string;
    active: boolean;
  }>;
  batches: Array<{
    id: string;
    programmeId: string;
    academicYearId: string;
    name: string;
    active: boolean;
  }>;
  sections: Array<{
    id: string;
    departmentId?: string;
    departmentName: string;
    name: string;
    active: boolean;
    programmeId: string;
    programmeName: string;
    academicYearId: string;
    academicYearName: string;
    batchId: string;
    batchName: string;
    status: "ACTIVE" | "INACTIVE" | "ARCHIVED";
  }>;
  subjects: Array<{
    id: string;
    code: string;
    name: string;
    active: boolean;
  }>;
  topics: Array<{
    id: string;
    subjectId: string;
    name: string;
    active: boolean;
  }>;
}

export interface ApiQuestion {
  id: string;
  code: string;
  stem: string;
  type: QuestionType;
  subjectId: string;
  topicId: string;
  subTopic?: string;
  difficulty: Difficulty;
  bloomLevel: BloomLevel;
  marks: number;
  negativeMarks: number;
  status: QuestionStatus;
  version: number;
  explanation?: string;
  language: string;
  authorUserId: string;
  reviewedBy?: string;
  approvedBy?: string;
  options: Array<{
    id: string;
    label: string;
    text: string;
    correct: boolean;
    sortOrder: number;
  }>;
  createdAt: string;
  updatedAt: string;
}

export interface ApiAssessment {
  id: string;
  title: string;
  code: string;
  type:
    | "PRACTICE_ASSESSMENT"
    | "CLASS_TEST"
    | "UNIT_TEST"
    | "CHAPTER_TEST"
    | "MID_TERM_EXAMINATION"
    | "FINAL_EXAMINATION"
    | "MOCK_TEST";
  subjectId: string;
  subjectIds: string[];
  durationMinutes: number;
  status: Assessment["status"];
  totalMarks: number;
  questionCount: number;
  shuffleQuestions: boolean;
  shuffleOptions: boolean;
  partialMarking: boolean;
  attemptsAllowed: number;
  startAt?: string;
  endAt?: string;
  questionIds: string[];
  eligibleSectionIds: string[];
  createdAt: string;
  updatedAt: string;
}

export interface UserSummary {
  userId: string;
  membershipId: string;
  email: string;
  firstName: string;
  lastName: string;
  role: UserRole;
  status: "INVITED" | "ACTIVE" | "SUSPENDED" | "ARCHIVED";
  sectionId?: string;
}

export interface AcademicSectionSummary {
  id: string;
  name: string;
  programmeId: string;
  programmeName: string;
  academicYearId: string;
  academicYearName: string;
  batchId: string;
  batchName: string;
  studentCount: number;
  teacherCount: number;
  assessmentCount: number;
  status: "ACTIVE" | "INACTIVE" | "ARCHIVED";
  archivedAt?: string;
}

export interface InvitationIssue {
  user: UserSummary;
  activationUrl: string;
  expiresAt: string;
}

export type CommercialPlan = "BASIC" | "PRO" | "LEGEND";
export type CommercialEntitlement =
  | "ASSESSMENT_DELIVERY"
  | "STUDENT_EVALUATION"
  | "INSTITUTION_ANALYTICS"
  | "TEACHER_ANALYTICS"
  | "REPORT_EXPORTS";
export type SubscriptionStatus = "TRIALING" | "ACTIVE" | "EXPIRED" | "SUSPENDED";

export interface CommercialAccess {
  enforcementEnabled: boolean;
  plan?: CommercialPlan;
  status?: SubscriptionStatus;
  studentLimit?: number;
  accessEndsAt?: string;
  entitlements: CommercialEntitlement[];
}

export interface CommercialOverview {
  enforcementEnabled: boolean;
  m5_6ActivationEvidenceAccepted: boolean;
  trialDays: number;
  serverNow: string;
  activeAndInvitedStudents: number;
  availableStudentSlots: number;
  effectiveEntitlements: CommercialEntitlement[];
  subscription?: {
    id: string;
    plan: CommercialPlan;
    studentLimit: number;
    monthlyPricePaise: number;
    status: SubscriptionStatus;
    trialStartsAt?: string;
    trialEndsAt?: string;
    periodStartsAt?: string;
    periodEndsAt?: string;
    pendingPlan?: CommercialPlan;
    pendingStudentLimit?: number;
    pendingMonthlyPricePaise?: number;
    pendingPeriodStartsAt?: string;
    pendingPeriodEndsAt?: string;
    note?: string;
    rowVersion: number;
    entitlements: CommercialEntitlement[];
  };
  catalog: Array<{
    code: CommercialPlan;
    label: string;
    description: string;
    prices: Array<{ studentLimit: number; monthlyPricePaise: number }>;
    entitlements: CommercialEntitlement[];
  }>;
  subscriptionEvents: Array<{
    id: string;
    eventType: string;
    beforeValue?: string;
    afterValue: string;
    actorUserId: string;
    occurredAt: string;
  }>;
  invoices: Array<{
    id: string;
    invoiceNumber: string;
    plan: CommercialPlan;
    studentLimit: number;
    periodStartsAt: string;
    periodEndsAt: string;
    subtotalPaise: number;
    taxPaise: number;
    totalPaise: number;
    status: "ISSUED" | "PAID" | "VOID";
    issuedAt: string;
    dueAt: string;
    paidAt?: string;
    note?: string;
  }>;
  payments: Array<{
    id: string;
    invoiceId: string;
    paymentReference: string;
    paymentMethod: "BANK_TRANSFER" | "UPI" | "CHEQUE" | "CASH" | "OTHER";
    amountPaise: number;
    status: "RECORDED";
    paidAt: string;
    note?: string;
  }>;
  receipts: Array<{
    id: string;
    paymentId: string;
    invoiceId: string;
    receiptNumber: string;
    amountPaise: number;
    issuedAt: string;
  }>;
  supportCases: Array<{
    id: string;
    caseNumber: string;
    severity: "S1" | "S2" | "S3" | "S4";
    category: "ACCESS" | "ASSESSMENT" | "REPORTING" | "BILLING" | "DATA" | "OTHER";
    status: "OPEN" | "IN_PROGRESS" | "WAITING_FOR_INSTITUTION" | "RESOLVED" | "CLOSED";
    summary: string;
    description: string;
    requesterUserId: string;
    assignedTo?: string;
    responseDueAt: string;
    resolvedAt?: string;
    resolution?: string;
    createdAt: string;
    updatedAt: string;
  }>;
}

export interface StudentAssessmentSummary {
  id: string;
  title: string;
  code: string;
  type: ApiAssessment["type"];
  durationMinutes: number;
  questionCount: number;
  totalMarks: number;
  startAt: string;
  endAt: string;
  status: "AVAILABLE_NOW" | "UPCOMING" | "COMPLETED" | "MISSED_CLOSED";
  remainingDays: number;
}

export interface StudentAssessmentInstructions extends StudentAssessmentSummary {
  serverNow: string;
  attemptsAllowed: number;
  attemptsUsed: number;
  shuffleQuestions: boolean;
  shuffleOptions: boolean;
  partialMarking: boolean;
  inProgressAttemptId?: string;
  inProgressExpiresAt?: string;
}

export interface AttemptHistoryItem {
  attemptId: string;
  assessmentId: string;
  assessmentTitle: string;
  assessmentType: ApiAssessment["type"];
  status: "IN_PROGRESS" | "SUBMITTED" | "AUTO_SUBMITTED";
  publicationStatus: "PENDING_PUBLICATION" | "PUBLISHED";
  startedAt: string;
  expiresAt: string;
  submittedAt?: string;
  answered: number;
  questionCount: number;
  score?: number;
  maxScore?: number;
  percentage?: number;
  grade?: string;
  evaluationVersion: number;
}

export interface AttemptView {
  attemptId: string;
  assessmentId: string;
  title: string;
  startedAt: string;
  expiresAt: string;
  questions: AssessmentQuestion[];
  responses: Array<{
    questionId: string;
    selectedOptionIds: string[];
    flagged: boolean;
    timeSpentSeconds: number;
  }>;
}

export interface ResultQuestion {
  questionId: string;
  questionCode: string;
  stem: string;
  subjectId: string;
  subjectName: string;
  topicId: string;
  topicName: string;
  chapter?: string;
  difficulty: Difficulty;
  bloomLevel: BloomLevel;
  selectedOptionIds: string[];
  correctOptionIds: string[];
  options: Array<{
    optionId: string;
    label: string;
    text: string;
    selected: boolean;
    correct: boolean;
  }>;
  awardedMarks: number;
  maxMarks: number;
  correct: boolean;
  answerStatus: "CORRECT" | "INCORRECT" | "UNANSWERED" | "PARTIALLY_CORRECT";
  timeSpentSeconds: number;
  explanation?: string;
}

export interface ResultView {
  attemptId: string;
  assessmentId: string;
  assessmentTitle: string;
  status: "IN_PROGRESS" | "SUBMITTED" | "AUTO_SUBMITTED" | "EVALUATED";
  publicationStatus: "PENDING_PUBLICATION" | "PUBLISHED";
  score?: number;
  maxScore?: number;
  percentage?: number;
  grade?: string;
  submittedAt: string;
  answered: number;
  questionCount: number;
  correctAnswers: number;
  wrongAnswers: number;
  unansweredAnswers: number;
  rank?: number;
  topperScore?: number;
  timeTakenSeconds: number;
  evaluationVersion: number;
  questions: ResultQuestion[];
}

export type PilotCheckStatus = "NOT_RUN" | "PASS" | "FAIL" | "BLOCKED";
export type PilotDecisionOutcome = "GO" | "CONDITIONAL_RETEST" | "NO_GO";

export interface PilotDecision {
  id: string;
  outcome: PilotDecisionOutcome;
  releaseVersion: string;
  releaseCommit: string;
  institutionName: string;
  authorisedBy: string;
  authoriserTitle: string;
  uatLead: string;
  technicalOwner: string;
  supportContact: string;
  monitoringOwner: string;
  backupRestoreOwner: string;
  incidentOwner: string;
  rollbackOwner: string;
  dataPrivacyOwner: string;
  handoverRecipient: string;
  evidenceReference: string;
  evidenceSha256: string;
  knownIssueCount: number;
  knownIssuesReference?: string;
  decisionReason: string;
  retestBy?: string;
  localDataConfirmed: boolean;
  localOnlyConfirmed: boolean;
  ownershipAccepted: boolean;
  scopeFreezeAccepted: boolean;
  mandatoryChecksPassed: boolean;
  passedChecks: number;
  failedChecks: number;
  blockedChecks: number;
  notRunChecks: number;
  decidedAt: string;
  decidedByUserId: string;
}

export interface PilotReadiness {
  totalChecks: number;
  passedChecks: number;
  failedChecks: number;
  blockedChecks: number;
  notRunChecks: number;
  mandatoryChecksPassed: boolean;
  signedOff: boolean;
  checks: Array<{
    id: string;
    key: string;
    category: string;
    label: string;
    mandatory: boolean;
    status: PilotCheckStatus;
    testerName?: string;
    evidenceUrl?: string;
    defectId?: string;
    notes?: string;
    executedAt?: string;
    updatedBy?: string;
  }>;
  signOff?: {
    id: string;
    releaseVersion: string;
    authorisedBy: string;
    authoriserTitle: string;
    supportContact: string;
    rollbackOwner: string;
    notes?: string;
    signedAt: string;
    signedByUserId: string;
  };
  latestDecision?: PilotDecision;
  decisions: PilotDecision[];
}

export function subjectName(catalog: AcademicCatalog, subjectId: string) {
  return catalog.subjects.find((item) => item.id === subjectId)?.name ?? "Unknown subject";
}

export function topicName(catalog: AcademicCatalog, topicId: string) {
  return catalog.topics.find((item) => item.id === topicId)?.name ?? "Unknown topic";
}

export function mapQuestion(
  question: ApiQuestion,
  catalog: AcademicCatalog,
): Question {
  return {
    id: question.id,
    code: question.code,
    stem: question.stem,
    type: question.type,
    subject: subjectName(catalog, question.subjectId),
    topic: topicName(catalog, question.topicId),
    subTopic: question.subTopic,
    difficulty: question.difficulty,
    bloomLevel: question.bloomLevel,
    marks: Number(question.marks),
    negativeMarks: Number(question.negativeMarks),
    status: question.status,
    version: question.version,
    options: question.options
      .toSorted((left, right) => left.sortOrder - right.sortOrder)
      .map((option) => ({
        id: option.id,
        label: option.label,
        text: option.text,
        correct: option.correct,
      })),
    explanation: question.explanation,
    author: `User ${question.authorUserId.slice(0, 8)}`,
    updatedAt: new Date(question.updatedAt).toLocaleString(),
  };
}

export function mapAssessment(
  assessment: ApiAssessment,
  catalog: AcademicCatalog,
): Assessment {
  return {
    id: assessment.id,
    title: assessment.title,
    code: assessment.code,
    type: assessment.type.replaceAll("_", " "),
    subject: subjectName(catalog, assessment.subjectId),
    durationMinutes: assessment.durationMinutes,
    questionCount: assessment.questionCount,
    totalMarks: Number(assessment.totalMarks),
    status: assessment.status,
    startAt: assessment.startAt
      ? new Date(assessment.startAt).toLocaleString()
      : undefined,
    endAt: assessment.endAt
      ? new Date(assessment.endAt).toLocaleString()
      : undefined,
  };
}

export function initials(firstName: string, lastName: string) {
  return `${firstName.at(0) ?? ""}${lastName.at(0) ?? ""}`.toUpperCase();
}
