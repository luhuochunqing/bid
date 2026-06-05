package com.xiyu.bid.projectworkflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.projectworkflow.parser.ScoreDraftDraftAssembler;
import com.xiyu.bid.projectworkflow.parser.ScoreDraftDocumentTextExtractor;
import com.xiyu.bid.projectworkflow.parser.ScoreDraftFileTypePolicy;
import com.xiyu.bid.projectworkflow.parser.ScoreDraftTextParser;
import com.xiyu.bid.projectworkflow.parser.WordTextExtractor;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.xiyu.bid.projectworkflow.service.ScoreDraftParserTestFixtures.buildDocx;
import static com.xiyu.bid.projectworkflow.service.ScoreDraftParserTestFixtures.buildPdf;
import static com.xiyu.bid.projectworkflow.service.ScoreDraftParserTestFixtures.buildSameLinePdf;
import static com.xiyu.bid.projectworkflow.service.ScoreDraftParserTestFixtures.buildTableDocx;
import static com.xiyu.bid.projectworkflow.service.ScoreDraftParserTestFixtures.buildXls;
import static com.xiyu.bid.projectworkflow.service.ScoreDraftParserTestFixtures.buildXlsx;

class ScoreDraftParserServiceTest {

    private final ScoreDraftParserService parserService = new ScoreDraftParserService(
            new ScoreDraftFileTypePolicy(),
            new ScoreDraftDocumentTextExtractor(new WordTextExtractor()),
            new ScoreDraftTextParser(),
            new ScoreDraftDraftAssembler(new ObjectMapper())
    );

    @Test
    void parse_ShouldRejectEmptyInput() {
        MockMultipartFile file = new MockMultipartFile("file", "评分标准.docx", "application/octet-stream", new byte[0]);

        assertThatThrownBy(() -> parserService.parse(1001L, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("请上传评分标准文件");
    }

    @Test
    void parse_ShouldReadDocxAndExtractScoreDrafts() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "评分标准.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                buildTableDocx()
        );

        List<ProjectScoreDraft> drafts = parserService.parse(1002L, file);

        assertThat(drafts).hasSize(3);
        assertThat(drafts).extracting(ProjectScoreDraft::getCategory)
                .containsExactly("business", "technical", "price");
    }

    @Test
    void parse_ShouldReadXlsxAndExtractScoreDrafts() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "评分标准.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildXlsx()
        );

        List<ProjectScoreDraft> drafts = parserService.parse(1003L, file);

        assertThat(drafts).hasSize(3);
        assertThat(drafts).extracting(ProjectScoreDraft::getCategory)
                .containsExactly("business", "technical", "price");
    }

    @Test
    void parse_ShouldReadXlsAndExtractScoreDrafts() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "评分标准.xls",
                "application/vnd.ms-excel",
                buildXls()
        );

        List<ProjectScoreDraft> drafts = parserService.parse(1004L, file);

        assertThat(drafts).hasSize(3);
        assertThat(drafts).extracting(ProjectScoreDraft::getCategory)
                .containsExactly("business", "technical", "price");
    }

    @Test
    void parse_ShouldReadTextPdfAndExtractScoreDrafts() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "评分标准.pdf",
                "application/pdf",
                buildPdf()
        );

        List<ProjectScoreDraft> drafts = parserService.parse(1005L, file);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).getCategory()).isEqualTo("business");
        assertThat(drafts.get(0).getScoreItemTitle()).isEqualTo("同类项目业绩");
    }

    @Test
    void parse_ShouldReadSameLinePdfTableAndExtractScoreDrafts() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "评分标准.pdf",
                "application/pdf",
                buildSameLinePdf()
        );

        List<ProjectScoreDraft> drafts = parserService.parse(1006L, file);

        assertThat(drafts).hasSize(3);
        assertThat(drafts).extracting(ProjectScoreDraft::getCategory)
                .containsExactly("business", "technical", "price");
        assertThat(drafts).extracting(ProjectScoreDraft::getScoreItemTitle)
                .containsExactly("同类项目业绩", "整体方案", "品类折扣率");
    }

    @Test
    void parse_ShouldRejectUnrecognizedScoreDrafts() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "评分标准.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                buildDocx("普通说明文字\n没有评分章节")
        );

        assertThatThrownBy(() -> parserService.parse(1007L, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未识别到规整评分标准");
    }
}
