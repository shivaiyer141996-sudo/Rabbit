import { StudentResultView } from "@/components/student-result-view";

export default async function ResultPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <StudentResultView attemptId={id} />;
}
