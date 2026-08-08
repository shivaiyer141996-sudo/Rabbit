package com.rabbit.aip.organisation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrganisationBrandingRulesTest {
    @Test
    void detectsPngJpegAndWebpBySignatureRatherThanFilename() {
        assertThat(OrganisationBrandingService.detectType(new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        })).isEqualTo("image/png");
        assertThat(OrganisationBrandingService.detectType(new byte[] {
                (byte) 0xff, (byte) 0xd8, (byte) 0xff
        })).isEqualTo("image/jpeg");
        assertThat(OrganisationBrandingService.detectType(
                "RIFF1234WEBP".getBytes(java.nio.charset.StandardCharsets.US_ASCII)
        )).isEqualTo("image/webp");
        assertThat(OrganisationBrandingService.detectType("fake".getBytes()))
                .isEqualTo("application/octet-stream");
    }
}
