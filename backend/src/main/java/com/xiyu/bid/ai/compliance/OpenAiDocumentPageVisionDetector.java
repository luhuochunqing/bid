// Input: 授权/身份证明/投标函/证照附近的扫描页或内嵌合规图片页
// Output: 有页码和置信度的原图文字（优先走已配置的百度OCR通道，未命中或未启用时回退多模态视觉），不推断签章或身份真伪
// Pos: ai/compliance/ - 复用百度OCR与真实多模态通道补充图片页文字
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.ai.compliance;

import com.xiyu.bid.biddraftagent.infrastructure.openai.MultimodalImage;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.PageImage;
import com.xiyu.bid.compliance.domain.policy.DocumentPageVisionDetector;
import com.xiyu.bid.compliance.domain.policy.ScanPageTextPolicy;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableCell;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableItem;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableResponse;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableWord;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableOcrClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Profile("!e2e")
@Slf4j
public class OpenAiDocumentPageVisionDetector implements DocumentPageVisionDetector {

    private static final double BAIDU_OCR_CONFIDENCE = 0.95;

    private final OpenAiStructuredOutputService outputService;
    private final OpenAiBidAgentConfigurationResolver configurationResolver;
    private final BaiduTableOcrClient baiduTableOcrClient;

    public OpenAiDocumentPageVisionDetector(
            final OpenAiStructuredOutputService outputService,
            final OpenAiBidAgentConfigurationResolver configurationResolver) {
        this(outputService, configurationResolver, null);
    }

    @Autowired
    public OpenAiDocumentPageVisionDetector(
            final OpenAiStructuredOutputService outputService,
            final OpenAiBidAgentConfigurationResolver configurationResolver,
            @Autowired(required = false) final BaiduTableOcrClient baiduTableOcrClient) {
        this.outputService = outputService;
        this.configurationResolver = configurationResolver;
        this.baiduTableOcrClient = baiduTableOcrClient;
    }

    @Override
    public List<ScanPageTextPolicy.Transcription> transcribe(final DocumentContent document) {
        var candidates = ScanPageTextPolicy.candidatePages(document);
        if (candidates.isEmpty()) return List.of();
        var images = document.images().stream().filter(i -> candidates.contains(i.pageHint())).limit(12).toList();
        if (images.isEmpty()) return List.of();

        List<ScanPageTextPolicy.Transcription> results = new ArrayList<>();
        Set<Integer> succeededIndexes = transcribeWithBaiduOcr(images, results);
        List<PageImage> remainingImages = IntStream.range(0, images.size())
                .filter(idx -> !succeededIndexes.contains(idx))
                .mapToObj(images::get)
                .limit(8)
                .toList();

        if (!remainingImages.isEmpty() && !Thread.currentThread().isInterrupted()) {
            results.addAll(transcribeWithMultimodal(remainingImages, candidates.size()));
        }
        return mergeByPageNumber(results);
    }

    private Set<Integer> transcribeWithBaiduOcr(
            final List<PageImage> images, final List<ScanPageTextPolicy.Transcription> outResults) {
        if (baiduTableOcrClient == null || !baiduTableOcrClient.isEnabled()) {
            return Set.of();
        }
        Set<Integer> succeededIndexes = new HashSet<>();
        for (int idx = 0; idx < images.size(); idx++) {
            PageImage image = images.get(idx);
            if (image.pageHint() == null || image.base64() == null || image.base64().isBlank()) {
                continue;
            }
            try {
                byte[] rawBytes = Base64.getDecoder().decode(image.base64());
                byte[] pdfBytes = wrapImageAsSinglePagePdf(rawBytes);
                if (pdfBytes.length == 0) {
                    continue;
                }
                BaiduTableResponse response = baiduTableOcrClient.extractTablesFromPdf(pdfBytes, 1);
                String text = formatBaiduResponse(response);
                if (!text.isBlank()) {
                    outResults.add(new ScanPageTextPolicy.Transcription(
                            image.pageHint(), text, BAIDU_OCR_CONFIDENCE));
                    succeededIndexes.add(idx);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("百度OCR单图识别中断，停止后续OCR调用: pageHint={}", image.pageHint());
                break;
            } catch (IOException | RuntimeException e) {
                log.warn("百度OCR单图识别未命中，将回退多模态通道: pageHint={}, reason={}", image.pageHint(), e.getMessage());
            }
        }
        return succeededIndexes;
    }

    private List<ScanPageTextPolicy.Transcription> transcribeWithMultimodal(
            final List<PageImage> images, final int totalCandidates) {
        if (outputService == null || configurationResolver == null) {
            return List.of();
        }
        var sentPages = images.stream().map(PageImage::pageHint).toList();
        String mapping = IntStream.range(0, images.size()).mapToObj(i -> "图片" + (i + 1) + "=PDF第"
                + images.get(i).pageHint() + "页").collect(Collectors.joining("，"));
        try {
            var config = configurationResolver.resolve("bid authorization scanned page transcription");
            Output output = outputService.request("仅转写图片中实际可见文字，不推断身份真伪、签章有效性或材料齐全。"
                    + "模糊字段留空，禁止补写、猜测姓名或号码。物理页码映射：" + mapping
                    + "。仅输出JSON：{\"pages\":[{\"pageNumber\":1,\"text\":\"原图文字\",\"confidence\":0.0}]}。",
                    images.stream().map(i -> new MultimodalImage(i.mimeType(), i.base64())).toList(),
                    Output.class, config, "扫描页文字识别未返回结果");
            return output == null || output.pages == null ? List.of() : output.pages.stream()
                    .filter(t -> t != null && sentPages.contains(t.pageNumber())).toList();
        } catch (RuntimeException e) {
            log.warn("授权扫描页文字视觉通道不可用，保留人工复核，候选页数={}", totalCandidates);
            return List.of();
        }
    }

    private static List<ScanPageTextPolicy.Transcription> mergeByPageNumber(
            final List<ScanPageTextPolicy.Transcription> raw) {
        if (raw.size() <= 1) {
            return raw;
        }
        Map<Integer, List<ScanPageTextPolicy.Transcription>> grouped = new LinkedHashMap<>();
        for (ScanPageTextPolicy.Transcription item : raw) {
            if (item != null && item.text() != null && !item.text().isBlank()) {
                grouped.computeIfAbsent(item.pageNumber(), k -> new ArrayList<>()).add(item);
            }
        }
        List<ScanPageTextPolicy.Transcription> merged = new ArrayList<>();
        for (Map.Entry<Integer, List<ScanPageTextPolicy.Transcription>> entry : grouped.entrySet()) {
            List<ScanPageTextPolicy.Transcription> list = entry.getValue();
            if (list.size() == 1) {
                merged.add(list.getFirst());
            } else {
                String combinedText = list.stream().map(ScanPageTextPolicy.Transcription::text)
                        .collect(Collectors.joining("\n"));
                double minConfidence = list.stream().mapToDouble(ScanPageTextPolicy.Transcription::confidence)
                        .min().orElse(0.95);
                merged.add(new ScanPageTextPolicy.Transcription(entry.getKey(), combinedText, minConfidence));
            }
        }
        return List.copyOf(merged);
    }

    static byte[] wrapImageAsSinglePagePdf(final byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return new byte[0];
        }
        if (imageBytes.length >= 4 && imageBytes[0] == '%' && imageBytes[1] == 'P'
                && imageBytes[2] == 'D' && imageBytes[3] == 'F') {
            return imageBytes;
        }
        try (PDDocument doc = new PDDocument()) {
            PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, imageBytes, "ocr-page");
            float width = Math.max(100f, pdImage.getWidth());
            float height = Math.max(100f, pdImage.getHeight());
            PDPage page = new PDPage(new PDRectangle(width, height));
            doc.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
                contentStream.drawImage(pdImage, 0, 0, width, height);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException | RuntimeException e) {
            return new byte[0];
        }
    }

    static String formatBaiduResponse(final BaiduTableResponse response) {
        if (response == null || !response.isSuccess() || response.tablesResult().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (BaiduTableItem item : response.tablesResult()) {
            for (BaiduTableWord word : item.header()) {
                if (word != null && word.words() != null && !word.words().isBlank()) {
                    sb.append(word.words().trim()).append('\n');
                }
            }
            Map<Integer, List<BaiduTableCell>> rows = new TreeMap<>();
            for (BaiduTableCell cell : item.body()) {
                if (cell != null && cell.words() != null && !cell.words().isBlank()) {
                    rows.computeIfAbsent(cell.rowStart(), k -> new ArrayList<>()).add(cell);
                }
            }
            for (List<BaiduTableCell> row : rows.values()) {
                row.sort(Comparator.comparingInt(BaiduTableCell::colStart));
                String line = row.stream().map(c -> c.words().replace('\n', ' ').trim())
                        .filter(s -> !s.isBlank()).collect(Collectors.joining(" "));
                if (!line.isBlank()) {
                    sb.append(line).append('\n');
                }
            }
            for (BaiduTableWord word : item.footer()) {
                if (word != null && word.words() != null && !word.words().isBlank()) {
                    sb.append(word.words().trim()).append('\n');
                }
            }
        }
        return sb.toString().trim();
    }

    public static final class Output {
        public List<ScanPageTextPolicy.Transcription> pages;
    }
}
