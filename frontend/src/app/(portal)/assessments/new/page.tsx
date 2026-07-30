import { PageHeader } from "@/components/page-header";
import { AssessmentAuthorForm } from "@/components/assessment-author-form";

export default function NewAssessmentPage() {
  return (
    <div className="page">
      <PageHeader
        eyebrow="Assessment creation · Live"
        title="Create an assessment"
        description="Only approved questions from the live bank are available. The draft is persisted before entering governance."
      />
      <AssessmentAuthorForm />
    </div>
  );
}
