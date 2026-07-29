import { PageHeader } from "@/components/page-header";
import { QuestionAuthorForm } from "@/components/question-author-form";

export default function NewQuestionPage() {
  return (
    <div className="page">
      <PageHeader
        eyebrow="Question authoring"
        title="Create a question"
        description="Draft a governed MCQ. Required fields and answer rules are validated before it enters review."
      />
      <QuestionAuthorForm />
    </div>
  );
}
