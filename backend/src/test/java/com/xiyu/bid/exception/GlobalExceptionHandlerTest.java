package com.xiyu.bid.exception;

import com.openai.core.http.Headers;
import com.openai.errors.UnauthorizedException;
import com.openai.models.ErrorObject;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleOpenAiUnauthorizedException_shouldReturnGenericAiCredentialMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/projects/1/tender-breakdown");
        ErrorObject error = ErrorObject.builder()
                .code("invalid_api_key")
                .message("Authentication Fails, Your api key: ****2f99 is invalid")
                .param("api_key")
                .type("invalid_request_error")
                .build();
        UnauthorizedException exception = UnauthorizedException.builder()
                .headers(Headers.builder().build())
                .error(error)
                .build();

        ResponseEntity<ApiResponse<Void>> response = handler.handleOpenAiUnauthorizedException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(502);
        assertThat(response.getBody().getMessage()).contains("AI provider API Key 无效或已失效");
        assertThat(response.getBody().getMessage()).doesNotContain("DeepSeek");
        assertThat(response.getBody().getMessage()).doesNotContain("2f99");
    }

    @Test
    void handleResourceNotFoundException_shouldReturn404() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/forms/tender.entry");
        ResourceNotFoundException exception = new ResourceNotFoundException("FormDefinition", "tender.entry");

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleResourceNotFoundException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("请求的资源不存在");
    }

    @Test
    void handleResourceNotFoundException_messageOnly_shouldReturn404() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/bid-results/fetch-results/confirm-batch");
        ResourceNotFoundException exception = new ResourceNotFoundException("Bid result fetch record not found: 999999");

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleResourceNotFoundException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("请求的资源不存在");
    }

    @Test
    void handleResponseStatusException_shouldReturnCorrectStatus() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/resource/999");
        ResponseStatusException exception = new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Resource not found");

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleResponseStatusException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("Resource not found");
    }

    @Test
    void handleResponseStatusException_conflictStatus() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tenders");
        ResponseStatusException exception = new ResponseStatusException(
                HttpStatus.CONFLICT, "Duplicate entry");

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleResponseStatusException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(409);
    }

    @Test
    void handleResponseStatusException_nullReason_shouldUseFallbackMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/resource/1");
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleResponseStatusException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("请求无法处理");
    }

    @Test
    void handleTenderDuplicateException_shouldReturn400WithMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tenders");
        Tender duplicate = Tender.builder()
                .id(1L)
                .title("已有标讯")
                .purchaserName("测试采购人")
                .registrationDeadline(LocalDateTime.of(2026, 7, 1, 12, 0))
                .bidOpeningTime(LocalDateTime.of(2026, 7, 15, 10, 0))
                .build();
        TenderDuplicateException exception = new TenderDuplicateException(List.of(duplicate));

        ResponseEntity<ApiResponse<Void>> response = handler.handleTenderDuplicate(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("投标管理系统该标讯已存在");
        assertThat(response.getBody().getData()).isNull();
    }
}
