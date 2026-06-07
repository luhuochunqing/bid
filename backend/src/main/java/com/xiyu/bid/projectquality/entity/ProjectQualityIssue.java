package com.xiyu.bid.projectquality.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "project_quality_issues")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectQualityIssue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "check_id", nullable = false)
    private Long checkId;

    @Column(nullable = false)
    private String type;

    @Column(name = "original_text", columnDefinition = "TEXT")
    private String originalText;

    @Column(name = "suggestion_text", columnDefinition = "TEXT")
    private String suggestionText;

    @Column(name = "location_label")
    private String locationLabel;

    @Column(nullable = false)
    private boolean adopted;

    @Column(nullable = false)
    private boolean ignored;
}
