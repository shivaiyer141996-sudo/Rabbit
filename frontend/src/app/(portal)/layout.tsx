import { redirect } from "next/navigation";
import { AppShell } from "@/components/app-shell";
import { getPortalSession } from "@/lib/server-auth";

export default async function PortalLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const session = await getPortalSession();
  if (!session.accessToken) {
    redirect("/login");
  }
  return <AppShell role={session.role}>{children}</AppShell>;
}
