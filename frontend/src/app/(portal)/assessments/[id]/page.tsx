import { AssessmentDetail } from "@/components/assessment-detail";

export default async function AssessmentDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <AssessmentDetail assessmentId={id} />;
}
