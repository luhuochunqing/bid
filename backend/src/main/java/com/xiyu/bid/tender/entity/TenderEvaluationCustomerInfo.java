package com.xiyu.bid.tender.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标讯评估表-客户信息段 EAV 行（V130 三段式重构新增）。
 *
 * <p>采用 EAV（实体-属性-值）模式存储 14 角色 × 17 维度的客户信息矩阵。
 * 每个单元格为一条记录，通过 (evaluation_id, role_key, info_key) 三元组唯一标识。
 * 对应 PRD §4.2.5 评估表第二段「客户信息」。
 */
@Entity
@Table(name = "tender_evaluation_customer_info", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"evaluation_id", "role_key", "info_key"},
        name = "uk_eval_role_info")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderEvaluationCustomerInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "evaluation_id", nullable = false)
    private TenderEvaluation evaluation;

    /** 角色枚举键，取自 14 个固定角色。 */
    @Column(name = "role_key", nullable = false, length = 32)
    private String roleKey;

    /** 信息维度枚举键，取自 17 个固定信息维度。 */
    @Column(name = "info_key", nullable = false, length = 32)
    private String infoKey;

    /** 单元格值。 */
    @Column(name = "cell_value", length = 500)
    private String cellValue;

    /** 值类型：TEXT / DROPDOWN / SWITCH / ENUM14 / ENUM7 / DROPDOWN6。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 20)
    @Builder.Default
    private ValueType valueType = ValueType.TEXT;

    /** 值类型枚举。 */
    public enum ValueType {
        TEXT,       // 自由文本
        DROPDOWN,   // 下拉选择（是/否 / 支持/中立/反对）
        SWITCH,     // 开关按钮（是/否）
        ENUM14,     // 14选项枚举（职位）
        ENUM7,      // 7选项枚举（触达方式）
        DROPDOWN6   // 6档下拉（对中标影响率）
    }
}
