package com.xiyu.bid.casework.application;

import com.xiyu.bid.scoreparse.application.BidFileQuotaException;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CasePrecipitationFailureMessagesTest {
    @Test void missingScoringItemsExplainNextStep() {
        assertThat(CasePrecipitationFailureMessages.prerequisites(List.of("缺少评分项")))
                .contains("缺少评分项", "评分项", "重新生成").doesNotContain("稍后重试");
    }
    @Test void quotaContainsActualAndConfiguredLimitWithoutRetryAdvice() {
        String message = CasePrecipitationFailureMessages.from(new IllegalStateException("OBS下载失败",
                new BidFileQuotaException(1603796578L, 1073741824L)));
        assertThat(message).contains("1603796578", "1073741824", "拆分").doesNotContain("稍后重试");
        assertThat(message.length()).isLessThanOrEqualTo(255);
    }
    @Test void modelChannelFailureDoesNotLeakUpstreamBodyOrCredentials() {
        String message = CasePrecipitationFailureMessages.from(new IllegalStateException(
                "AI 案例沉淀失败：大模型调用失败，原因：503 model_not_found https://private?token=SECRET"));
        assertThat(message).contains("模型暂无可用通道", "管理员").doesNotContain("SECRET", "https", "503");
    }
    @Test void wrappedConversionTimeoutIsSpecific() {
        assertThat(CasePrecipitationFailureMessages.from(new IllegalStateException(
                "流式提取 OBS 标书文本失败: obs-direct:private", new IOException("retry exhausted", new HttpTimeoutException("timeout")))))
                .contains("文档转换服务响应超时").doesNotContain("obs-direct");
    }
    @Test void invalidDocumentIsNotDeclaredPermanentlyCorrupt() {
        assertThat(CasePrecipitationFailureMessages.from(new IllegalStateException("投标文件无法解析：secret upstream response")))
                .contains("文档无法解析", "文件正常仍失败").doesNotContain("secret", "已损坏");
    }
    @Test void statusDigitsInUpstreamBodyAreNotAuthenticationOrRateLimitErrors() {
        var upstream = org.springframework.web.client.HttpServerErrorException.create(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Unavailable", null,
                "request_id=401429403".getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(CasePrecipitationFailureMessages.from(new IllegalStateException("大模型调用失败", upstream)))
                .contains("AI 服务调用失败").doesNotContain("认证", "额度受限");
    }
    @Test void actualAiAuthenticationFailureExplainsConfigurationAction() {
        var upstream = org.springframework.web.client.HttpClientErrorException.create(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Unauthorized", null, new byte[0], null);
        assertThat(CasePrecipitationFailureMessages.from(new IllegalStateException("大模型调用失败", upstream)))
                .contains("认证或权限校验未通过", "管理员");
    }
    @Test void conversionPermissionFailureRequiresAdministratorInsteadOfRetry() {
        assertThat(CasePrecipitationFailureMessages.from(new IllegalStateException(
                "调用文档转换服务失败 HTTP 401：private shared key invalid")))
                .contains("转换服务认证或权限校验未通过", "管理员").doesNotContain("稍后重试", "private");
    }
    @Test void missingDownloadRequiresCheckingSourceFile() {
        assertThat(CasePrecipitationFailureMessages.from(new IllegalStateException("下载投标文件失败 HTTP 404")))
                .contains("标书源文件不存在", "重新上传").doesNotContain("稍后重试");
    }
    @Test void bodyHttpDigitsDoNotOverrideConversionStatus() {
        assertThat(CasePrecipitationFailureMessages.from(new IllegalStateException(
                "调用文档转换服务失败 HTTP 503：upstream 401403429")))
                .contains("暂不可用").doesNotContain("认证或权限");
    }
    @Test void unknownFailureDoesNotExposeRawException() {
        assertThat(CasePrecipitationFailureMessages.from(new IllegalArgumentException("/etc/private credential")))
                .contains("原因尚未确认", "管理员").doesNotContain("private", "credential");
    }
}
