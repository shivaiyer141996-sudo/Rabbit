import { redirect } from "next/navigation";
import { getPortalSession } from "@/lib/server-auth";

export default async function StudentLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const session = await getPortalSession();
  if (!session.accessToken && process.env.NODE_ENV === "production") {
    redirect("/login");
  }
  return children;
}
