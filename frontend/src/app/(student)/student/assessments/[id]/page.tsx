import { AssessmentPlayer } from "@/components/assessment-player";
import { assessmentQuestions } from "@/lib/demo-data";

export default function StudentAssessmentPage() {
  return (
    <AssessmentPlayer
      assessmentId="jee-physics-04"
      title="JEE Physics — Kinematics"
      durationMinutes={45}
      questions={assessmentQuestions}
    />
  );
}
