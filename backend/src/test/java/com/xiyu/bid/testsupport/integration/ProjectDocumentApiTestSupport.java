package com.xiyu.bid.testsupport.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentCreateRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class ProjectDocumentApiTestSupport {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public ProjectDocumentApiTestSupport(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    public JsonNode createProjectDocument(
            Long projectId,
            User uploader,
            String documentCategory,
            String linkedEntityType,
            Long linkedEntityId,
            String fileName,
            String fileSize,
            String fileType,
            String fileUrl
    ) throws Exception {
        ProjectDocumentCreateRequest request = ProjectDocumentCreateRequest.builder()
                .name(fileName)
                .size(fileSize)
                .fileType(fileType)
                .uploaderId(uploader.getId())
                .uploaderName(uploader.getFullName())
                .documentCategory(documentCategory)
                .linkedEntityType(linkedEntityType)
                .linkedEntityId(linkedEntityId)
                .fileUrl(fileUrl)
                .build();

        String response = mockMvc.perform(post("/api/projects/{projectId}/documents", projectId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        return readDataNode(response);
    }

    public long createProjectDocumentId(
            Long projectId,
            User uploader,
            String documentCategory,
            String linkedEntityType,
            Long linkedEntityId,
            String fileName,
            String fileSize,
            String fileType,
            String fileUrl
    ) throws Exception {
        return createProjectDocument(
                projectId,
                uploader,
                documentCategory,
                linkedEntityType,
                linkedEntityId,
                fileName,
                fileSize,
                fileType,
                fileUrl
        ).path("id").asLong();
    }

    public JsonNode readDataNode(String response) throws Exception {
        return objectMapper.readTree(response).path("data");
    }
}
