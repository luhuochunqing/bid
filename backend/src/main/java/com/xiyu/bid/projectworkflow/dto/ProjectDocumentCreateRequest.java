package com.xiyu.bid.projectworkflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDocumentCreateRequest {

    @NotBlank(message = "文档名称不能为空")
    private String name;

    private String size;

    private String fileType;

    private String documentCategory;

    private String linkedEntityType;

    private Long linkedEntityId;

    private String fileUrl;

    private Long uploaderId;

    private String uploaderName;

    // OBS 直传 JSON 路径透传浏览器 File API 的字节数，供 archive_file.file_size 使用（修复档案详情"大小 0B"问题）
    // multipart 路径不使用此字段，直接用 MultipartFile.getSize() 真实字节数
    private Long fileSizeBytes;
}
