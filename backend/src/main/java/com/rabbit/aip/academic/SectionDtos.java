package com.rabbit.aip.academic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class SectionDtos {
    private SectionDtos() {
    }

    public record SectionRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull UUID programmeId,
            @NotNull UUID academicYearId,
            @NotNull UUID batchId
    ) {
    }

    public record SectionResponse(
            UUID id,
            String name,
            UUID programmeId,
            String programmeName,
            UUID academicYearId,
            String academicYearName,
            UUID batchId,
            String batchName,
            long studentCount,
            long teacherCount,
            long assessmentCount,
            SectionStatus status,
            Instant archivedAt
    ) {
    }
}
