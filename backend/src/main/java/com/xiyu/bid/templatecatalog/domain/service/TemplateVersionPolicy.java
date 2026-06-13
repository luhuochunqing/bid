package com.xiyu.bid.templatecatalog.domain.service;


import java.math.BigDecimal;
import java.math.RoundingMode;

public class TemplateVersionPolicy {

    private static final String NUMERIC_VERSION_PATTERN = "\\d+(\\.\\d+)?";

    public String initialVersion() {
        return "1.0";
    }

    public String nextVersion(String currentVersion) {
        if (currentVersion == null || currentVersion.isBlank()) {
            return initialVersion();
        }
        if (!currentVersion.matches(NUMERIC_VERSION_PATTERN)) {
            return currentVersion + ".1";
        }
        return new BigDecimal(currentVersion)
                .add(BigDecimal.valueOf(0.1))
                .setScale(1, RoundingMode.HALF_UP)
                .toPlainString();
    }
}
