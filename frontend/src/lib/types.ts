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
  status: "DRAFT" | "PUBLISHED" | "SCHEDULED" | "COMPLETED";
  startAt?: string;
  endAt?: string;
}
