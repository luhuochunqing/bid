package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiJsonObjectPayloadReaderTest {

    private final OpenAiJsonObjectPayloadReader reader = new OpenAiJsonObjectPayloadReader(new ObjectMapper());

    @Test
    void read_shouldParsePlainJsonObject() throws Exception {
        DraftPayload payload = reader.read("""
                {"draftText":"draft","reviewSummary":"review","handoffChecklist":"checklist"}
                """, DraftPayload.class);

        assertThat(payload.draftText).isEqualTo("draft");
        assertThat(payload.reviewSummary).isEqualTo("review");
        assertThat(payload.handoffChecklist).isEqualTo("checklist");
    }

    @Test
    void read_shouldParseMarkdownFencedJsonObject() throws Exception {
        DraftPayload payload = reader.read("""
                ```json
                {"draftText":"draft","reviewSummary":"review","handoffChecklist":"checklist"}
                ```
                """, DraftPayload.class);

        assertThat(payload.draftText).isEqualTo("draft");
        assertThat(payload.reviewSummary).isEqualTo("review");
        assertThat(payload.handoffChecklist).isEqualTo("checklist");
    }

    @Test
    void read_shouldParseDoubleEncodedJsonObject() throws Exception {
        DraftPayload payload = reader.read("""
                "{\\"draftText\\":\\"draft\\",\\"reviewSummary\\":\\"review\\",\\"handoffChecklist\\":\\"checklist\\"}"
                """, DraftPayload.class);

        assertThat(payload.draftText).isEqualTo("draft");
        assertThat(payload.reviewSummary).isEqualTo("review");
        assertThat(payload.handoffChecklist).isEqualTo("checklist");
    }

    @Test
    void read_shouldExtractObjectFromPrefixedPayload() throws Exception {
        DraftPayload payload = reader.read("""
                Here is your JSON:
                {"draftText":"draft with {braces}","reviewSummary":"review","handoffChecklist":"checklist"}
                End.
                """, DraftPayload.class);

        assertThat(payload.draftText).isEqualTo("draft with {braces}");
        assertThat(payload.reviewSummary).isEqualTo("review");
        assertThat(payload.handoffChecklist).isEqualTo("checklist");
    }

    @Test
    void read_shouldAcceptUnescapedControlCharactersViaLenientMapper() throws Exception {
        DraftPayload payload = reader.read("{\"draftText\":\"line1\nline2\",\"reviewSummary\":\"review\",\"handoffChecklist\":\"checklist\"}",
                DraftPayload.class);

        assertThat(payload.draftText).isEqualTo("line1\nline2");
        assertThat(payload.reviewSummary).isEqualTo("review");
        assertThat(payload.handoffChecklist).isEqualTo("checklist");
    }

    @Test
    void read_shouldNormalizeObjectListsAndItemsAliasForTenderRequirements() throws Exception {
        TenderRequirementOutput payload = reader.read("""
                {
                  "projectName":"项目A",
                  "requiredMaterials":{"primary":"营业执照","secondary":"授权书"},
                  "items":{
                    "first":{"category":"qualification","title":"资质","content":"提供营业执照","mandatory":true,"sourceExcerpt":"营业执照","confidence":95}
                  }
                }
                """, TenderRequirementOutput.class);

        assertThat(payload.requiredMaterials).containsExactly("营业执照", "授权书");
        assertThat(payload.requirementItems).hasSize(1);
        assertThat(payload.requirementItems.get(0).content).isEqualTo("提供营业执照");
    }

    @Test
    void read_shouldFlattenObjectArraysIntoStringLists() throws Exception {
        TenderRequirementOutput payload = reader.read("""
                {
                  "projectName":"项目A",
                  "qualificationRequirements":[
                    {"content":"营业执照"},
                    {"title":"授权书","description":"加盖公章"}
                  ]
                }
                """, TenderRequirementOutput.class);

        assertThat(payload.qualificationRequirements).containsExactly("营业执照", "授权书 / 加盖公章");
    }

    @Test
    void read_shouldIgnoreMetadataOnlyObjectsWhenNormalizingReadableLists() throws Exception {
        TenderRequirementOutput payload = reader.read("""
                {
                  "projectName":"项目A",
                  "qualificationRequirements":[
                    "供应商须为在中华人民共和国境内注册的企业法人。",
                    {
                      "category":"qualification",
                      "mandatory":true,
                      "sourceExcerpt":"未明确提及，根据通用要求推断",
                      "confidence":50
                    }
                  ],
                  "requiredMaterials":[
                    {
                      "title":"法定代表人身份证复印件（正反面）",
                      "mandatory":true,
                      "confidence":100
                    },
                    {
                      "primary":"授权书",
                      "secondary":"营业执照"
                    }
                  ]
                }
                """, TenderRequirementOutput.class);

        assertThat(payload.qualificationRequirements)
                .containsExactly("供应商须为在中华人民共和国境内注册的企业法人。");
        assertThat(payload.requiredMaterials)
                .containsExactly("法定代表人身份证复印件（正反面）", "授权书", "营业执照");
    }

    @Test
    void read_shouldNormalizeArrayIntoStringField() throws Exception {
        DraftPayload payload = reader.read("""
                {
                  "draftText":"draft",
                  "reviewSummary":["第一条","第二条"],
                  "handoffChecklist":{"first":"补充报价","second":"补充法务确认"}
                }
                """, DraftPayload.class);

        assertThat(payload.reviewSummary).isEqualTo("第一条\n第二条");
        assertThat(payload.handoffChecklist).isEqualTo("补充报价\n补充法务确认");
    }

    static final class DraftPayload {
        public String draftText;
        public String reviewSummary;
        public String handoffChecklist;
    }
}
