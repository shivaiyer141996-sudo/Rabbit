package com.rabbit.aip.feature;

public enum FeatureFlagKey {
    PDF_EXPORTS(
            "PDF report exports",
            "Allow styled PDF downloads for governed assessment reports.",
            true
    ),
    EXCEL_EXPORTS(
            "Excel report exports",
            "Allow native XLSX downloads for governed assessment reports.",
            true
    ),
    OPERATIONS_CONSOLE(
            "Operations console",
            "Expose tenant-scoped GA health, traffic, and workflow indicators.",
            true
    ),
    PILOT_MODE(
            "Controlled pilot mode",
            "Show pilot-readiness controls while Release 1.0 is introduced.",
            true
    ),
    BULK_IMPORTS(
            "Bulk imports",
            "Enable guarded question and user bulk-import workflows.",
            false
    ),
    EXTERNAL_DELIVERY(
            "External notification delivery",
            "Enable provider-backed email and SMS delivery adapters.",
            false
    );

    private final String label;
    private final String description;
    private final boolean enabledByDefault;

    FeatureFlagKey(String label, String description, boolean enabledByDefault) {
        this.label = label;
        this.description = description;
        this.enabledByDefault = enabledByDefault;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public boolean enabledByDefault() {
        return enabledByDefault;
    }
}
