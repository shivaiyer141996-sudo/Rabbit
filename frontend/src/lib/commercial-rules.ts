import type {
  CommercialOverview,
  CommercialPlan,
  SubscriptionStatus,
} from "./live-types";

export function formatInrFromPaise(paise: number) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: paise % 100 === 0 ? 0 : 2,
  }).format(paise / 100);
}

export function planPrice(
  catalog: CommercialOverview["catalog"],
  plan: CommercialPlan,
  studentLimit: number,
) {
  return catalog
    .find((item) => item.code === plan)
    ?.prices.find((price) => price.studentLimit === studentLimit)
    ?.monthlyPricePaise;
}

export function accessLabel(status?: SubscriptionStatus) {
  switch (status) {
    case "TRIAL":
      return "Free trial";
    case "ACTIVE":
      return "Paid and active";
    case "EXPIRED":
      return "Expired — read-only access";
    case "TRIAL_EXPIRED":
      return "Trial expired — read-only access";
    case "GRACE_PERIOD":
      return "Grace Period";
    case "SUSPENDED":
      return "Suspended — read-only access";
    case "CANCELLED":
      return "Cancelled — read-only access";
    default:
      return "Not started";
  }
}

export function wholeDaysRemaining(endAt: string | undefined, now: string) {
  if (!endAt) return 0;
  return Math.max(
    0,
    Math.ceil((new Date(endAt).getTime() - new Date(now).getTime()) / 86_400_000),
  );
}
