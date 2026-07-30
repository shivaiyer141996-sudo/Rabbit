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
  sections: Array<{
    id: string;
    departmentId?: string;
    departmentName: string;
    name: string;
    active: boolean;
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
  stem: string;
  topicId: string;
  selectedOptionIds: string[];
  correctOptionIds: string[];
  awardedMarks: number;
  maxMarks: number;
  correct: boolean;
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
  timeTakenSeconds: number;
  evaluationVersion: number;
  questions: ResultQuestion[];
}

export type PilotCheckStatus = "NOT_RUN" | "PASS" | "FAIL" | "BLOCKED";

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
