package com.xiyu.bid.warehouse.infrastructure;

import com.xiyu.bid.warehouse.domain.WarehouseAttachmentReadModel;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "warehouse_attachment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attachment_warehouse_type_stored",
                columnNames = {"warehouse_id", "type", "stored_filename"}
        ))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseAttachmentEntity implements WarehouseAttachmentReadModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private WarehouseEntity warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WarehouseAttachmentType type;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, length = 255)
    private String storedFilename;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    void onCreate() {
        if (uploadedAt == null) uploadedAt = LocalDateTime.now();
    }
}
