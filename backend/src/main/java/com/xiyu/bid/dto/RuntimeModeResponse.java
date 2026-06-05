package com.xiyu.bid.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class RuntimeModeResponse {
    private final String modeCode;
    private final String modeLabel;
    private final String database;
    private final boolean demoFusionEnabled;
    private final List<String> activeProfiles;
}
