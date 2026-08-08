import { redirect } from "next/navigation";
import { PlatformConsole } from "@/components/platform-console";
import { getPortalSession } from "@/lib/server-auth";

export default async function PlatformPage() {
  const session = await getPortalSession();
  if (session.role !== "SUPER_ADMIN") redirect("/dashboard");
  return <PlatformConsole />;
}
