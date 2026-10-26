// Input: 授权/身份证明附近的低文本扫描页
// Output: 有物理页码和置信度的原图文字，不推断签章或身份真伪
// Pos: ai/compliance/ - 复用真实多模态通道补充扫描页文字
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.ai.compliance;

import com.xiyu.bid.biddraftagent.infrastructure.openai.MultimodalImage;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.PageImage;
import com.xiyu.bid.compliance.domain.policy.DocumentPageVisionDetector;
import com.xiyu.bid.compliance.domain.policy.ScanPageTextPolicy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Profile("!e2e")
@Slf4j
public class OpenAiDocumentPageVisionDetector implements DocumentPageVisionDetector {
    private final OpenAiStructuredOutputService outputService;
    private final OpenAiBidAgentConfigurationResolver configurationResolver;

    public OpenAiDocumentPageVisionDetector(final OpenAiStructuredOutputService outputService,
            final OpenAiBidAgentConfigurationResolver configurationResolver) {
        this.outputService = outputService;
        this.configurationResolver = configurationResolver;
    }

    @Override
    public List<ScanPageTextPolicy.Transcription> transcribe(final DocumentContent document) {
        var candidates = ScanPageTextPolicy.candidatePages(document);
        if (candidates.isEmpty()) return List.of();
        var images = document.images().stream().filter(i -> candidates.contains(i.pageHint())).limit(8).toList();
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
            log.warn("授权扫描页文字视觉通道不可用，保留人工复核，候选页数={}", candidates.size());
            return List.of();
        }
    }

    public static final class Output {
        public List<ScanPageTextPolicy.Transcription> pages;
    }
}
