package com.xiyu.bid.scoreparse.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.scoreparse.application.BidDocumentUploadService;
import com.xiyu.bid.scoreparse.application.ScoreParseAppService;
import com.xiyu.bid.scoreparse.application.ScoreScoringAppService;
import com.xiyu.bid.scoreparse.dto.ScoreParseItemsDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ScoreParseControllerTest {

    @Mock
    private ScoreParseAppService scoreParseAppService;

    @Mock
    private ScoreScoringAppService scoreScoringAppService;

    @Mock
    private BidDocumentUploadService bidDocumentUploadService;

    private ScoreParseController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new ScoreParseController(scoreParseAppService, scoreScoringAppService, bidDocumentUploadService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();
    }

    @Test
    void handleAccessDenied_returnsForbiddenWithExactPrdMessage() {
        AccessDeniedException ex = new AccessDeniedException("Access is denied");
        ResponseEntity<ApiResponse<Void>> response = controller.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(403);
        assertThat(response.getBody().getMsg()).isEqualTo("您无权限查看此任务的评分解析结果");
    }

    @Test
    void mockMvc_whenAccessDenied_returns403WithPrdMessage() throws Exception {
        when(scoreParseAppService.getItems(100L)).thenThrow(new AccessDeniedException("Access is denied"));

        mockMvc.perform(get("/api/projects/100/score-parse/items"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("您无权限查看此任务的评分解析结果"));
    }

    @Test
    void getItems_success_returnsItemsDTO() {
        ScoreParseItemsDTO dto = new ScoreParseItemsDTO(java.util.List.of(), null, null);
        when(scoreParseAppService.getItems(100L)).thenReturn(dto);

        ResponseEntity<ApiResponse<ScoreParseItemsDTO>> response = controller.getItems(100L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(dto);
    }
}
