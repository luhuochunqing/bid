package com.xiyu.bid.projectworkflow.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreDraftDraftAssemblerTest {

    private final ScoreDraftDraftAssembler assembler = new ScoreDraftDraftAssembler(new ObjectMapper());

    @Test
    void assemble_ShouldMapSeedsToProjectScoreDrafts() {
        List<ParsedSection> sections = List.of(
                new ParsedSection("business", 0, List.of(
                        new DraftSeed(
                                "资质",
                                "提供资质证书",
                                "6分",
                                "准备",
                                "准备资质（6分）",
                                "描述",
                                List.of("资质证书复印件", "有效期说明")
                        ),
                        new DraftSeed(
                                "业绩",
                                "提供业绩合同",
                                "2分",
                                "整理",
                                "整理业绩（2分）",
                                "描述2",
                                List.of("合同关键页")
                        )
                ))
        );

        var drafts = assembler.assemble(1001L, "评分标准.docx", sections);

        assertThat(drafts).hasSize(2);
        assertThat(drafts.get(0).getProjectId()).isEqualTo(1001L);
        assertThat(drafts.get(0).getCategory()).isEqualTo("business");
        assertThat(drafts.get(0).getSourceTableIndex()).isEqualTo(0);
        assertThat(drafts.get(0).getSourceRowIndex()).isEqualTo(0);
        assertThat(drafts.get(1).getSourceRowIndex()).isEqualTo(1);
        assertThat(drafts.get(0).getSuggestedDeliverables()).contains("资质证书复印件");
    }
}
