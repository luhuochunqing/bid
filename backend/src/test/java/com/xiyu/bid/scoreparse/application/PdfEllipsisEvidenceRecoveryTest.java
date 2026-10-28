package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PdfEllipsisEvidenceRecoveryTest {
    private static final String PREFIX = "供应商须提供近三年完整业绩以及完整可靠的相关合同材料证明";
    private static final String SUFFIX = "每增加一家得一分本项最高五分并提供相应凭证";
    private static final String RULE = PREFIX + "，中间内容要求核对合同、发票和证明材料。" + SUFFIX + "。";
    @Test void restoresOnlyUniqueOrderedFragmentsFromCitedRule() {
        var art=artifact(false); var result=TextPrimaryEvidenceBinder.bind(List.of(fact(PREFIX+"..."+SUFFIX)),art).getFirst();
        assertThat(result.quotedText()).isEqualTo(RULE);
        var validation=new MultimodalScoreFactValidator().validate(List.of(result),art,BigDecimal.valueOf(5));
        assertThat(TextPrimaryScoreFactMapper.map(result,art,validation).sourceText()).isEqualTo(RULE);
    }
    @Test void rejectsDuplicateSourceRows() {
        var f=fact(PREFIX+"..."+SUFFIX); var result=TextPrimaryEvidenceBinder.bind(List.of(f),artifact(true)).getFirst();
        assertThat(result.quotedText()).isEqualTo(f.quotedText());
    }
    @Test void rejectsShortOrChangedFragments() {
        var f=fact("供应商须提供...每增加一家得一分");var result=TextPrimaryEvidenceBinder.bind(List.of(f),artifact(false)).getFirst();
        assertThat(result.quotedText()).isEqualTo(f.quotedText());
    }
    private static MultimodalScoreFact fact(String quote) {
        return new MultimodalScoreFact("第四章 评审办法","7.1","7","详细评审",BigDecimal.valueOf(5),"","OBJECTIVE",14,"t",List.of("t-r1-c2"),quote,.9);
    }
    private static DocumentArtifact artifact(boolean duplicate) {
        var cells=new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        String[][] rows={{"条款号","评审因素","评审标准","分值"},{"7","客户经验（5分）",RULE,"5"}};
        for(int r=0;r<rows.length;r++)for(int c=0;c<4;c++)cells.add(cell(r,c,rows[r][c]));
        if(duplicate)for(int c=0;c<4;c++)cells.add(cell(2,c,rows[1][c]));
        var t=new DocumentArtifact.CanonicalTable("t",13,"第四章 评审办法",duplicate?3:2,4,List.of(),cells,true,"PDF");
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc","hash","PDF","2.2"),List.of(t),List.of());
    }
    private static DocumentArtifact.CanonicalTable.CanonicalCellDto cell(int r,int c,String x){
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-r"+r+"-c"+c,r,c,x,x,1,1,false,"pdf:page=14",14,"t-r"+r);
    }
}
