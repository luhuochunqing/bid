package com.xiyu.bid.bidresult.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.testsupport.integration.ProjectDocumentApiTestSupport;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;

final class BidResultIntegrationHttpSupport {

    private final ProjectDocumentApiTestSupport documentApiSupport;
    private final User adminUser;

    BidResultIntegrationHttpSupport(MockMvc mockMvc, ObjectMapper objectMapper, User adminUser) {
        this.documentApiSupport = new ProjectDocumentApiTestSupport(mockMvc, objectMapper);
        this.adminUser = adminUser;
    }

    long createProjectDocument(
            Long projectId,
            String linkedEntityType,
            Long linkedEntityId,
            String category,
            String fileName,
            String fileSize
    ) throws Exception {
        return documentApiSupport.createProjectDocumentId(
                projectId,
                adminUser,
                category,
                linkedEntityType,
                linkedEntityId,
                fileName,
                fileSize,
                APPLICATION_PDF_VALUE,
                "https://files.example.com/bid/" + fileName
        );
    }

    long createBidResultDocument(Long projectId, Long linkedEntityId, String category, String fileName)
            throws Exception {
        return createProjectDocument(projectId, "BID_RESULT", linkedEntityId, category, fileName, "3MB");
    }

    JsonNode readDataNode(String response) throws Exception {
        return documentApiSupport.readDataNode(response);
    }
}
