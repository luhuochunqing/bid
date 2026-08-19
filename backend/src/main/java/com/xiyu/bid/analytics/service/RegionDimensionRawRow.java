// Input: pid.headquartersLocation, t.region, p.id, p.status
// Output: Raw database projection for region dimension
// Pos: Model/数据模型层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.entity.Project;

/**
 * 区域维度原始查询投影（避免在 SQL 中跨多表 COALESCE 触发 MySQL Collation NONE 异常）。
 */
public record RegionDimensionRawRow(
        String pidHeadquartersLocation,
        String tenderRegion,
        Long projectId,
        Project.Status status
) {
}
