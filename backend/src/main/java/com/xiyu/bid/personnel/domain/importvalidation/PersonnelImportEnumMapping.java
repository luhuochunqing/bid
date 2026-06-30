package com.xiyu.bid.personnel.domain.importvalidation;

import java.util.Map;
import java.util.Set;

/**
 * CO-419: 人员证书批量导入枚举值常量与中英文映射。
 *
 * 作为前后端枚举值一致性的唯一真相源：
 * - 模板生成器用 {@link #CERT_TYPE_LABELS} 等中文值生成下拉
 * - 校验器用 {@link #GENDER_VALUES} 等集合做合法性校验
 * - 导入器用 {@link #CERT_TYPE_CN_TO_EN} 把中文映射为英文枚举存储
 *
 * 纯核心：无 Spring 依赖，无 I/O，符合 FP-Java Profile。
 */
public final class PersonnelImportEnumMapping {

    private PersonnelImportEnumMapping() {}

    // ===== 合法枚举值集合（用于校验器） =====

    public static final Set<String> GENDER_VALUES = Set.of("男", "女");

    public static final Set<String> HIGHEST_EDUCATION_VALUES =
            Set.of("初中", "高中", "中专", "大专", "本科", "硕士", "博士");

    public static final Set<String> STUDY_FORM_VALUES =
            Set.of("全日制", "非全日制", "网络教育", "自学考试", "其他");

    public static final Set<String> YES_NO_VALUES = Set.of("是", "否");

    public static final Set<String> CERT_TITLE_VALUES = Set.of("初级", "中级", "高级");

    public static final Set<String> CERT_TYPE_LABELS =
            Set.of("建造师", "PMP", "工程师", "会计师", "律师", "安全工程师", "IT类证书", "其他");

    /**
     * 证书类型英文枚举值集合（导入器映射后 + 校验器校验用）。
     * 与 {@link #CERT_TYPE_CN_TO_EN} 的 values 一致，与前端 personnelConstants.js 的 CERT_TYPE_OPTIONS value 对齐。
     */
    public static final Set<String> CERT_TYPE_EN_VALUES =
            Set.of("CONSTRUCTOR", "PMP", "ENGINEER", "ACCOUNTANT", "LAWYER", "SECURITY", "IT", "OTHER");

    // ===== 证书类型中文→英文映射（用于导入器） =====

    public static final Map<String, String> CERT_TYPE_CN_TO_EN = Map.of(
            "建造师", "CONSTRUCTOR",
            "PMP", "PMP",
            "工程师", "ENGINEER",
            "会计师", "ACCOUNTANT",
            "律师", "LAWYER",
            "安全工程师", "SECURITY",
            "IT类证书", "IT",
            "其他", "OTHER"
    );

    // ===== 下拉可选值数组（用于模板生成器，顺序即下拉顺序） =====

    public static final String[] GENDER_DROPDOWN = {"男", "女"};

    public static final String[] HIGHEST_EDUCATION_DROPDOWN =
            {"初中", "高中", "中专", "大专", "本科", "硕士", "博士"};

    public static final String[] STUDY_FORM_DROPDOWN =
            {"全日制", "非全日制", "网络教育", "自学考试", "其他"};

    public static final String[] YES_NO_DROPDOWN = {"是", "否"};

    public static final String[] CERT_TITLE_DROPDOWN = {"初级", "中级", "高级"};

    public static final String[] CERT_TYPE_DROPDOWN =
            {"建造师", "PMP", "工程师", "会计师", "律师", "安全工程师", "IT类证书", "其他"};
}
