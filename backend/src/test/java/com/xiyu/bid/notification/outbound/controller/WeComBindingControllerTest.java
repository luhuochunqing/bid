package com.xiyu.bid.notification.outbound.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.notification.outbound.dto.WeComBindingRequest;
import com.xiyu.bid.notification.outbound.service.WeComBindingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeComBindingController — admin binding endpoints")
class WeComBindingControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private WeComBindingService bindingService;

    @InjectMocks private WeComBindingController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET returns current binding")
    void get_ReturnsBinding() throws Exception {
        when(bindingService.currentBinding(7L)).thenReturn("wc_007");

        mockMvc.perform(get("/api/admin/users/7/wecom-binding"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.wecomUserId").value("wc_007"));
    }

    @Test
    @DisplayName("PUT binds the user and returns ok")
    void put_Binds() throws Exception {
        String body = objectMapper.writeValueAsString(new WeComBindingRequest("wc_007"));

        mockMvc.perform(put("/api/admin/users/7/wecom-binding")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.wecomUserId").value("wc_007"));

        verify(bindingService).bind(7L, "wc_007");
    }

    @Test
    @DisplayName("PUT with blank wecomUserId returns 400")
    void put_RejectsBlank() throws Exception {
        String body = objectMapper.writeValueAsString(new WeComBindingRequest(""));

        mockMvc.perform(put("/api/admin/users/7/wecom-binding")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT with control chars rejects via @Pattern")
    void put_RejectsControlChars() throws Exception {
        String body = objectMapper.writeValueAsString(new WeComBindingRequest("abc\r\nX"));

        mockMvc.perform(put("/api/admin/users/7/wecom-binding")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT with Chinese characters rejects via @Pattern")
    void put_RejectsChineseChars() throws Exception {
        String body = objectMapper.writeValueAsString(new WeComBindingRequest("张三"));

        mockMvc.perform(put("/api/admin/users/7/wecom-binding")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE unbinds the user")
    void delete_Unbinds() throws Exception {
        mockMvc.perform(delete("/api/admin/users/7/wecom-binding"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(bindingService).unbind(7L);
    }
}
