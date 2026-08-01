import { AssessmentInstructions } from "@/components/assessment-instructions";

export default async function AssessmentInstructionsPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <AssessmentInstructions assessmentId={id} />;
}
