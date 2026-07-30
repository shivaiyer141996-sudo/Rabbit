import { redirect } from "next/navigation";
import { getPortalSession } from "@/lib/server-auth";

export default async function StudentLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const session = await getPortalSession();
  if (!session.accessToken) {
    redirect("/login");
  }
  if (session.role !== "STUDENT") redirect("/dashboard");
  return children;
}
