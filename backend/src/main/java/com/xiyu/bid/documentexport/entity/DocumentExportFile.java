package com.xiyu.bid.documentexport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "document_export_files", indexes = {
        @Index(name = "idx_document_export_file_export", columnList = "export_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentExportFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "export_id", nullable = false, unique = true)
    private Long exportId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;
}
