package com.rabbit.aip.commercial;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class CommercialTypes {

    private CommercialTypes() {
    }

    public enum PlanCode {
        BASIC("Basic", "Assessment creation, delivery, scoring, and core administration.", 1),
        PRO("Pro", "Basic plus detailed student evaluation and progress reporting.", 2),
        LEGEND("Legend", "All Release 1.0 modules, teacher analytics, and governed exports.", 3);

        private final String label;
        private final String description;
        private final int rank;

        PlanCode(String label, String description, int rank) {
            this.label = label;
            this.description = description;
            this.rank = rank;
        }

        public String label() { return label; }
        public String description() { return description; }
        public int rank() { return rank; }
    }

    public enum Entitlement {
        ASSESSMENT_DELIVERY,
        STUDENT_EVALUATION,
        INSTITUTION_ANALYTICS,
        TEACHER_ANALYTICS,
        REPORT_EXPORTS
    }

    public enum SubscriptionStatus {
        TRIALING,
        ACTIVE,
        EXPIRED,
        SUSPENDED
    }

    public enum SubscriptionEventType {
        TRIAL_STARTED,
        TRIAL_EXPIRED,
        PLAN_ACTIVATED,
        PLAN_CHANGE_SCHEDULED,
        PLAN_CHANGE_APPLIED,
        SUBSCRIPTION_EXPIRED,
        SUBSCRIPTION_SUSPENDED,
        SUBSCRIPTION_RESTORED
    }

    public enum InvoiceStatus { ISSUED, PAID, VOID }
    public enum PaymentMethod { BANK_TRANSFER, UPI, CHEQUE, CASH, OTHER }
    public enum PaymentStatus { RECORDED }
    public enum SupportSeverity { S1, S2, S3, S4 }
    public enum SupportCategory { ACCESS, ASSESSMENT, REPORTING, BILLING, DATA, OTHER }
    public enum SupportStatus { OPEN, IN_PROGRESS, WAITING_FOR_INSTITUTION, RESOLVED, CLOSED }

    public static final int TRIAL_DAYS = 20;
    public static final List<Integer> STUDENT_LIMITS = List.of(50, 150, 500);

    public static int studentLimitFor(int declaredStudents) {
        if (declaredStudents < 1 || declaredStudents > 500) {
            throw new IllegalArgumentException("Declared students must be between 1 and 500.");
        }
        return declaredStudents <= 50 ? 50 : declaredStudents <= 150 ? 150 : 500;
    }

    public static long monthlyPricePaise(PlanCode plan, int studentLimit) {
        return switch (plan) {
            case BASIC -> switch (studentLimit) {
                case 50 -> 59_900L;
                case 150 -> 99_900L;
                case 500 -> 149_900L;
                default -> throw invalidLimit(studentLimit);
            };
            case PRO -> switch (studentLimit) {
                case 50 -> 89_900L;
                case 150 -> 139_900L;
                case 500 -> 189_900L;
                default -> throw invalidLimit(studentLimit);
            };
            case LEGEND -> switch (studentLimit) {
                case 50 -> 149_900L;
                case 150 -> 199_900L;
                case 500 -> 249_900L;
                default -> throw invalidLimit(studentLimit);
            };
        };
    }

    public static Set<Entitlement> entitlements(PlanCode plan) {
        return switch (plan) {
            case BASIC -> EnumSet.of(Entitlement.ASSESSMENT_DELIVERY);
            case PRO -> EnumSet.of(
                    Entitlement.ASSESSMENT_DELIVERY,
                    Entitlement.STUDENT_EVALUATION
            );
            case LEGEND -> EnumSet.allOf(Entitlement.class);
        };
    }

    public static Duration firstResponseTarget(SupportSeverity severity) {
        return switch (severity) {
            case S1 -> Duration.ofHours(1);
            case S2 -> Duration.ofHours(4);
            case S3 -> Duration.ofDays(1);
            case S4 -> Duration.ofDays(3);
        };
    }

    private static IllegalArgumentException invalidLimit(int studentLimit) {
        return new IllegalArgumentException(
                "Student limit must be one of 50, 150, or 500; received " + studentLimit + "."
        );
    }
}
