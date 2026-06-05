package com.xiyu.bid.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardLayoutDTO {
    private String code;
    private String name;
    private String layoutJson;
}
