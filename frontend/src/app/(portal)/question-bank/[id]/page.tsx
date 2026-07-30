import { QuestionDetail } from "@/components/question-detail";

export default async function QuestionDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <QuestionDetail questionId={id} />;
}
