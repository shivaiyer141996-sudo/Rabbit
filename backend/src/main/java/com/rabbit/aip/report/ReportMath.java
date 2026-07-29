package com.rabbit.aip.report;

import com.rabbit.aip.report.ReportDtos.CountValue;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class ReportMath {

    private ReportMath() {
    }

    public static BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) return BigDecimal.ZERO.setScale(2);
        return values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal percentage(long numerator, long denominator) {
        if (denominator == 0) return BigDecimal.ZERO.setScale(2);
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    public static List<CountValue> distribution(List<BigDecimal> values) {
        return List.of(
                new CountValue("0–39", count(values, 0, 40)),
                new CountValue("40–59", count(values, 40, 60)),
                new CountValue("60–79", count(values, 60, 80)),
                new CountValue("80–100", count(values, 80, 101))
        );
    }

    public static String trajectory(List<BigDecimal> orderedValues) {
        if (orderedValues.size() < 2) return "STABLE";
        BigDecimal delta = orderedValues.get(orderedValues.size() - 1)
                .subtract(orderedValues.get(orderedValues.size() - 2));
        if (delta.compareTo(BigDecimal.valueOf(5)) > 0) return "IMPROVING";
        if (delta.compareTo(BigDecimal.valueOf(-5)) < 0) return "DECLINING";
        return "STABLE";
    }

    private static long count(List<BigDecimal> values, int minimum, int exclusiveMaximum) {
        return values.stream()
                .filter(value -> value.compareTo(BigDecimal.valueOf(minimum)) >= 0)
                .filter(value -> value.compareTo(
                        BigDecimal.valueOf(exclusiveMaximum)
                ) < 0)
                .count();
    }
}
