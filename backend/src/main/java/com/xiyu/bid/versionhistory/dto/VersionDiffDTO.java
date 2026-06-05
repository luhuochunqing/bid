// Input: 两个版本的比较结果
// Output: 版本差异DTO
// Pos: DTO/数据传输对象层
// 用于返回两个版本之间的差异信息

package com.xiyu.bid.versionhistory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 版本差异DTO
 * 用于返回两个版本之间的比较结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionDiffDTO {

    private Long version1Id;
    private Long version2Id;
    private Integer version1Number;
    private Integer version2Number;
    private String content1;
    private String content2;
    private List<String> differences;
}
