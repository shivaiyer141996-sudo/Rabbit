import { redirect } from "next/navigation";
import { CommercialConsole } from "@/components/commercial-console";
import { getPortalSession } from "@/lib/server-auth";

export default async function CommercialPage() {
  const session = await getPortalSession();
  if (session.role !== "SUPER_ADMIN" && session.role !== "ORG_ADMIN") {
    redirect("/dashboard");
  }
  return <CommercialConsole role={session.role} />;
}
