// Input: 项目结果确认回调 4.2 契约的凭证文件信息
// Output: 回传 CRM 的凭证文件 DTO
// Pos: crm/infrastructure/dto/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.crm.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 项目结果确认回调的凭证文件（4.2 契约 FileInfo）。
 * <p>对应接口文档 §4.2 evidenceFiles 数组元素。
 */
public record EvidenceFile(
        @JsonProperty("fileName") String fileName,
        @JsonProperty("fileUrl") String fileUrl,
        @JsonProperty("fileSize") Long fileSize
) {}
