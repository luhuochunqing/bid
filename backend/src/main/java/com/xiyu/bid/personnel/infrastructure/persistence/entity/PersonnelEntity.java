package com.xiyu.bid.personnel.infrastructure.persistence.entity;

import com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "personnel")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonnelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "employee_number", nullable = false, unique = true, length = 50)
    private String employeeNumber;

    @Column(name = "department_code", length = 50)
    private String departmentCode;

    @Column(name = "department_name", length = 100)
    private String departmentName;

    @Column(length = 10)
    private String gender;

    @Column(name = "entry_date")
    private java.time.LocalDate entryDate;

    @Column(length = 20)
    private String phone;

    @Column(length = 50)
    private String education;

    @Column(name = "technical_title", length = 100)
    private String technicalTitle;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PersonnelStatus status;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Integer version;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
