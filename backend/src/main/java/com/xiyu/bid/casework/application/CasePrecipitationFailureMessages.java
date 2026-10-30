package com.xiyu.bid.casework.application;

import java.util.List;

/** 用户提示的纯转换规则；上游响应、文件路径及凭证只进入异常日志。 */
final class CasePrecipitationFailureMessages {
    private CasePrecipitationFailureMessages() {}

    static String prerequisites(List<String> missing) {
        String next = missing.equals(List.of("缺少评分项")) ? "请先完成评分项解析，再重新生成。"
                : missing.equals(List.of("缺少标书文件")) ? "请先归档标书文件，再重新生成。"
                : "请先归档标书文件并完成评分项解析，再重新生成。";
        return "案例生成失败：" + String.join("；", missing) + "。" + next;
    }

    private static int httpStatus(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.springframework.web.client.HttpStatusCodeException http) {
                return http.getStatusCode().value();
            }
        }
        return 0;
    }

    private static int stageHttpStatus(Throwable error, String stage) {
        var pattern = java.util.regex.Pattern.compile("^" + stage + " HTTP (\\d{3})(?=[:：\\s]|$)");
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            var match = pattern.matcher(cause.getMessage() == null ? "" : cause.getMessage());
            if (match.find()) return Integer.parseInt(match.group(1));
        }
        return 0;
    }

    static String from(Throwable error) {
        StringBuilder chain = new StringBuilder();
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            chain.append(cause.getClass().getSimpleName()).append(':').append(cause.getMessage()).append('\n');
        }
        String detail = chain.toString();
        if (detail.contains("BidFileQuotaException")) {
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (cause instanceof com.xiyu.bid.scoreparse.application.BidFileQuotaException quota) {
                    return "案例生成失败：" + quota.getMessage();
                }
            }
        }
        if (detail.contains("文本量超过")) {
            return "案例生成失败：标书提取后的文本量超过处理上限。请检查是否误传或拆分文档后重新生成。";
        }
        if (detail.contains("超过转换上限")) return "案例生成失败：标书大小超过转换上限。请拆分或精简文件后重新生成。";
        if (detail.contains("AI Provider")) return "案例生成失败：未配置可用的 AI 服务。请联系管理员在 AI 管理页面启用服务后重新生成。";
        if (detail.contains("大模型") || detail.contains("model_not_found")) {
            if (detail.contains("model_not_found")) return "案例生成失败：所用 AI 模型暂无可用通道。请联系管理员检查模型配置后重新生成。";
            int status = httpStatus(error);
            if (status == 401 || status == 403) return "案例生成失败：AI 服务认证或权限校验未通过。请联系管理员检查服务配置。";
            if (status == 429) return "案例生成失败：AI 服务请求频率或额度受限。请稍后重试；持续失败请联系管理员检查额度。";
            return "案例生成失败：AI 服务调用失败或超时。请稍后重试；持续失败请联系管理员。";
        }
        if (detail.contains("文档无法解析") || detail.contains("投标文件无法解析")) {
            return "案例生成失败：标书文档无法解析。请检查文件能否正常打开及文件格式；文件正常仍失败时请联系管理员。";
        }
        int downloadStatus = stageHttpStatus(error, "下载投标文件失败");
        int conversionStatus = stageHttpStatus(error, "调用文档转换服务失败");
        if (detail.contains("OBS 签名失败") || downloadStatus == 401 || downloadStatus == 403) {
            return "案例生成失败：无法获取标书下载权限。请联系管理员检查文件访问权限后重新生成。";
        }
        if (downloadStatus == 404) return "案例生成失败：标书源文件不存在或已不可用。请检查归档文件，重新上传标书后再生成。";
        if (conversionStatus == 401 || conversionStatus == 403) {
            return "案例生成失败：文档转换服务认证或权限校验未通过。请联系管理员检查转换服务配置。";
        }
        if (conversionStatus == 413) return "案例生成失败：标书大小超过文档转换服务接收上限。请拆分或精简文件后重新生成。";
        if (detail.contains("下载") || detail.contains("OBS 标书")) {
            if (!detail.contains("提取")) return "案例生成失败：标书下载失败。请稍后重试；持续失败请联系管理员检查文件存储服务。";
        }
        if (detail.contains("HttpTimeoutException") || detail.contains("SocketTimeoutException")) {
            return "案例生成失败：文档转换服务响应超时。请稍后重试；持续失败请联系管理员。";
        }
        if (detail.contains("IOException") || detail.contains("ConnectException") || detail.contains("文档转换服务")) {
            return "案例生成失败：文档转换服务连接中断或暂不可用。请稍后重试；持续失败请联系管理员。";
        }
        return "案例生成失败：处理过程中发生异常，原因尚未确认。请联系管理员查看该项目的生成日志。";
    }
}
