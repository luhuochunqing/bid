// Input: Sidecar /convert JSON 负载 + 文件名 + 内容哈希；本地纯文本降级读取
// Output: DocumentContent（pages→\f→虚拟分页优先级）、warnings、SHA-256 工具
// Pos: compliance/service/ - Sidecar 负载解析与本地降级（Jackson 依赖留在 Shell 层）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiyu.bid.compliance.domain.DocumentContent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Sidecar 负载解析器：把 /convert 响应转换为 {@link DocumentContent}，
 * 并提供纯文本小文件本地降级与 SHA-256 工具（纯函数，无状态）.
 */
public final class SidecarPayloadParser {

    /** 允许本地降级读取的纯文本扩展名. */
    private static final Set<String> PLAIN_TEXT_EXTENSIONS = Set.of(
            ".txt", ".md", ".markdown", ".csv", ".json", ".xml", ".html", ".htm"
    );

    /** 本地降级读取的大小上限（防 DirectBuffer OOM）. */
    private static final long MAX_LOCAL_READ_BYTES = 5 * 1024 * 1024;

    private SidecarPayloadParser() {
    }

    /**
     * 解析 Sidecar /convert 响应为 DocumentContent：
     * 优先消费结构化 pages（PDF 物理页）；缺省时回退 {@link #buildDocumentContent}
     * 的 \f 切分与虚拟分页兜底.
     */
    public static DocumentContent documentFromSidecarPayload(
            final String fileName, final String contentHash, final JsonNode payload) {
        String markdown = payload.path("markdown").asText("");
        JsonNode pagesNode = payload.path("pages");
        if (pagesNode.isArray() && pagesNode.size() > 0) {
            List<DocumentContent.PageContent> pages = new ArrayList<>();
            for (int i = 0; i < pagesNode.size(); i++) {
                JsonNode pageNode = pagesNode.get(i);
                String pageText = pageNode.path("text").asText("");
                if (pageText.isBlank()) {
                    continue;
                }
                int pageNumber = Math.max(1, pageNode.path("pageNumber").asInt(pages.size() + 1));
                pages.add(new DocumentContent.PageContent(pageNumber, pageText, Collections.emptyList()));
            }
            if (!pages.isEmpty()) {
                return new DocumentContent(fileName, contentHash, markdown, pages);
            }
        }
        return buildDocumentContent(fileName, contentHash, markdown);
    }

    /**
     * 将纯文本与哈希封装为 DocumentContent.
     */
    public static DocumentContent buildDocumentContent(
            final String fileName, final String fileHash, final String fullText) {
        if (fullText == null || fullText.isBlank()) {
            return new DocumentContent(fileName, fileHash, "", Collections.emptyList());
        }

        // 识别物理分页符 \f
        String[] rawPages = fullText.split("\f");
        List<DocumentContent.PageContent> pages = new ArrayList<>();
        for (int i = 0; i < rawPages.length; i++) {
            String pageText = rawPages[i].trim();
            if (!pageText.isEmpty()) {
                pages.add(new DocumentContent.PageContent(i + 1, pageText, Collections.emptyList()));
            }
        }
        if (pages.isEmpty()) {
            pages.add(new DocumentContent.PageContent(1, fullText, Collections.emptyList()));
        }
        // 分页缺失兜底：markitdown 输出无 \f 时整篇退化为单页，
        // 长文本按标题/段落虚拟分页，恢复页数判断、证据定位与检索预算的有效性
        if (pages.size() == 1 && fullText.length() > DocumentContent.VIRTUAL_PAGE_MIN_TEXT_LENGTH) {
            pages = DocumentContent.virtualPages(fullText);
        }
        return new DocumentContent(fileName, fileHash, fullText, pages);
    }

    /** 提取 Sidecar 响应 warnings（如 low_text_density），非数组时返回空列表. */
    public static List<String> warningsOf(final JsonNode payload) {
        JsonNode warningsNode = payload == null ? null : payload.path("warnings");
        if (warningsNode == null || !warningsNode.isArray()) {
            return List.of();
        }
        List<String> warnings = new ArrayList<>();
        for (JsonNode node : warningsNode) {
            String text = node.asText("");
            if (!text.isBlank()) {
                warnings.add(text);
            }
        }
        return warnings;
    }

    /** 是否为可本地降级读取的纯文本小文件（≤5MB）. */
    public static boolean isSmallPlainTextFile(final String fileName, final Path filePath) {
        if (fileName == null || filePath == null) {
            return false;
        }
        String lowerName = fileName.toLowerCase();
        boolean isPlainText = PLAIN_TEXT_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
        if (!isPlainText) {
            return false;
        }
        try {
            return Files.size(filePath) <= MAX_LOCAL_READ_BYTES;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * 本地读取纯文本文件（仅限 {@link #isSmallPlainTextFile} 通过的文件）；
     * 读取失败返回空串，由调用方保持失败分类.
     */
    public static String readLocalPlainText(final Path filePath) {
        try {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /** 计算文件 SHA-256（失败返回空串）. */
    public static String computeSha256(final Path path) {
        if (path == null || !Files.exists(path)) {
            return "";
        }
        try (InputStream is = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (IOException | java.security.NoSuchAlgorithmException | RuntimeException e) {
            return "";
        }
    }
}
