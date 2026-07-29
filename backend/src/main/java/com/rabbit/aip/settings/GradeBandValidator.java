package com.rabbit.aip.settings;

import com.rabbit.aip.common.exception.DomainException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public final class GradeBandValidator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal STEP = BigDecimal.valueOf(0.01);

    private GradeBandValidator() {
    }

    public static void validate(List<SettingsDtos.GradeBandRequest> bands) {
        if (bands.isEmpty()) {
            throw DomainException.badRequest(
                    "GRADE_BANDS_REQUIRED",
                    "At least one grade band is required."
            );
        }
        List<SettingsDtos.GradeBandRequest> ordered = bands.stream()
                .sorted(Comparator.comparing(SettingsDtos.GradeBandRequest::minPercentage))
                .toList();
        if (ordered.get(0).minPercentage().compareTo(BigDecimal.ZERO) != 0
                || ordered.get(ordered.size() - 1).maxPercentage().compareTo(HUNDRED) != 0) {
            invalid();
        }
        for (int index = 0; index < ordered.size(); index++) {
            SettingsDtos.GradeBandRequest band = ordered.get(index);
            if (band.minPercentage().scale() > 2
                    || band.maxPercentage().scale() > 2
                    || band.minPercentage().compareTo(BigDecimal.ZERO) < 0
                    || band.maxPercentage().compareTo(HUNDRED) > 0
                    || band.minPercentage().compareTo(band.maxPercentage()) > 0) {
                invalid();
            }
            if (index > 0) {
                BigDecimal expected = ordered.get(index - 1)
                        .maxPercentage()
                        .add(STEP);
                if (band.minPercentage().compareTo(expected) != 0) {
                    invalid();
                }
            }
        }
        long uniqueCodes = bands.stream()
                .map(item -> item.code().trim().toUpperCase())
                .distinct()
                .count();
        if (uniqueCodes != bands.size()) {
            throw DomainException.badRequest(
                    "GRADE_CODE_DUPLICATE",
                    "Grade codes must be unique."
            );
        }
    }

    private static void invalid() {
        throw DomainException.badRequest(
                "GRADE_BANDS_INVALID",
                "Grade bands must cover 0–100 without gaps or overlaps."
        );
    }
}
