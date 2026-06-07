package com.xiyu.bid.marketinsight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 预测提示 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForecastTipDTO {

    private String text;

    private String color;
}
