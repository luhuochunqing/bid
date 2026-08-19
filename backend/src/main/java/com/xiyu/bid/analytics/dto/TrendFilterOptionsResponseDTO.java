// Input: Filter options from services
// Output: Structured trend filter options DTO
// Pos: DTO/数据传输对象
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendFilterOptionsResponseDTO {
    private List<AnalyticsDepartmentTreeDTO> department;
    private List<AnalyticsFilterOptionDTO> person;
    private List<AnalyticsFilterOptionDTO> region;
    private List<AnalyticsFilterOptionDTO> customerType;
    private List<AnalyticsFilterOptionDTO> projectType;
    private List<AnalyticsFilterOptionDTO> tenderEntity;
    private List<AnalyticsFilterOptionDTO> competitor;
}
