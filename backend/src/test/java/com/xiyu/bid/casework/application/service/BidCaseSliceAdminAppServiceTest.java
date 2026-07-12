package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.application.BidCaseSliceImportCommand;
import com.xiyu.bid.casework.domain.model.BidCaseSliceAdminStat;
import com.xiyu.bid.casework.domain.model.BidCaseSliceAdminView;
import com.xiyu.bid.casework.infrastructure.BidCaseSlice;
import com.xiyu.bid.casework.infrastructure.BidCaseSliceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BidCaseSliceAdminAppService - 管理端切片应用服务")
class BidCaseSliceAdminAppServiceTest {

    private BidCaseSliceRepository repository;
    private BidCaseSliceAdminAppService service;

    @BeforeEach
    void setUp() {
        repository = mock(BidCaseSliceRepository.class);
        service = new BidCaseSliceAdminAppService(repository);
    }

    @Nested
    @DisplayName("importSingleSlice")
    class ImportSingleSlice {

        @Test
        @DisplayName("正常导入：字段正确映射到实体并返回视图")
        void shouldMapAllFieldsToEntityAndReturnView() {
            var command = new BidCaseSliceImportCommand(
                    "project-001", "doc/abc.docx", "技术标", "第一章 概述",
                    "正文预览…", 1, 2, 3, 500, 10
            );

            var saved = new BidCaseSlice();
            saved.setId(42L);
            saved.setProjectDir("project-001");
            saved.setDocxFile("doc/abc.docx");
            saved.setDocxLabel("技术标");
            saved.setSectionIdx(2);
            saved.setLevel(3);
            saved.setTitle("第一章 概述");
            saved.setTextPreview("正文预览…");
            saved.setTextLength(500);
            saved.setParaCount(10);
            saved.setCreatedAt(LocalDateTime.now());

            when(repository.save(any(BidCaseSlice.class))).thenReturn(saved);

            BidCaseSliceAdminView view = service.importSingleSlice(command);

            assertThat(view.sliceId()).isEqualTo(42L);
            assertThat(view.projectDir()).isEqualTo("project-001");
            assertThat(view.docxFile()).isEqualTo("doc/abc.docx");
            assertThat(view.docxLabel()).isEqualTo("技术标");
            assertThat(view.title()).isEqualTo("第一章 概述");
            assertThat(view.textPreview()).isEqualTo("正文预览…");
            assertThat(view.sectionIdx()).isEqualTo(2);
            assertThat(view.level()).isEqualTo(3);
            assertThat(view.textLength()).isEqualTo(500);
            assertThat(view.paraCount()).isEqualTo(10);

            ArgumentCaptor<BidCaseSlice> captor = ArgumentCaptor.forClass(BidCaseSlice.class);
            verify(repository).save(captor.capture());
            BidCaseSlice entity = captor.getValue();
            assertThat(entity.getProjectDir()).isEqualTo("project-001");
            assertThat(entity.getDocxLabel()).isEqualTo("技术标");
            assertThat(entity.getLevel()).isEqualTo(3);
        }

        @Test
        @DisplayName("project 为空时抛出 IllegalArgumentException")
        void shouldThrowWhenProjectIsBlank() {
            var command = new BidCaseSliceImportCommand(
                    null, null, null, "标题", null, null, null, null, null, null
            );
            assertThatThrownBy(() -> service.importSingleSlice(command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("project");
        }

        @Test
        @DisplayName("title 为空时抛出 IllegalArgumentException")
        void shouldThrowWhenTitleIsBlank() {
            var command = new BidCaseSliceImportCommand(
                    "project-001", null, null, "  ", null, null, null, null, null, null
            );
            assertThatThrownBy(() -> service.importSingleSlice(command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("title");
        }

        @Test
        @DisplayName("可选字段为 null 时使用默认值")
        void shouldApplyDefaultsForNullOptionalFields() {
            var command = new BidCaseSliceImportCommand(
                    "project-001", null, null, "标题", null, null, null, null, null, null
            );

            var saved = new BidCaseSlice();
            saved.setId(1L);
            saved.setProjectDir("project-001");
            saved.setTitle("标题");
            when(repository.save(any(BidCaseSlice.class))).thenReturn(saved);

            service.importSingleSlice(command);

            ArgumentCaptor<BidCaseSlice> captor = ArgumentCaptor.forClass(BidCaseSlice.class);
            verify(repository).save(captor.capture());
            BidCaseSlice entity = captor.getValue();
            assertThat(entity.getDocxFile()).isEqualTo("");
            assertThat(entity.getDocxLabel()).isEqualTo("其他");
            assertThat(entity.getProjectIdx()).isZero();
            assertThat(entity.getSectionIdx()).isZero();
            assertThat(entity.getLevel()).isEqualTo(1);
            assertThat(entity.getTextPreview()).isEqualTo("");
            assertThat(entity.getTextLength()).isZero();
            assertThat(entity.getParaCount()).isZero();
        }
    }

    @Nested
    @DisplayName("getStats")
    class GetStats {

        @Test
        @DisplayName("返回正确的统计快照")
        void shouldReturnCorrectStats() {
            when(repository.count()).thenReturn(100L);
            when(repository.countByEmbeddingIsNotNull()).thenReturn(70L);

            BidCaseSliceAdminStat stat = service.getStats();

            assertThat(stat.total()).isEqualTo(100L);
            assertThat(stat.withEmbedding()).isEqualTo(70L);
            assertThat(stat.withoutEmbedding()).isEqualTo(30L);
        }
    }

    @Nested
    @DisplayName("deleteSlice")
    class DeleteSlice {

        @Test
        @DisplayName("切片不存在时抛出 IllegalArgumentException")
        void shouldThrowWhenSliceDoesNotExist() {
            when(repository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> service.deleteSlice(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("切片不存在");
        }

        @Test
        @DisplayName("切片存在时正常删除")
        void shouldDeleteWhenSliceExists() {
            when(repository.existsById(1L)).thenReturn(true);

            service.deleteSlice(1L);

            verify(repository).deleteById(1L);
        }
    }
}
