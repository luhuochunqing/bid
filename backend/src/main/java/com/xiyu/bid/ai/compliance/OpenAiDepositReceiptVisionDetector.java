package com.xiyu.bid.ai.compliance;

import com.xiyu.bid.biddraftagent.infrastructure.openai.MultimodalImage;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.PageImage;
import com.xiyu.bid.compliance.domain.policy.DepositReceiptVisionDetector;
import com.xiyu.bid.compliance.domain.policy.DepositReceiptVisionResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/** 复用既有真实多模态通道，从 PDF 页面图片提取保证金凭证事实。 */
@Service
@Profile("!e2e")
public class OpenAiDepositReceiptVisionDetector implements DepositReceiptVisionDetector {
    private static final String USE_CASE = "bid deposit receipt vision detection";
    private static final int MAX_IMAGES = 24;
    private static final Set<String> CUES = Set.of(
            "投标保证金", "保证金回单", "银行回单", "转账凭证", "电子保函", "保函凭证");

    private final OpenAiStructuredOutputService outputService;
    private final OpenAiBidAgentConfigurationResolver configurationResolver;

    public OpenAiDepositReceiptVisionDetector(
            final OpenAiStructuredOutputService outputService,
            final OpenAiBidAgentConfigurationResolver configurationResolver) {
        this.outputService = outputService;
        this.configurationResolver = configurationResolver;
    }

    @Override
    public DepositReceiptVisionResult detect(final DocumentContent document) {
        if (document == null || document.images().isEmpty()) {
            return DepositReceiptVisionResult.unavailable("未抽取到 PDF 页面图像");
        }
        List<PageImage> images = candidateImages(document);
        try {
            var config = configurationResolver.resolve(USE_CASE);
            Output output = outputService.request(prompt(images),
                    images.stream().map(image -> new MultimodalImage(image.mimeType(), image.base64())).toList(),
                    Output.class, config, "AI 未返回保证金凭证视觉结果");
            if (output == null) {
                return DepositReceiptVisionResult.unavailable("AI 返回空结果");
            }
            List<DepositReceiptVisionResult.Observation> observations = output.observations == null
                    ? List.of() : output.observations.stream().map(OpenAiDepositReceiptVisionDetector::map).toList();
            return DepositReceiptVisionResult.available(observations, config.model());
        } catch (IllegalStateException e) {
            return DepositReceiptVisionResult.unavailable(e.getMessage());
        } catch (RuntimeException e) {
            return DepositReceiptVisionResult.unavailable("保证金凭证视觉调用失败");
        }
    }

    static List<PageImage> candidateImages(final DocumentContent document) {
        Set<Integer> cuePages = document.pages().stream()
                .filter(page -> CUES.stream().anyMatch(page.text()::contains))
                .map(DocumentContent.PageContent::pageNumber).collect(java.util.stream.Collectors.toSet());
        List<PageImage> nearby = document.images().stream()
                .filter(image -> image.pageHint() != null)
                .filter(image -> cuePages.stream().anyMatch(page -> Math.abs(page - image.pageHint()) <= 2))
                .limit(MAX_IMAGES).toList();
        return nearby.isEmpty() ? document.images().stream().limit(MAX_IMAGES).toList() : nearby;
    }

    private static String prompt(final List<PageImage> images) {
        String pageMap = java.util.stream.IntStream.range(0, images.size())
                .mapToObj(index -> "第" + (index + 1) + "张图片=PDF第" + images.get(index).pageHint() + "页")
                .collect(java.util.stream.Collectors.joining("，"));
        return """
                你是投标保证金凭证视觉核验器。逐页检查图片是否包含真实银行回单、转账凭证或电子保函。
                目录、声明、缴纳说明、空白模板和仅出现“保证金”文字都不能算作凭证。
                仅输出 JSON：{"observations":[{"pageNumber":1,"voucherDetected":true,"confidence":0.0,
                "payer":"","payee":"","amount":"","transactionDate":"","bankSerialNumber":"",
                "x":0.0,"y":0.0,"width":0.0,"height":0.0}]}。
                必须按图片真实可见内容提取付款人、收款人、金额、交易日期、银行流水号；不可见则留空，禁止猜测。
                坐标使用 0 到 1 归一化范围；无法定位时返回 null。图片页码映射：%s。
                """.formatted(pageMap);
    }

    private static DepositReceiptVisionResult.Observation map(final Observation item) {
        return new DepositReceiptVisionResult.Observation(item.pageNumber, item.voucherDetected, item.confidence,
                item.payer, item.payee, item.amount, item.transactionDate, item.bankSerialNumber,
                item.x, item.y, item.width, item.height);
    }

    public static final class Output {
        public List<Observation> observations;
    }

    public static final class Observation {
        public int pageNumber;
        public boolean voucherDetected;
        public double confidence;
        public String payer;
        public String payee;
        public String amount;
        public String transactionDate;
        public String bankSerialNumber;
        public Double x;
        public Double y;
        public Double width;
        public Double height;
    }
}
