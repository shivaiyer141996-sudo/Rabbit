import type { QuestionType } from "./types";

export interface QuestionDraft {
  stem: string;
  type: QuestionType;
  subject: string;
  topic: string;
  difficulty: string;
  marks: number;
  negativeMarks: number;
  options: Array<{ text: string; correct: boolean }>;
}

export function validateQuestionDraft(draft: QuestionDraft): string[] {
  const errors: string[] = [];
  const options = draft.options.filter((option) => option.text.trim().length > 0);
  const correctCount = options.filter((option) => option.correct).length;

  if (!draft.stem.trim()) errors.push("Question stem cannot be empty.");
  if (!draft.subject.trim() || !draft.topic.trim()) {
    errors.push("Subject and Topic are required fields.");
  }
  if (!draft.difficulty) errors.push("Difficulty level is required.");
  if (options.length < 4) errors.push("At least 4 options are required.");
  if (options.length > 6) errors.push("A maximum of 6 options is allowed.");
  if (draft.type === "SINGLE_CORRECT" && correctCount !== 1) {
    errors.push("Single Correct MCQ requires exactly one correct option.");
  }
  if (draft.type === "MULTIPLE_CORRECT" && correctCount < 2) {
    errors.push("Multiple Correct MCQ requires at least two correct options.");
  }
  if (draft.marks <= 0) errors.push("Marks must be a positive number.");
  if (draft.negativeMarks < 0 || draft.negativeMarks > draft.marks) {
    errors.push("Negative marks must be between zero and the question marks.");
  }

  return errors;
}
