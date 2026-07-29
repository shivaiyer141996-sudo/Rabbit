package com.rabbit.aip.settings;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rabbit.aip.common.exception.DomainException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class GradeBandValidatorTest {

    @Test
    void acceptsCompleteContiguousBands() {
        assertThatCode(() -> GradeBandValidator.validate(List.of(
                band("A", "80.00", "100.00"),
                band("B", "40.00", "79.99"),
                band("F", "0.00", "39.99")
        ))).doesNotThrowAnyException();
    }

    @Test
    void rejectsGapOrOverlap() {
        assertThatThrownBy(() -> GradeBandValidator.validate(List.of(
                band("A", "80.00", "100.00"),
                band("B", "40.00", "78.99"),
                band("F", "0.00", "39.99")
        )))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("without gaps or overlaps");
    }

    private SettingsDtos.GradeBandRequest band(
            String code,
            String min,
            String max
    ) {
        return new SettingsDtos.GradeBandRequest(
                code,
                code,
                new BigDecimal(min),
                new BigDecimal(max)
        );
    }
}
