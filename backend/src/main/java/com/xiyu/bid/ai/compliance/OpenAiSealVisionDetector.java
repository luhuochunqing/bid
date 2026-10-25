package com.xiyu.bid.ai.compliance;

import com.xiyu.bid.biddraftagent.infrastructure.openai.MultimodalImage;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.PageImage;
import com.xiyu.bid.compliance.domain.policy.SealVisionDetector;
import com.xiyu.bid.compliance.domain.policy.DocumentEvidencePageSelector;
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
        List<PageImage> images = document.images().stream()
                .filter(i -> i.pageHint() != null && i.pageHint() > 0).toList();
        if (images.isEmpty()) return SealVisionResult.unavailable("页面图像无物理页码，不能核验签章");
        List<Integer> targets = targetPages(document, images);
        List<PageImage> selected = images.stream().filter(i -> targets.contains(i.pageHint())).toList();
        if (selected.isEmpty()) return SealVisionResult.unavailable("未抽取到目标签章页图像");
        try {
            var config = configurationResolver.resolve(USE_CASE);
            List<SealVisionResult.Observation> observations = new java.util.ArrayList<>();
            for (int offset = 0; offset < selected.size(); offset += MAX_IMAGES) {
                List<PageImage> batch = selected.subList(offset, Math.min(offset + MAX_IMAGES, selected.size()));
                Output output = outputService.request(buildPrompt(document, batch),
                        batch.stream().map(image -> new MultimodalImage(image.mimeType(), image.base64())).toList(),
                        Output.class, config, "AI 未返回印章视觉识别结果");
                if (output == null) return SealVisionResult.unavailable("AI 返回空结果");
                Set<Integer> sent = batch.stream().map(PageImage::pageHint).collect(Collectors.toSet());
                if (output.observations != null) {
                    output.observations.stream().filter(java.util.Objects::nonNull)
                            .filter(o -> sent.contains(o.pageNumber))
                            .map(OpenAiSealVisionDetector::toObservation).forEach(observations::add);
                }
            }
            return SealVisionResult.available(targets, observations, config.model());
        } catch (IllegalStateException e) {
            return SealVisionResult.unavailable(e.getMessage());
        } catch (RuntimeException e) {
            return SealVisionResult.unavailable("视觉识别调用失败");
        }
    }

    private static String buildPrompt(final DocumentContent document, final List<PageImage> images) {
        String pageMap = java.util.stream.IntStream.range(0, images.size())
                .mapToObj(i -> "第" + (i + 1) + "张图片=PDF第" + images.get(i).pageHint() + "页")
                .collect(Collectors.joining("，"));
        return """
                你是投标文件签章完整性核验器。请逐页检查附件图片中的实际印章或签字，不要把“盖章处”“签字”文字当成印章。
                重点检查技术方案末尾、投标函、授权委托书、承诺函等需要盖章页面。
                只输出 JSON：{"targetPages":[5],"observations":[{"pageNumber":5,"type":"COMPANY_SEAL|LEGAL_REP_SEAL|CROSS_PAGE_SEAL|UNKNOWN","detected":true,"confidence":0.0,"x":0.0,"y":0.0,"width":0.0,"height":0.0}]}。
                坐标使用图片宽高归一化的 0 到 1 范围；无法定位坐标时返回 null。targetPages 只列实际需要签章核验的页码。
                文档页数：%d，送检图片数：%d。图片物理页码映射：%s。必须按此映射返回pageNumber，禁止使用图片序号作为PDF页码。
                """.formatted(document.pages().size(), images.size(), pageMap);
    }

    private static List<Integer> targetPages(final DocumentContent document, final List<PageImage> images) {
        List<Integer> targets = DocumentEvidencePageSelector.sealTargets(document).stream().sorted().toList();
        return targets.isEmpty() ? images.stream().map(PageImage::pageHint).distinct().sorted().toList() : targets;
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
