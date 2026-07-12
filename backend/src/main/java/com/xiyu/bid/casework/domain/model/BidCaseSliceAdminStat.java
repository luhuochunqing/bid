package com.xiyu.bid.casework.domain.model;

/**
 * 案例切片管理端统计（不可变值对象 / 前端 DTO）。
 *
 * @param total            切片总数
 * @param withEmbedding    已生成 embedding 的切片数
 * @param withoutEmbedding 未生成 embedding 的切片数
 */
public record BidCaseSliceAdminStat(
        long total,
        long withEmbedding,
        long withoutEmbedding
) {
}
