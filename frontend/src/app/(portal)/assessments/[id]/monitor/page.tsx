import { AssessmentMonitor } from "@/components/assessment-monitor";

export default async function AssessmentMonitorPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <AssessmentMonitor assessmentId={id} />;
}
