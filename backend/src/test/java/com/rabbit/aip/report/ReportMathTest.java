package com.rabbit.aip.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportMathTest {

    @Test
    void calculatesStableAverageAndDistribution() {
        List<BigDecimal> values = List.of(
                new BigDecimal("35"),
                new BigDecimal("55"),
                new BigDecimal("75"),
                new BigDecimal("95")
        );

        assertThat(ReportMath.average(values)).isEqualByComparingTo("65.00");
        assertThat(ReportMath.distribution(values))
                .extracting(ReportDtos.CountValue::value)
                .containsExactly(1L, 1L, 1L, 1L);
    }

    @Test
    void identifiesImprovingAndDecliningTrajectories() {
        assertThat(ReportMath.trajectory(List.of(
                new BigDecimal("45"),
                new BigDecimal("60")
        ))).isEqualTo("IMPROVING");
        assertThat(ReportMath.trajectory(List.of(
                new BigDecimal("70"),
                new BigDecimal("55")
        ))).isEqualTo("DECLINING");
    }
}
