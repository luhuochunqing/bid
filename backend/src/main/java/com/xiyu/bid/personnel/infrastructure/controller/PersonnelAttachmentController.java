package com.xiyu.bid.personnel.infrastructure.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.exception.InvalidArgumentException;
import com.xiyu.bid.personnel.application.dto.BatchAttachmentUploadResult;
import com.xiyu.bid.personnel.application.service.BatchAttachmentAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 人员证书附件批量操作控制器。
 * 负责批量上传文件并自动关联到对应的证书记录。
 */
@RestController
@RequestMapping("/api/knowledge/personnel")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class PersonnelAttachmentController {

    private final BatchAttachmentAppService batchAttachmentService;

    /**
     * 批量上传证书附件并按文件名自动关联。
     * 文件名须遵循 PER_姓名_工号_序号_证书名.pdf 格式，
     * 服务端将按工号匹配人员、按证书名匹配证书、存储文件并更新 attachmentUrl。
     */
    @PostMapping("/attachments/batch-upload")
    @PreAuthorize("hasAnyAuthority('/bidAdmin', 'bid-TeamLeader', 'bid-Team')")
    @Auditable(action = "UPDATE", entityType = "PersonnelCertificate", description = "批量关联证书附件")
    public ResponseEntity<ApiResponse<BatchAttachmentUploadResult>> batchUpload(
            @RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new InvalidArgumentException("请选择要上传的文件");
        }
        // 单个文件上限 10MB（与单个上传接口一致）
        for (MultipartFile file : files) {
            if (file.getSize() > 10 * 1024 * 1024) {
                throw new InvalidArgumentException("附件不能超过10MB: " + file.getOriginalFilename());
            }
        }

        BatchAttachmentUploadResult result = batchAttachmentService.batchUpload(files);
        return ResponseEntity.ok(ApiResponse.success("批量上传完成", result));
    }
}
