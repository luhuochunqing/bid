// Input: 项目结果确认回调 4.2 契约的请求体
// Output: 回传 CRM 的项目结果确认载荷 DTO
// Pos: crm/infrastructure/dto/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.crm.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 项目结果确认回调请求体（4.2 契约）。
 * <p>本平台在项目「结果确认」阶段登记完成后，主动回调 CRM 系统的请求体。
 * <p>顶层字段：
 * <ul>
 *   <li>{@code tenderId} — 投标系统内部标讯 ID</li>
 *   <li>{@code projectId} — 投标系统内部项目 ID</li>
 *   <li>{@code sourceId} — 来源系统的数据唯一 ID（与标讯推送接口的 sourceId 取值一致）</li>
 *   <li>{@code bidResult} — 投标结果：WON/LOST/FAILED/ABANDONED</li>
 *   <li>{@code evidenceFiles} — 凭证文件列表，每个结果类型至少 1 个</li>
 *   <li>{@code competitors} — 竞争对手情况表，仅 WON/LOST 时有值，FAILED/ABANDONED 时为空数组</li>
 *   <li>{@code systemName} — 调用方系统名称，固定值"西域数智化投标管理平台"</li>
 *   <li>{@code operatorName} — 操作人姓名</li>
 *   <li>{@code operatorEmployeeId} — 操作人工号</li>
 *   <li>{@code operatedAt} — 操作时间，ISO 8601 格式（yyyy-MM-ddTHH:mm:ss+08:00）</li>
 * </ul>
 */
public record ProjectResultCallbackPayload(
        @JsonProperty("tenderId") Long tenderId,
        @JsonProperty("projectId") Long projectId,
        @JsonProperty("sourceId") String sourceId,
        @JsonProperty("bidResult") String bidResult,
        @JsonProperty("evidenceFiles") List<EvidenceFile> evidenceFiles,
        @JsonProperty("competitors") List<CompetitorInfo> competitors,
        @JsonProperty("systemName") String systemName,
        @JsonProperty("operatorName") String operatorName,
        @JsonProperty("operatorEmployeeId") String operatorEmployeeId,
        @JsonProperty("operatedAt") String operatedAt
) {}
