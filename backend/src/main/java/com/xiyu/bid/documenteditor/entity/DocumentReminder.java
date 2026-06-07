package com.xiyu.bid.documenteditor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_reminders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(name = "recipient", nullable = false)
    private String recipient;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "reminded_by")
    private Long remindedBy;

    @Column(name = "reminded_at", nullable = false)
    private LocalDateTime remindedAt;
}
