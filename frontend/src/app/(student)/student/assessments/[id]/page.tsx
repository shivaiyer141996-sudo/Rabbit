import { AssessmentPlayer } from "@/components/assessment-player";

export default async function StudentAssessmentPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <AssessmentPlayer assessmentId={id} />;
}
