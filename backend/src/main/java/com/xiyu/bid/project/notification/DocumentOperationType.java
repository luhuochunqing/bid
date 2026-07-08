package com.xiyu.bid.project.notification;

import lombok.Getter;

/**
 * 文档操作类型枚举（蓝图 §消息中心-系统通知 序号 5）。
 *
 * <p>替代散落在调用方的 {@code "上传"/"删除"} 字符串字面量，提供编译期校验。
 * {@link #getLabel()} 返回中文标签，用于通知正文展示；
 * 持久化/统计建议用 {@link #name()}（英文稳定契约）。</p>
 *
 * <p>{@link #MODIFY} 当前无调用方（产品层无文档修改 API），保留为占位——
 * 未来新增修改 API 时直接使用，无需改枚举。</p>
 */
@Getter
public enum DocumentOperationType {
    UPLOAD("上传"),
    MODIFY("修改"),
    DELETE("删除");

    private final String label;

    DocumentOperationType(String label) {
        this.label = label;
    }
}
