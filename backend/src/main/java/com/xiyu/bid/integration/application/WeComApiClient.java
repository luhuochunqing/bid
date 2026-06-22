package com.xiyu.bid.integration.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.config.TraceHeaderInjector;
import com.xiyu.bid.integration.domain.WeComApiErrCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * Low-level HTTP client for the WeCom (企业微信) API.
 * Single responsibility: make HTTP calls and parse raw responses.
 * No caching, no orchestration, no retry logic here.
 */
@Component
@Slf4j
public class WeComApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int LOG_BODY_MAX_LEN = 2000;

    public record WeComAccessTokenResponse(
            String token, long expiresIn, int errcode, String errmsg) {}

    public record WeComUserInfoResponse(
            int errcode, String errmsg, String userId, String openId,
            String user_ticket, int expires_in) {}

    public record WeComUserDetailResponse(
            int errcode, String errmsg, String userid, String name,
            String gender, String avatar, String qr_code, String mobile,
            String email, String biz_mail, String address) {}

    public record WeComSendResponse(
            int errcode, String errmsg, String invaliduser) {}

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public WeComApiClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${wecom.api.base-url:https://qyapi.weixin.qq.com}") String baseUrl,
            @Value("${wecom.http.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${wecom.http.read-timeout-ms:5000}") int readTimeoutMs) {
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    public WeComUserInfoResponse requestUserInfo(String accessToken, String code) {
        String url = baseUrl + "/cgi-bin/user/getuserinfo?access_token={token}&code={code}";
        log.info("WeCom getUserInfo request: url={}", url);
        try {
            HttpHeaders headers = new HttpHeaders();
            TraceHeaderInjector.inject(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    Map.class, accessToken, code);
            log.info("WeCom getUserInfo response: url={}, errcode={}, errmsg={}, body={}",
                    url, errcode(response.getBody()), errmsg(response.getBody()), toLog(response.getBody()));
            return parseUserInfoResponse(response.getBody());
        } catch (HttpStatusCodeException ex) {
            log.error("WeCom getUserInfo HTTP error: url={}, status={}, body={}",
                    url, ex.getStatusCode(), truncate(ex.getResponseBodyAsString()), ex);
            throw new WeComApiException(WeComApiErrCode.UNKNOWN.code(),
                    "WeCom getuserinfo HTTP error: " + ex.getStatusCode(), ex);
        } catch (RestClientException ex) {
            log.error("WeCom getUserInfo request failed: url={}, error={}",
                    url, ex.getMessage(), ex);
            throw new WeComApiException(WeComApiErrCode.UNKNOWN.code(),
                    "WeCom getuserinfo request failed: " + ex.getMessage(), ex);
        }
    }

    public WeComUserDetailResponse requestUserDetail(String accessToken, String userTicket) {
        String url = baseUrl + "/cgi-bin/user/getuserdetail?access_token={token}";
        Map<String, String> payload = Map.of("user_ticket", userTicket);
        log.info("WeCom getUserDetail request: url={}", url);
        try {
            HttpHeaders headers = new HttpHeaders();
            TraceHeaderInjector.inject(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(payload, headers),
                    Map.class, accessToken);
            log.info("WeCom getUserDetail response: url={}, errcode={}, errmsg={}, userid={}, body={}",
                    url, errcode(response.getBody()), errmsg(response.getBody()),
                    field(response.getBody(), "userid"), toLog(response.getBody()));
            return parseUserDetailResponse(response.getBody());
        } catch (HttpStatusCodeException ex) {
            log.error("WeCom getUserDetail HTTP error: url={}, status={}, body={}",
                    url, ex.getStatusCode(), truncate(ex.getResponseBodyAsString()), ex);
            throw new WeComApiException(WeComApiErrCode.UNKNOWN.code(),
                    "WeCom getuserdetail HTTP error: " + ex.getStatusCode(), ex);
        } catch (RestClientException ex) {
            log.error("WeCom getUserDetail request failed: url={}, error={}",
                    url, ex.getMessage(), ex);
            throw new WeComApiException(WeComApiErrCode.UNKNOWN.code(),
                    "WeCom getuserdetail request failed: " + ex.getMessage(), ex);
        }
    }

    public WeComAccessTokenResponse requestAccessToken(String corpId, String corpSecret) {
        String url = baseUrl + "/cgi-bin/gettoken?corpid={corpId}&corpsecret={corpSecret}";
        log.info("WeCom gettoken request: url={}, corpId={}", url, corpId);
        try {
            HttpHeaders headers = new HttpHeaders();
            TraceHeaderInjector.inject(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    Map.class, corpId, corpSecret);
            log.info("WeCom gettoken response: url={}, errcode={}, errmsg={}, body={}",
                    url, errcode(response.getBody()), errmsg(response.getBody()), toLog(response.getBody()));
            return parseTokenResponse(response.getBody());
        } catch (HttpStatusCodeException ex) {
            log.error("WeCom gettoken HTTP error: url={}, status={}, body={}",
                    url, ex.getStatusCode(), truncate(ex.getResponseBodyAsString()), ex);
            throw new WeComApiException(WeComApiErrCode.UNKNOWN.code(),
                    "WeCom gettoken HTTP error: " + ex.getStatusCode(), ex);
        } catch (RestClientException ex) {
            log.error("WeCom gettoken request failed: url={}, error={}",
                    url, ex.getMessage(), ex);
            throw new WeComApiException(WeComApiErrCode.UNKNOWN.code(),
                    "WeCom gettoken request failed: " + ex.getMessage(), ex);
        }
    }

    public WeComSendResponse sendAppMessage(String accessToken, Map<String, Object> payload) {
        String url = baseUrl + "/cgi-bin/message/send?access_token={token}";
        log.info("WeCom sendAppMessage request: url={}, payload={}", url, toJson(payload));
        try {
            HttpHeaders headers = new HttpHeaders();
            TraceHeaderInjector.inject(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(payload, headers),
                    Map.class, accessToken);
            log.info("WeCom sendAppMessage response: url={}, errcode={}, errmsg={}, invaliduser={}, body={}",
                    url, errcode(response.getBody()), errmsg(response.getBody()),
                    field(response.getBody(), "invaliduser"), toLog(response.getBody()));
            return parseSendResponse(response.getBody());
        } catch (HttpStatusCodeException ex) {
            log.error("WeCom sendAppMessage HTTP error: url={}, status={}, body={}",
                    url, ex.getStatusCode(), truncate(ex.getResponseBodyAsString()), ex);
            throw new WeComApiException(WeComApiErrCode.UNKNOWN.code(),
                    "WeCom sendmessage HTTP error: " + ex.getStatusCode(), ex);
        } catch (RestClientException ex) {
            log.error("WeCom sendAppMessage request failed: url={}, error={}",
                    url, ex.getMessage(), ex);
            throw new WeComApiException(WeComApiErrCode.UNKNOWN.code(),
                    "WeCom sendmessage request failed: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private WeComAccessTokenResponse parseTokenResponse(Map<?, ?> body) {
        if (body == null) throw new WeComApiException(WeComApiErrCode.UNKNOWN.code(), "WeCom gettoken returned null body");
        int errcode = body.containsKey("errcode") ? toInt(body.get("errcode")) : 0;
        String errmsg = body.containsKey("errmsg") ? String.valueOf(body.get("errmsg")) : "";
        String token = body.containsKey("access_token") ? String.valueOf(body.get("access_token")) : null;
        long expiresIn = body.containsKey("expires_in") ? toLong(body.get("expires_in")) : 0L;
        return new WeComAccessTokenResponse(token, expiresIn, errcode, errmsg);
    }

    @SuppressWarnings("unchecked")
    private WeComSendResponse parseSendResponse(Map<?, ?> body) {
        if (body == null) throw new WeComApiException(WeComApiErrCode.UNKNOWN.code(), "WeCom sendmessage returned null body");
        int errcode = body.containsKey("errcode") ? toInt(body.get("errcode")) : 0;
        String errmsg = body.containsKey("errmsg") ? String.valueOf(body.get("errmsg")) : "";
        String invaliduser = body.containsKey("invaliduser") ? String.valueOf(body.get("invaliduser")) : null;
        return new WeComSendResponse(errcode, errmsg, invaliduser);
    }

    @SuppressWarnings("unchecked")
    private WeComUserInfoResponse parseUserInfoResponse(Map<?, ?> body) {
        if (body == null) throw new WeComApiException(WeComApiErrCode.UNKNOWN.code(), "WeCom getuserinfo returned null body");
        int errcode = body.containsKey("errcode") ? toInt(body.get("errcode")) : 0;
        String errmsg = body.containsKey("errmsg") ? String.valueOf(body.get("errmsg")) : "";
        String userId = body.containsKey("UserId") ? String.valueOf(body.get("UserId")) : null;
        String openId = body.containsKey("OpenId") ? String.valueOf(body.get("OpenId")) : null;
        String userTicket = body.containsKey("user_ticket") ? String.valueOf(body.get("user_ticket")) : null;
        int expiresIn = body.containsKey("expires_in") ? toInt(body.get("expires_in")) : 0;
        return new WeComUserInfoResponse(errcode, errmsg, userId, openId, userTicket, expiresIn);
    }

    @SuppressWarnings("unchecked")
    private WeComUserDetailResponse parseUserDetailResponse(Map<?, ?> body) {
        if (body == null) throw new WeComApiException(WeComApiErrCode.UNKNOWN.code(), "WeCom getuserdetail returned null body");
        int errcode = body.containsKey("errcode") ? toInt(body.get("errcode")) : 0;
        String errmsg = body.containsKey("errmsg") ? String.valueOf(body.get("errmsg")) : "";
        String userid = body.containsKey("userid") ? String.valueOf(body.get("userid")) : null;
        String name = body.containsKey("name") ? String.valueOf(body.get("name")) : null;
        String gender = body.containsKey("gender") ? String.valueOf(body.get("gender")) : null;
        String avatar = body.containsKey("avatar") ? String.valueOf(body.get("avatar")) : null;
        String qrCode = body.containsKey("qr_code") ? String.valueOf(body.get("qr_code")) : null;
        String mobile = body.containsKey("mobile") ? String.valueOf(body.get("mobile")) : null;
        String email = body.containsKey("email") ? String.valueOf(body.get("email")) : null;
        String bizMail = body.containsKey("biz_mail") ? String.valueOf(body.get("biz_mail")) : null;
        String address = body.containsKey("address") ? String.valueOf(body.get("address")) : null;
        return new WeComUserDetailResponse(errcode, errmsg, userid, name, gender, avatar, qrCode, mobile, email, bizMail, address);
    }

    private int toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        return 0;
    }

    private long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        return 0L;
    }

    // ——— 日志辅助 ———
    private static int errcode(Map<?, ?> body) {
        if (body == null) return -1;
        Object v = body.get("errcode");
        return v instanceof Number n ? n.intValue() : 0;
    }

    private static String errmsg(Map<?, ?> body) {
        return body != null && body.get("errmsg") != null ? String.valueOf(body.get("errmsg")) : null;
    }

    private static String field(Map<?, ?> body, String key) {
        return body != null && body.get(key) != null ? String.valueOf(body.get(key)) : null;
    }

    private static String toLog(Map<?, ?> body) {
        return truncate(toJson(body));
    }

    private static String toJson(Object value) {
        if (value == null) return "null";
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private static String truncate(String value) {
        if (value == null) return "null";
        if (value.length() <= LOG_BODY_MAX_LEN) return value;
        return value.substring(0, LOG_BODY_MAX_LEN) + "...(truncated " + value.length() + " chars)";
    }
}
