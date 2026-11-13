package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.application.*;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.assertj.core.api.Assertions.*;

class NativeScoringDocumentExtractorTest {
    private final TenderDocumentTextExtractor nativePoi=(name,type,bytes)->{
        try(var word=new HWPFDocument(new ByteArrayInputStream(bytes));var extractor=new WordExtractor(word)) {
            String text=extractor.getText();return new ExtractedTenderDocument(name,type,text,text.length(),"poi-doc");
        }catch(IOException ex){throw new UncheckedIOException(ex);}
    };
    private byte[] resource(String name)throws IOException {
        try(var input=getClass().getResourceAsStream("/scoreparse/"+name)){return input.readAllBytes();}
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "bid.doc,application/octet-stream", "bid.DOC,application/msword",
            "投标文件,application/msword", "投标文件,APPLICATION/MSWORD"
    })
    void nativeDocKeepsPoiTextAndOnlyClaimsEnumeratedCoverage(String name,String contentType)throws Exception {
        var document=NativeScoringDocumentExtractor.extract(new LoadedBidFile.InMemory(resource("native-scoring-text.doc"),name,contentType),
                nativePoi,System.currentTimeMillis()+5000);
        assertThat(document.text()).contains("ABC999").contains("Second paragraph");
        assertThat(document.extraction().coverageStatus()).isEqualTo("COMPLETE");
        assertThat(document.extraction().sourceUnitCount()).isGreaterThan(0);
    }
    @Test void scannedNativeDocDoesNotPretendToHaveReadableText()throws Exception {
        assertThatThrownBy(()->NativeScoringDocumentExtractor.extract(new LoadedBidFile.InMemory(resource("native-scoring-image.doc"),"scan.doc","application/msword"),
                nativePoi,System.currentTimeMillis()+5000)).hasMessageContaining("CONVERT_SCANNED_DOC");
    }
}
