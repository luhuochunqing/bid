package com.xiyu.bid.dto;

import com.xiyu.bid.entity.Qualification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * 资质数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualificationDTO {

    private Long id;
    private String name;
    private Qualification.Type type;
    private Qualification.Level level;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String fileUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
