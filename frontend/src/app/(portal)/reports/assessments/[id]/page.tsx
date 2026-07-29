import { AssessmentReportDetail } from "@/components/assessment-report-detail";

export default async function AssessmentReportPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <AssessmentReportDetail assessmentId={id} />;
}
