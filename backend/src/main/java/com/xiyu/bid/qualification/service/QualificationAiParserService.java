package com.xiyu.bid.qualification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.ai.client.AiProviderRuntimeConfig;
import com.xiyu.bid.ai.client.RoutingAiProvider;
import com.xiyu.bid.docinsight.application.DocumentTextExtractor;
import com.xiyu.bid.docinsight.application.ExtractedDocument;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class QualificationAiParserService {

    private final DocumentTextExtractor documentTextExtractor;
    private final RoutingAiProvider routingAiProvider;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public QualificationAiParserService(
            @Qualifier("markItDownSidecarExtractor") DocumentTextExtractor documentTextExtractor,
            RoutingAiProvider routingAiProvider,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.documentTextExtractor = documentTextExtractor;
        this.routingAiProvider = routingAiProvider;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public QualificationDTO extractFromPdf(MultipartFile file) {
        ExtractedDocument doc;
        try {
            doc = documentTextExtractor.extract(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (Exception e) {
            log.error("Failed to extract markdown from uploaded qualification", e);
            throw new BusinessException("无法读取资质证书文件内容，请确认文件未损坏且非空");
        }
        if (doc == null || doc.text() == null || doc.text().isBlank()) {
            throw new BusinessException("资质证书文件内容为空，请确认上传文件正确");
        }
        return callAiToParse(doc.text());
    }

    private QualificationDTO callAiToParse(String markdown) {
        // AI 未启用或未配置时，RoutingAiProvider.resolveActiveConfig() 会抛 IllegalStateException，
        // 由 GlobalExceptionHandler 转成 409 响应提示用户去系统设置启用 AI；这里不再吞异常返回 Mock。
        AiProviderRuntimeConfig config = routingAiProvider.resolveActiveConfig();

        try {
            String prompt = "你是一个专业的投标资质证书提取助手。请从以下资质证书的文本内容中提取证书的关键元数据。\n" +
                    "你需要返回一个 JSON 对象，且仅返回 JSON 对象本身，包含以下字段：\n" +
                    "- name: 证书名称\n" +
                    "- certificateNo: 证书编号\n" +
                    "- issuer: 发证机构\n" +
                    "- holderName: 证书持有人\n" +
                    "- expiryDate: 有效期截止日期（格式为 YYYY-MM-DD，若证书为长期则返回 2099-12-31）\n\n" +
                    "证书文本内容：\n" + markdown;

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", config.model());
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", "You only reply with valid JSON object."),
                    Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("temperature", 0.1);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.apiKey());

            ResponseEntity<String> response = restTemplate.exchange(
                    config.baseUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    String.class
            );

            if (response.getBody() == null) {
                throw new BusinessException("AI 返回结果为空，请稍后重试或人工填写字段");
            }
            String jsonStr = extractJson(response.getBody());
            JsonNode root = objectMapper.readTree(jsonStr);
            QualificationDTO dto = new QualificationDTO();
            dto.setName(root.path("name").asText(""));
            dto.setCertificateNo(root.path("certificateNo").asText(""));
            dto.setIssuer(root.path("issuer").asText(""));
            dto.setHolderName(root.path("holderName").asText(""));
            String expiryStr = root.path("expiryDate").asText("");
            if (!expiryStr.isBlank()) {
                try {
                    dto.setExpiryDate(LocalDate.parse(expiryStr));
                } catch (DateTimeParseException ignored) { log.debug("Invalid date parse", ignored); }
            }
            return dto;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI Qualification Extraction failed", e);
            throw new BusinessException("AI 解析资质证书失败，请稍后重试或人工填写字段");
        }
    }

    private String extractJson(String response) {
        int startIndex = response.indexOf("{");
        int endIndex = response.lastIndexOf("}");
        if (startIndex >= 0 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1);
        }
        return response;
    }
}
