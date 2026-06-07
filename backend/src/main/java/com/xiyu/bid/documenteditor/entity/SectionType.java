package com.xiyu.bid.documenteditor.entity;

/**
 * 文档章节类型枚举
 * 定义文档中不同类型的章节
 */
public enum SectionType {
    /**
     * 章节 - 主要的文档章节
     */
    CHAPTER,

    /**
     * 小节 - 章节下的子节
     */
    SECTION,

    /**
     * 子小节 - 小节下的更小单位
     */
    SUBSECTION,

    /**
     * 表格 - 数据表格
     */
    TABLE,

    /**
     * 图片 - 图像内容
     */
    IMAGE,

    /**
     * 附件 - 附加文档
     */
    ATTACHMENT
}
