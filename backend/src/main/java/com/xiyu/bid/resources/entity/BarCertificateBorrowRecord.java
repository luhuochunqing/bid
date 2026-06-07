package com.xiyu.bid.resources.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bar_certificate_borrow_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarCertificateBorrowRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "certificate_id", nullable = false)
    private Long certificateId;

    @Column(nullable = false, length = 100)
    private String borrower;

    @Column(name = "project_id")
    private Long projectId;

    @Column(length = 200)
    private String purpose;

    @Column(length = 500)
    private String remark;

    @Column(name = "borrowed_at", nullable = false)
    private LocalDateTime borrowedAt;

    @Column(name = "expected_return_date")
    private LocalDate expectedReturnDate;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BorrowStatus status;

    @PrePersist
    protected void onCreate() {
        if (borrowedAt == null) {
            borrowedAt = LocalDateTime.now();
        }
    }

    public enum BorrowStatus {
        BORROWED,
        RETURNED
    }
}
