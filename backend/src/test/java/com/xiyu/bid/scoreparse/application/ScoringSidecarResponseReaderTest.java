package com.xiyu.bid.scoreparse.application;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class ScoringSidecarResponseReaderTest {
    private String report(int version,String engine) {
        return "{\"markdown\":\"尾页证书ABC999\",\"pages\":[{\"pageNumber\":41}],\"extraction\":{"
                +"\"schemaVersion\":"+version+",\"engineVersion\":\""+engine+"\",\"coverageStatus\":\"COMPLETE\","
                +"\"enumerationComplete\":true,\"sourceUnitCount\":41,\"processedUnitCount\":41,\"uniqueOcrUnitCount\":41,"
                +"\"unreadUnitCount\":0,\"failedUnitCount\":0,\"emptyUnitCount\":0}}";
    }
    private InputStream body(String value){return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));}
    @Test void keepsTailPageAndAccurateCounts()throws IOException {
        var document=new ScoringSidecarResponseReader().read(body(report(1,"scoring-ocr-v2")),100,System.currentTimeMillis()+5000);
        assertThat(document.text()).contains("ABC999");
        assertThat(document.extraction().sourceUnitCount()).isEqualTo(41);
        assertThat(document.sourceRefs()).contains(java.util.Map.of("pageNumber",41));
    }
    @Test void readsActualPythonNativeDocxResponseWithEnumerationAndAdditionalFields()throws Exception {
        try(var input=getClass().getResourceAsStream("/scoreparse/scoring-sidecar-v2-contract.json")) {
            var document=new ScoringSidecarResponseReader().read(input,1000,System.currentTimeMillis()+5000);
            assertThat(document.text()).isEqualTo("NATIVE CONTRACT VALUE 72000000");
            assertThat(document.extraction().coverageStatus()).isEqualTo("COMPLETE");
            assertThat(document.extraction().enumerationComplete()).isTrue();
            assertThat(document.sourceRefs().getFirst()).containsEntry("partName","word/document.xml").containsEntry("anchor","p1").containsEntry("text","NATIVE CONTRACT VALUE 72000000").containsEntry("kind","TEXT");
        }
    }
    @Test void unsupportedOrMissingVersionNeverClaimsComplete(){
        assertThatThrownBy(()->new ScoringSidecarResponseReader().read(body(report(2,"scoring-ocr-v2")),100,System.currentTimeMillis()+5000))
                .isInstanceOf(IOException.class).hasMessageContaining("INVALID_SCORING_COVERAGE");
        assertThatThrownBy(()->new ScoringSidecarResponseReader().read(body(report(1,"")),100,System.currentTimeMillis()+5000))
                .isInstanceOf(IOException.class);
    }
    @Test void incompleteEnumerationCannotClaimCompleteEvenWithZeroUnreadCount(){
        assertThatThrownBy(()->new ScoringSidecarResponseReader().read(body(report(1,"scoring-ocr-v2")
                .replace("\"enumerationComplete\":true","\"enumerationComplete\":false")),100,System.currentTimeMillis()+5000))
                .isInstanceOf(IOException.class);
    }
    @Test void textBoundaryIsEnforcedBeforePublishingDocument(){
        assertThatThrownBy(()->new ScoringSidecarResponseReader().read(body(report(1,"scoring-ocr-v2")),3,System.currentTimeMillis()+5000))
                .isInstanceOf(OversizedBidTextException.class);
    }
}
