package com.xiyu.bid.warehouse.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.warehouse.domain.WarehouseActionType;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentType;
import com.xiyu.bid.warehouse.dto.WarehouseAttachmentDTO;
import com.xiyu.bid.warehouse.file.WarehouseFileService;
import com.xiyu.bid.warehouse.infrastructure.WarehouseAttachmentEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseAttachmentRepository;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseRepository;
import com.xiyu.bid.warehouse.service.WarehouseLogService;
import com.xiyu.bid.warehouse.service.WarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 仓库附件子控制器 — 上传/列表/删除。从 WarehouseController 拆出以控行数。
 */
@RestController
@RequestMapping("/api/knowledge/warehouses/{id}/attachments")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class WarehouseAttachmentController {

    private static final String PERM = RoleProfileCatalog.WAREHOUSE_MANAGE_PERMISSION;

    private final WarehouseRepository repo;
    private final WarehouseAttachmentRepository attachmentRepo;
    private final WarehouseFileService fileService;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseLogService warehouseLogService;
    private final UserResolver userResolver;

    @PostMapping
    @PreAuthorize("hasAuthority('" + PERM + "')")
    @Auditable(action = "CREATE", entityType = "WarehouseAttachment", description = "上传仓库附件")
    public ResponseEntity<ApiResponse<WarehouseAttachmentDTO>> upload(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") WarehouseAttachmentType type) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        try {
            User user = userResolver.resolveCurrentUser();
            if (user == null) {
                throw new IllegalStateException("未找到当前登录用户");
            }
            Long uploadedBy = user.getId();
            String operatorUsername = user.getFullName() + "(" + user.getUsername() + ")";
            WarehouseAttachmentEntity entity = fileService.upload(id, type, file, uploadedBy);
            WarehouseEntity wh = repo.findById(id).orElse(null);
            if (wh != null) {
                warehouseLogService.saveLog(wh, WarehouseActionType.ATTACH_UPLOAD,
                        typeLabel(type), null, null,
                        "上传附件：" + entity.getOriginalFilename(), operatorUsername, uploadedBy);
            }
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("上传成功", warehouseMapper.toAttachmentDTO(entity)));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<ApiResponse<List<WarehouseAttachmentDTO>>> list(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(attachmentRepo.findByWarehouseId(id).stream()
                .map(warehouseMapper::toAttachmentDTO).toList()));
    }

    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    @Auditable(action = "DELETE", entityType = "WarehouseAttachment", description = "删除仓库附件")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id, @PathVariable Long attachmentId) {
        return attachmentRepo.findById(attachmentId)
                .filter(a -> a.getWarehouse().getId().equals(id))
                .map(a -> {
                    User user = userResolver.resolveCurrentUser();
                    String operatorUsername = user != null ? user.getFullName() + "(" + user.getUsername() + ")" : "system";
                    Long operatorId = user != null ? user.getId() : null;
                    String fileName = a.getOriginalFilename();
                    WarehouseAttachmentType type = a.getType();
                    fileService.delete(a);
                    WarehouseEntity wh = repo.findById(id).orElse(null);
                    if (wh != null) {
                        warehouseLogService.saveLog(wh, WarehouseActionType.ATTACH_DELETE,
                                typeLabel(type), null, null,
                                "删除附件：" + fileName, operatorUsername, operatorId);
                    }
                    return ResponseEntity.ok(ApiResponse.<Void>success("删除成功", null));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{attachmentId}/download")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @PathVariable Long id, @PathVariable Long attachmentId) {
        return attachmentRepo.findById(attachmentId)
                .filter(a -> a.getWarehouse().getId().equals(id))
                .map(a -> {
                    var resource = fileService.download(a);
                    ContentDisposition disposition = ContentDisposition.attachment()
                            .filename(a.getOriginalFilename(), StandardCharsets.UTF_8)
                            .build();
                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(a.getContentType()))
                            .contentLength(a.getFileSize())
                            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                            .body(resource);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private static String typeLabel(WarehouseAttachmentType type) {
        return type == WarehouseAttachmentType.PROPERTY_CERTIFICATE ? "产权证"
                : type == WarehouseAttachmentType.INVOICE ? "发票" : "内外照片";
    }
}
