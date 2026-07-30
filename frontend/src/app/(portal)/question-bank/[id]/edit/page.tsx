import { PageHeader } from "@/components/page-header";
import { QuestionAuthorForm } from "@/components/question-author-form";

export default async function EditQuestionPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return (
    <div className="page">
      <PageHeader
        eyebrow="Question authoring"
        title="Edit draft question"
        description="Changes are persisted immediately when you save this draft."
      />
      <QuestionAuthorForm questionId={id} />
    </div>
  );
}
