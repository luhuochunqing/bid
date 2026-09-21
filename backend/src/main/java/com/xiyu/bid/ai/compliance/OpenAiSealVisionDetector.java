package com.xiyu.bid.ai.compliance;

import com.xiyu.bid.biddraftagent.infrastructure.openai.MultimodalImage;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.PageImage;
import com.xiyu.bid.compliance.domain.policy.SealVisionDetector;
import com.xiyu.bid.compliance.domain.policy.SealVisionResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 真实多模态印章识别适配器，使用既有 CHAT_COMPLETIONS 图片传输通道。 */
@Service
@Profile("!e2e")
public class OpenAiSealVisionDetector implements SealVisionDetector {

    private static final String USE_CASE = "bid document seal vision detection";
    private static final int MAX_IMAGES = 24;
    private static final Set<String> TARGET_KEYWORDS = Set.of(
            "技术方案", "实施方案", "承诺函", "投标函", "授权委托书", "盖章", "签章");

    private final OpenAiStructuredOutputService outputService;
    private final OpenAiBidAgentConfigurationResolver configurationResolver;

    public OpenAiSealVisionDetector(
            final OpenAiStructuredOutputService outputService,
            final OpenAiBidAgentConfigurationResolver configurationResolver) {
        this.outputService = outputService;
        this.configurationResolver = configurationResolver;
    }

    @Override
    public SealVisionResult detect(final DocumentContent document) {
        if (document == null || document.images().isEmpty()) {
            return SealVisionResult.unavailable("未抽取到页面图像");
        }
        List<PageImage> images = document.images().stream().limit(MAX_IMAGES).toList();
        try {
            var config = configurationResolver.resolve(USE_CASE);
            Output output = outputService.request(
                    buildPrompt(document, images),
                    images.stream().map(image -> new MultimodalImage(image.mimeType(), image.base64())).toList(),
                    Output.class,
                    config,
                    "AI 未返回印章视觉识别结果");
            if (output == null) {
                return SealVisionResult.unavailable("AI 返回空结果");
            }
            List<SealVisionResult.Observation> observations = output.observations == null
                    ? List.of() : output.observations.stream().map(OpenAiSealVisionDetector::toObservation).toList();
            List<Integer> targets = output.targetPages == null || output.targetPages.isEmpty()
                    ? targetPages(document, images) : output.targetPages;
            return SealVisionResult.available(targets, observations, config.model());
        } catch (IllegalStateException e) {
            return SealVisionResult.unavailable(e.getMessage());
        } catch (RuntimeException e) {
            return SealVisionResult.unavailable("视觉识别调用失败");
        }
    }

    private static String buildPrompt(final DocumentContent document, final List<PageImage> images) {
        return """
                你是投标文件签章完整性核验器。请逐页检查附件图片中的实际印章或签字，不要把“盖章处”“签字”文字当成印章。
                重点检查技术方案末尾、投标函、授权委托书、承诺函等需要盖章页面。
                只输出 JSON：{"targetPages":[5],"observations":[{"pageNumber":5,"type":"COMPANY_SEAL|LEGAL_REP_SEAL|CROSS_PAGE_SEAL|UNKNOWN","detected":true,"confidence":0.0,"x":0.0,"y":0.0,"width":0.0,"height":0.0}]}。
                坐标使用图片宽高归一化的 0 到 1 范围；无法定位坐标时返回 null。targetPages 只列实际需要签章核验的页码。
                文档页数：%d，送检图片数：%d。
                """.formatted(document.pages().size(), images.size());
    }

    private static List<Integer> targetPages(final DocumentContent document, final List<PageImage> images) {
        Set<Integer> fromText = document.pages().stream()
                .filter(page -> TARGET_KEYWORDS.stream().anyMatch(keyword -> page.text().contains(keyword)))
                .map(DocumentContent.PageContent::pageNumber).collect(Collectors.toSet());
        List<Integer> targets = images.stream().map(PageImage::pageHint).filter(java.util.Objects::nonNull)
                .filter(fromText::contains).distinct().sorted().toList();
        if (!targets.isEmpty()) {
            return targets;
        }
        return images.stream().map(PageImage::pageHint).filter(java.util.Objects::nonNull).distinct().sorted().toList();
    }

    private static SealVisionResult.Observation toObservation(final Observation output) {
        SealVisionResult.SealType type;
        try {
            type = SealVisionResult.SealType.valueOf(output.type == null ? "UNKNOWN" : output.type);
        } catch (IllegalArgumentException e) {
            type = SealVisionResult.SealType.UNKNOWN;
        }
        return new SealVisionResult.Observation(output.pageNumber, type, output.detected, output.confidence,
                output.x, output.y, output.width, output.height);
    }

    public static final class Output {
        public List<Integer> targetPages;
        public List<Observation> observations;
    }

    public static final class Observation {
        public int pageNumber;
        public String type;
        public boolean detected;
        public double confidence;
        public Double x;
        public Double y;
        public Double width;
        public Double height;
    }
}
