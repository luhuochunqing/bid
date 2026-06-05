package com.xiyu.bid.personnel.domain.importvalidation;

import java.util.Objects;

/**
 * 附件文件名解析结果（纯核心值对象）
 *
 * 蓝图约定格式：
 * PER_{姓名}_{工号}_{序号}_{证书名称}.{扩展名}
 *
 * 示例：PER_张三_EMP001_01_一级建造师.pdf
 */
public record ParsedAttachmentName(
        String rawFileName,
        String personnelName,           // 仅用于展示和交叉校验警告，不作为匹配主键
        String employeeNumber,          // 匹配主键（必须存在且与Sheet1一致）
        Integer sequenceNumber,         // 同一人员下证书序号，从01开始
        String certificateNamePart,     // 证书名称部分（不含扩展名）
        String fileExtension            // 小写，不含点，例如 "pdf", "jpg"
) {

    public ParsedAttachmentName {
        Objects.requireNonNull(rawFileName, "rawFileName cannot be null");
    }

    /**
     * 是否符合基本格式规范（可进一步加强校验）
     */
    public boolean isWellFormed() {
        return employeeNumber != null && !employeeNumber.isBlank()
                && sequenceNumber != null && sequenceNumber > 0
                && fileExtension != null && !fileExtension.isBlank();
    }

    /**
     * 生成推荐的规范文件名（用于修正提示）
     */
    public String toCanonicalFileName() {
        if (!isWellFormed()) {
            return rawFileName;
        }
        String namePart = personnelName != null ? personnelName : "未知姓名";
        return String.format("PER_%s_%s_%02d_%s.%s",
                namePart,
                employeeNumber,
                sequenceNumber,
                certificateNamePart != null ? certificateNamePart : "未知证书",
                fileExtension);
    }
}
