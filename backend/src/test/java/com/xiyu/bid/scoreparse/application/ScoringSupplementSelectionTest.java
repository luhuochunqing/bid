package com.xiyu.bid.scoreparse.application;
import com.xiyu.bid.biddraftagent.infrastructure.openai.*;
import com.xiyu.bid.scoreparse.domain.*;
import com.xiyu.bid.scoreparse.entity.*;
import com.xiyu.bid.scoreparse.infrastructure.openai.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ScoringSupplementSelectionTest {
    @Test void relevantSourcesComeFirstWithStableTiesAndNoDuplicatePages() {
        String body="[第 1 页]\n企业简介\n\n[第 2 页]\n设备照片\n\n[第 818 页]\n免费配送承诺";
        var document=new ScoringDocument(body,List.of(Map.of("pageNumber",1),Map.of("pageNumber",2),Map.of("pageNumber",818)),
                new ScoringExtractionReport(1,"synthetic","COMPLETE",3,3,3,0,0,0,List.of(),List.of(),List.of(),0));
        var segment=new ScoreDocExcerptExtractor.Segment("all",body,0);
        var excerpt=new ScoreDocExcerptExtractor.Excerpt(body,List.of(segment,segment));
        assertThat(new ScoringSourceIndex(document).select(excerpt,"免费配送",null))
                .extracting(ScoringSourceRef::pageNumber).containsExactly(818,1,2);
    }

    @Test void lateRelevantCommitmentPageIsNotDroppedForEarlierNeighborPages() throws Exception {
        String body="[第 1 页]\n企业简介\n\n[第 2 页]\n设备照片\n\n[第 3 页]\n仓库照片\n\n[第 818 页]\n最小起订量：0元起送，不受订单金额、配送范围限制全部免运费配送。";
        var refs=List.<Map<String,Object>>of(Map.of("pageNumber",1),Map.of("pageNumber",2),Map.of("pageNumber",3),Map.of("pageNumber",818));
        var document=new ScoringDocument(body,refs,new ScoringExtractionReport(1,"synthetic","COMPLETE",4,4,4,0,0,0,List.of(),List.of(),List.of(),0));
        var text=mock(SidecarStreamTextExtractor.class);var images=mock(SidecarImageExtractor.class);
        when(text.extractScoringDocument(any(byte[].class),anyString(),anyLong())).thenReturn(document);
        when(images.renderPages(any(byte[].class),anyString(),anySet(),anyLong())).thenReturn(new SidecarImageExtraction(List.of(),false));
        var analyzer=new OpenAiScoreAnalyzer(null,null,null) {
            @Override public ScoreAssessmentOutput assessObjective(String detail,BigDecimal weight,String excerpt,List<DocumentImage> supplied,OpenAiRequestBudget budget) {
                budget.acquire(Duration.ofSeconds(1));var out=new ScoreAssessmentOutput();out.actualScore=0d;out.quote=null;return out;
            }
        };
        long deadline=System.currentTimeMillis()+10000;
        try(var file=new ScoringFileSession(1L,"synthetic",new LoadedBidFile.InMemory(new byte[]{1},"synthetic.pdf","pdf"),text,images,deadline,()->{})) {
            file.extract();
            new ScoreItemAssessor(analyzer,new ImageBudget(3,4096)).assessScoring(ScoreParseTask.builder().id(1L).build(),
                ScoreItem.builder().id(2L).dim("免费配送最小起订额度").detail("不受订单金额、配送范围限制全部免运费配送，得10分").weight(BigDecimal.TEN).scoreType("OBJECTIVE").build(),
                document,file,deadline,new OpenAiBidAgentRequestConfig("synthetic","http://provider.invalid","synthetic",Duration.ofSeconds(1),OpenAiBidAgentApiStyle.CHAT_COMPLETIONS));
        }
        org.mockito.ArgumentCaptor<Set<Integer>> pages=org.mockito.ArgumentCaptor.captor();
        verify(images).renderPages(any(byte[].class),anyString(),pages.capture(),anyLong());
        assertThat(pages.getValue()).contains(818).hasSize(3);
    }
}
