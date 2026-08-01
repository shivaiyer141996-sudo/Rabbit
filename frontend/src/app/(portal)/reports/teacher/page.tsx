import { redirect } from "next/navigation";
import { TeacherAnalyticsReportView } from "@/components/teacher-analytics-report";
import { getPortalSession } from "@/lib/server-auth";

export default async function TeacherReportsPage() {
  const session = await getPortalSession();
  if (!["SUPER_ADMIN", "ORG_ADMIN", "ACADEMIC_HEAD", "FACULTY"].includes(session.role)) {
    redirect("/dashboard");
  }
  return <TeacherAnalyticsReportView />;
}
