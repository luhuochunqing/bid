package com.xiyu.bid.docinsight.application;

import com.xiyu.bid.docinsight.application.exception.DocumentNotFoundException;
import com.xiyu.bid.docinsight.application.exception.UnsupportedProfileException;
import com.xiyu.bid.docinsight.domain.DocumentChunk;
import com.xiyu.bid.docinsight.domain.StructuralDocumentChunker;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DocumentIntelligenceServiceImpl")
class DocumentIntelligenceServiceImplTest {

    @Mock private DocumentStorage storage;
    @Mock private DocumentTextExtractor extractor;
    @Mock private StructuralDocumentChunker chunker;
    @Mock private DocumentAnalyzer analyzer;
    @Mock private ProjectAccessScopeService projectAccessScopeService;
    @Mock private MultipartFile file;

    private DocumentIntelligenceServiceImpl service;

    private static final String PROFILE_NON_BOUND = "REPORT";
    private static final String PROFILE_TENDER = "TENDER";
    private static final String PROFILE_TENDER_INTAKE = "TENDER_INTAKE";
    private static final String ENTITY_ID = "42";
    private static final String STORAGE_PATH = "/tmp/uploads/doc.pdf";
    private static final byte[] CONTENT = "pdf-bytes".getBytes();
    private static final String FILE_URL = "doc-insight://REPORT/42/abc-doc.pdf";

    @BeforeEach
    void setUp() throws IOException {
        service = new DocumentIntelligenceServiceImpl(
                storage, extractor, chunker, List.of(analyzer), projectAccessScopeService
        );

        // Default stubs
        when(file.getBytes()).thenReturn(CONTENT);
        when(file.getOriginalFilename()).thenReturn("doc.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        when(analyzer.supports(PROFILE_NON_BOUND)).thenReturn(true);
        when(analyzer.supports(PROFILE_TENDER)).thenReturn(true);
        when(analyzer.supports(PROFILE_TENDER_INTAKE)).thenReturn(true);

        ExtractedDocument extracted = new ExtractedDocument("full text", 9, null, "test", Map.of());
        when(extractor.extract(anyString(), anyString(), any())).thenReturn(extracted);

        List<DocumentChunk> chunks = List.of(new DocumentChunk("chunk", List.of()));
        when(chunker.chunk(anyString(), any())).thenReturn(chunks);

        DocumentAnalysisResult mockResult = new DocumentAnalysisResult(
                FILE_URL, Map.of(), List.of(), null, List.of()
        );
        when(analyzer.analyze(any())).thenReturn(mockResult);
    }

    // ── process() happy path ──────────────────────────────────────────────────

    @Test
    @DisplayName("process() 成功时应存储文件并返回分析结果")
    void process_happyPath_shouldStoreAndReturnResult() {
        StoredDocument stored = new StoredDocument(FILE_URL, STORAGE_PATH, "sha256abc");
        when(storage.store(any(), any(), any(), any(), any())).thenReturn(stored);

        DocumentAnalysisResult result = service.process(PROFILE_NON_BOUND, ENTITY_ID, file);

        assertThat(result).isNotNull();
        assertThat(result.documentId()).isEqualTo(FILE_URL);
        verify(storage).store(eq(PROFILE_NON_BOUND), eq(ENTITY_ID), any(), any(), eq(CONTENT));
    }

    // ── processExisting() happy path ──────────────────────────────────────────

    @Test
    @DisplayName("processExisting() documentId 应与 lookup() 返回的真实 fileUrl 一致")
    void processExisting_happyPath_documentIdMatchesRealFileUrl() {
        String realFileUrl = "doc-insight://REPORT/42/abc-doc.pdf";
        StoredDocument stored = new StoredDocument(realFileUrl, STORAGE_PATH, "realhash");
        when(storage.load(STORAGE_PATH)).thenReturn(Optional.of(CONTENT));
        when(storage.lookup(STORAGE_PATH)).thenReturn(Optional.of(stored));
        when(analyzer.analyze(any())).thenReturn(
                new DocumentAnalysisResult(realFileUrl, Map.of(), List.of(), null, List.of())
        );

        DocumentAnalysisResult result = service.processExisting(
                PROFILE_NON_BOUND, ENTITY_ID, STORAGE_PATH, "doc.pdf", "application/pdf"
        );

        assertThat(result.documentId()).isEqualTo(realFileUrl);
        assertThat(result.documentId()).doesNotStartWith("doc-insight://existing");
    }

    // ── processExisting() – file not found ───────────────────────────────────

    @Test
    @DisplayName("processExisting() 存储路径不存在时抛出 DocumentNotFoundException")
    void processExisting_fileNotFound_throwsDocumentNotFoundException() {
        when(storage.load(STORAGE_PATH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.processExisting(
                PROFILE_NON_BOUND, ENTITY_ID, STORAGE_PATH, "doc.pdf", "application/pdf"
        ))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining(STORAGE_PATH);
    }

    // ── parse() – unsupported profile ─────────────────────────────────────────

    @Test
    @DisplayName("parse() 没有 analyzer 支持 profileCode 时抛出 UnsupportedProfileException")
    void parse_unsupportedProfile_throwsUnsupportedProfileException() throws IOException {
        String unknownProfile = "UNKNOWN_PROFILE";
        when(file.getBytes()).thenReturn(CONTENT);
        StoredDocument stored = new StoredDocument(FILE_URL, STORAGE_PATH, "hash");
        when(storage.store(any(), any(), any(), any(), any())).thenReturn(stored);
        // analyzer does NOT support this profile
        when(analyzer.supports(unknownProfile)).thenReturn(false);

        assertThatThrownBy(() -> service.process(unknownProfile, ENTITY_ID, file))
                .isInstanceOf(UnsupportedProfileException.class)
                .hasMessageContaining(unknownProfile);
    }

    // ── TENDER profile – access scope guard ───────────────────────────────────

    @Test
    @DisplayName("process() TENDER 配置应调用 assertCurrentUserCanAccessProject(42)")
    void process_tenderProfile_invokesAccessScopeCheck() {
        StoredDocument stored = new StoredDocument(FILE_URL, STORAGE_PATH, "hash");
        when(storage.store(any(), any(), any(), any(), any())).thenReturn(stored);

        service.process(PROFILE_TENDER, ENTITY_ID, file);

        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(42L);
    }

    @Test
    @DisplayName("process() TENDER + 无权限时 AccessDeniedException 向上抛出")
    void process_tenderProfile_accessDenied_propagatesException() {
        doThrow(new AccessDeniedException("权限不足，无法访问该项目"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(42L);

        assertThatThrownBy(() -> service.process(PROFILE_TENDER, ENTITY_ID, file))
                .isInstanceOf(AccessDeniedException.class);

        verify(storage, never()).store(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("process() 非项目型配置不调用 assertCurrentUserCanAccessProject")
    void process_nonProjectProfile_skipsAccessScopeCheck() {
        StoredDocument stored = new StoredDocument(FILE_URL, STORAGE_PATH, "hash");
        when(storage.store(any(), any(), any(), any(), any())).thenReturn(stored);

        service.process(PROFILE_NON_BOUND, ENTITY_ID, file);

        verify(projectAccessScopeService, never()).assertCurrentUserCanAccessProject(any());
    }

    @Test
    @DisplayName("process() TENDER_INTAKE 是入库前解析，不校验项目访问范围")
    void process_tenderIntakeProfile_skipsAccessScopeCheckAndAllowsNonProjectEntityId() {
        StoredDocument stored = new StoredDocument(FILE_URL, STORAGE_PATH, "hash");
        when(storage.store(any(), any(), any(), any(), any())).thenReturn(stored);

        service.process(PROFILE_TENDER_INTAKE, "intake-task-001", file);

        verify(projectAccessScopeService, never()).assertCurrentUserCanAccessProject(any());
        verify(storage).store(eq(PROFILE_TENDER_INTAKE), eq("intake-task-001"), any(), any(), eq(CONTENT));
    }
}
