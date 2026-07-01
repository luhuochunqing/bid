package com.xiyu.bid.resources.integration;

import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class CaCommitmentLetterUploadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${app.upload.ca-borrow-dir:uploads/ca-borrow}")
    private String uploadDir;

    private Path testUploadPath;

    @BeforeEach
    void setUp() throws IOException {
        testUploadPath = resolveAbsoluteUploadPath();
        Files.createDirectories(testUploadPath);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(testUploadPath)) {
            try (Stream<Path> walk = Files.walk(testUploadPath)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
            }
        }
    }

    private Path resolveAbsoluteUploadPath() {
        Path p = Paths.get(uploadDir);
        if (!p.isAbsolute()) {
            p = Paths.get(System.getProperty("user.dir")).resolve(p).normalize();
        }
        return p;
    }

    @Test
    @WithMockUser(authorities = {"resource", "ROLE_ADMIN"})
    void uploadCommitmentLetter_shouldReturnUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "commitment.pdf",
                "application/pdf",
                "fake pdf content".getBytes()
        );

        mockMvc.perform(multipart("/api/ca-certificates/commitment-letter/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.url").value(notNullValue()));
    }

    @Test
    @WithMockUser(authorities = {"resource", "ROLE_ADMIN"})
    void uploadCommitmentLetter_imageJpeg_shouldSucceed() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "letter.jpg",
                "image/jpeg",
                "fake jpeg content".getBytes()
        );

        mockMvc.perform(multipart("/api/ca-certificates/commitment-letter/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(authorities = {"resource", "ROLE_ADMIN"})
    void uploadCommitmentLetter_imagePng_shouldSucceed() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "letter.png",
                "image/png",
                "fake png content".getBytes()
        );

        mockMvc.perform(multipart("/api/ca-certificates/commitment-letter/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(authorities = {"resource", "ROLE_ADMIN"})
    void uploadCommitmentLetter_invalidType_shouldFail() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "letter.exe",
                "application/exe",
                "fake exe content".getBytes()
        );

        mockMvc.perform(multipart("/api/ca-certificates/commitment-letter/upload")
                        .file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadCommitmentLetter_unauthenticated_shouldFail() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "commitment.pdf",
                "application/pdf",
                "fake pdf content".getBytes()
        );

        mockMvc.perform(multipart("/api/ca-certificates/commitment-letter/upload")
                        .file(file))
                .andExpect(status().isForbidden());
    }
}
