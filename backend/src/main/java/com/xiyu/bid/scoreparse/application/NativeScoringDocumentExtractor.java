package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.application.TenderDocumentTextExtractor;
import com.xiyu.bid.scoreparse.domain.ScoringDocument;
import com.xiyu.bid.scoreparse.domain.ScoringExtractionReport;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.hwpf.HWPFDocument;

/** Preserve the existing native DOC extractor; independently enumerate its source before trusting coverage. */
final class NativeScoringDocumentExtractor {
    static ScoringDocument extract(LoadedBidFile file,TenderDocumentTextExtractor delegate,long deadline) {
        if(!(file instanceof LoadedBidFile.InMemory memory) || delegate==null)
            throw new IllegalStateException("SCORING_FORMAT_REQUIRES_PDF_OR_DOCX");
        ScoringDeadline.check(deadline);
        var extracted=delegate.extract(file.fileName(),file.contentType(),memory.content());
        String text=extracted==null?null:extracted.text();
        ScoringDeadline.check(deadline);
        if(text==null||normalize(text).isBlank())throw new IllegalStateException("SCORING_NATIVE_TEXT_UNAVAILABLE_CONVERT_SCANNED_DOC");
        if(text.length()>5_000_000)throw new OversizedBidTextException();
        boolean doc="application/msword".equalsIgnoreCase(file.contentType())
                || file.fileName()!=null && file.fileName().toLowerCase(Locale.ROOT).endsWith(".doc");
        if(!doc)return new ScoringDocument(text,List.of(),ScoringExtractionReport.unknown());
        try(var word=new HWPFDocument(new ByteArrayInputStream(memory.content()))) {
            var range=word.getOverallRange();
            List<Map<String,Object>> refs=new ArrayList<>(),unread=new ArrayList<>();long processed=0;
            String normalized=normalize(text);
            for(int i=0;i<range.numParagraphs();i++) {
                ScoringDeadline.check(deadline);
                String paragraph=normalize(range.getParagraph(i).text());
                if(paragraph.isBlank())continue;
                var ref=Map.<String,Object>of("partName","WordDocument","anchor","p"+i);
                refs.add(ref);
                if(normalized.contains(paragraph))processed++;else unread.add(ref);
            }
            int images=word.getPicturesTable().getAllPictures().size();
            for(int i=0;i<images;i++) {
                var ref=Map.<String,Object>of("partName","WordDocument","anchor","image"+i,"reason","NATIVE_DOC_IMAGE_OCR_UNSUPPORTED");
                refs.add(ref);unread.add(ref);
            }
            return new ScoringDocument(text,refs,new ScoringExtractionReport(1,"native-doc-coverage-v1",
                    unread.isEmpty()?"COMPLETE":"PARTIAL",refs.size(),processed,0,unread.size(),0,0,unread,List.of(),List.of(),0));
        }catch(IOException ex){throw new IllegalStateException("SCORING_NATIVE_DOC_COVERAGE_UNAVAILABLE",ex);}
    }
    private static String normalize(String value){return value.replaceAll("[\\p{Cntrl}\\s]+","");}
}
