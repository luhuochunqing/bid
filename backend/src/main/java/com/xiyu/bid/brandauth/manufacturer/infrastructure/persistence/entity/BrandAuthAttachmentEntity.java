package com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity;

import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AttachmentType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "brand_auth_attachment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrandAuthAttachmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "authorization_id", nullable = false)
    private Long authorizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type", nullable = false, columnDefinition = "ENUM('AUTH_DOC', 'SUPPLEMENTARY')")
    private AttachmentType attachmentType;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_type", length = 100)
    private String fileType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
