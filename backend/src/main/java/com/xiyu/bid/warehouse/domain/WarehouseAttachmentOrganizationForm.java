package com.xiyu.bid.warehouse.domain;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 仓库附件导出时的组织形式（CO-582 §3.2）。
 * <p>
 * 用户可单选其中一种，也可两种同时勾选，但至少选择一种。
 * <ul>
 *   <li>{@link #ATTACHMENTS_FOLDER}：ZIP 内保留 attachments/ 目录，附件以原文件形式存放</li>
 *   <li>{@link #WORD_COMBINED}：ZIP 内包含一个 Word 文档，所有附件内容按层级合并嵌入</li>
 * </ul>
 */
public enum WarehouseAttachmentOrganizationForm {

    /** 附件文件夹（原有形式）：ZIP 内 attachments/ 目录存放附件原文件 */
    ATTACHMENTS_FOLDER,

    /** Word 合订本（新增）：所有附件内容按层级合并嵌入到一个 Word 文档中 */
    WORD_COMBINED;

    /**
     * 从协议层字符串集合构造组织形式集合。
     * <p>
     * CO-582 §3.2：至少选择一种组织形式。默认勾选 "Word 合订本"。
     *
     * @param names 组织形式名称集合（如 {@code Set.of("WORD_COMBINED")}），不能为 null 或空
     * @return 合法的组织形式集合
     * @throws IllegalArgumentException 当集合为空、包含 null/空白值或包含未知名称时
     */
    public static Set<WarehouseAttachmentOrganizationForm> from(Set<String> names) {
        if (names == null || names.isEmpty()) {
            throw new IllegalArgumentException("至少选择一种附件组织形式");
        }
        Set<String> normalized = names.stream()
                .filter(n -> n != null && !n.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("至少选择一种附件组织形式");
        }
        Set<WarehouseAttachmentOrganizationForm> forms = normalized.stream()
                .map(WarehouseAttachmentOrganizationForm::parseName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return Set.copyOf(forms);
    }

    private static WarehouseAttachmentOrganizationForm parseName(String name) {
        try {
            return WarehouseAttachmentOrganizationForm.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知的附件组织形式: " + name
                    + "，可选值: " + java.util.Arrays.stream(WarehouseAttachmentOrganizationForm.values())
                            .map(WarehouseAttachmentOrganizationForm::name)
                            .collect(Collectors.joining(", ")), e);
        }
    }
}
