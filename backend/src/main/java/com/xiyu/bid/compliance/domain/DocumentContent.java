package com.xiyu.bid.compliance.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 解析后的标书文档结构化内容（含物理分页与全文哈希）.
 */
public record DocumentContent(
        String fileName,
        String fileHash,
        String fullText,
        List<PageContent> pages
) implements Serializable {

    public DocumentContent {
        if (pages == null) {
            pages = Collections.emptyList();
        } else {
            pages = Collections.unmodifiableList(new ArrayList<>(pages));
        }
        if (fullText == null) {
            fullText = "";
        }
        if (fileHash == null) {
            fileHash = "";
        }
        if (fileName == null) {
            fileName = "";
        }
    }

    /**
     * 单页物理切片内容.
     */
    public record PageContent(
            int pageNumber,
            String text,
            List<String> headings
    ) implements Serializable {
        public PageContent {
            if (text == null) {
                text = "";
            }
            if (headings == null) {
                headings = Collections.emptyList();
            } else {
                headings = Collections.unmodifiableList(new ArrayList<>(headings));
            }
        }
    }

    /**
     * 空文档内容.
     */
    public static DocumentContent empty() {
        return new DocumentContent("", "", "", Collections.emptyList());
    }

    /**
     * 从单文本构建简单分页文档（兼容测试与纯文本输入）.
     */
    public static DocumentContent fromText(
            final String fileName, final String fileHash, final String text) {
        if (text == null || text.isBlank()) {
            return empty();
        }
        // 若文本中含有分页符 \f，按分页符切分
        String[] splitPages = text.split("\f");
        List<PageContent> pageList = new ArrayList<>();
        for (int i = 0; i < splitPages.length; i++) {
            pageList.add(new PageContent(i + 1, splitPages[i], Collections.emptyList()));
        }
        return new DocumentContent(fileName, fileHash, text, pageList);
    }
}
