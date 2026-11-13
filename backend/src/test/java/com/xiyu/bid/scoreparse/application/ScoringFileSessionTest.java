package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScoringFileSessionTest {
    private static ScoreDocExcerptExtractor.Excerpt selection(String text,int offset) {
        return new ScoreDocExcerptExtractor.Excerpt(text,List.of(new ScoreDocExcerptExtractor.Segment("selected",text,offset)));
    }
    @Test void pageCacheEvictsWithinByteBudgetAndRendersAgainAfterEviction() throws Exception {
        String body="[第 1 页]\nONE\n\n[第 2 页]\nTWO";
        var texts=mock(SidecarStreamTextExtractor.class);var images=mock(SidecarImageExtractor.class);
        when(texts.extractScoringDocument(any(byte[].class),anyString(),anyLong())).thenReturn(
                new ScoringDocument(body,List.of(Map.of("pageNumber",1),Map.of("pageNumber",2)),null));
        var one=new DocumentImage(0,"image/png",null,1,2,2,"AAAAAAAA");
        var two=new DocumentImage(0,"image/png",null,2,2,2,"BBBBBBBB");
        when(images.renderPages(any(byte[].class),anyString(),eq(Set.of(1)),anyLong()))
                .thenReturn(new SidecarImageExtraction(List.of(one),false));
        when(images.renderPages(any(byte[].class),anyString(),eq(Set.of(2)),anyLong()))
                .thenReturn(new SidecarImageExtraction(List.of(two),false));
        try(var file=new ScoringFileSession(1L,"hash",new LoadedBidFile.InMemory(new byte[]{1},"bid.pdf","pdf"),
                texts,images,System.currentTimeMillis()+10000,()->{})) {
            file.extract();var budget=new ImageBudget(1,8);
            assertThat(file.imagesFor(selection("ONE",body.indexOf("ONE")),budget)).containsExactly(one);
            assertThat(file.imagesFor(selection("TWO",body.indexOf("TWO")),budget)).containsExactly(two);
            assertThat(file.imagesFor(selection("ONE",body.indexOf("ONE")),budget)).containsExactly(one);
        }
        verify(images,times(2)).renderPages(any(byte[].class),anyString(),eq(Set.of(1)),anyLong());
        verify(images).renderPages(any(byte[].class),anyString(),eq(Set.of(2)),anyLong());
    }
    @Test void longWordOcrExcerptWithoutMarkerLoadsOnlyOverlappingSourceMedia() throws Exception {
        String marker = "[word/document.xml#p7 rId9]";
        String ocr = "ordinary OCR text ".repeat(220) + "CERTIFICATE ABC999 " + "tail OCR text ".repeat(220);
        String otherMarker = "[word/header1.xml#p7 rId9]";
        String otherOcr = "UNRELATED HEADER IMAGE";
        String body = "INTRODUCTION\n\n" + marker + " " + ocr + "\n\n" + otherMarker + " " + otherOcr;
        var refs = List.<Map<String, Object>>of(
                Map.of("partName", "word/document.xml", "anchor", "p7", "relationshipId", "rId9",
                        "mediaName", "word/media/cert.png", "kind", "IMAGE", "text", ocr),
                Map.of("partName", "word/header1.xml", "anchor", "p7", "relationshipId", "rId9",
                        "mediaName", "word/media/header.png", "kind", "IMAGE", "text", otherOcr));
        var coverage = new ScoringExtractionReport(1, "fixture", "COMPLETE", 2, 2, 2, 0, 0, 0,
                List.of(), List.of(), List.of(), 0);
        var text = mock(SidecarStreamTextExtractor.class);
        when(text.extractScoringDocument(any(byte[].class), anyString(), anyLong()))
                .thenReturn(new ScoringDocument(body, refs, coverage));
        var png = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png);
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (String name : List.of("word/media/cert.png", "word/media/header.png")) {
                zip.putNextEntry(new ZipEntry(name)); zip.write(png.toByteArray()); zip.closeEntry();
            }
        }
        String excerpt = "CERTIFICATE ABC999 " + "tail OCR text ".repeat(5);
        assertThat(body).contains(excerpt);
        assertThat(excerpt).doesNotContain(marker, otherMarker);
        try (var file = new ScoringFileSession(1L, "hash", new LoadedBidFile.InMemory(bytes.toByteArray(), "bid.docx", "docx"),
                text, mock(SidecarImageExtractor.class), System.currentTimeMillis() + 10000, () -> {})) {
            file.extract();
            assertThat(file.imagesFor(selection(excerpt,body.indexOf(excerpt)), new ImageBudget(3, 1024)))
                    .singleElement().satisfies(image -> {
                        assertThat(image.section()).isEqualTo("word/media/cert.png");
                        assertThat(image.base64()).isEqualTo(Base64.getEncoder().encodeToString(png.toByteArray()));
                    });
            // Repeated OCR fragments do not identify a unique source position.
            assertThat(file.imagesFor(selection("ordinary OCR text",-1), new ImageBudget(3, 1024))).isEmpty();
            assertThat(file.imagesFor(selection("INTRODUCTION",0), new ImageBudget(3, 1024))).isEmpty();
        }
    }

    @Test void latePdfPageRegionKeepsSupplementalImageAssociation() throws Exception {
        var text=mock(SidecarStreamTextExtractor.class);
        var images=mock(SidecarImageExtractor.class);
        String body="[第 1 页]\nINTRODUCTION\n\n[第 17 页]\n"+"ordinary text ".repeat(260)
                +"CERTIFICATE ABC999 "+"tail text ".repeat(260)+"\n\n[第 18 页]\nAPPENDIX";
        var coverage=new ScoringExtractionReport(1,"v2","COMPLETE",3,3,3,0,0,0,List.of(),List.of(),List.of(),0);
        var refs=List.<Map<String,Object>>of(Map.of("pageNumber",1),Map.of("pageNumber",17),Map.of("pageNumber",18));
        when(text.extractScoringDocument(any(byte[].class),anyString(),anyLong()))
                .thenReturn(new ScoringDocument(body,refs,coverage));
        var image=new DocumentImage(0,"image/png",null,17,2,2,"AAAA");
        byte[] bytes={1};long deadline=System.currentTimeMillis()+10000;
        when(images.renderPages(same(bytes),eq("bid.pdf"),eq(Set.of(17)),eq(deadline)))
                .thenReturn(new SidecarImageExtraction(List.of(image),false));
        var excerpt=ScoreDocExcerptExtractor.extractRelevantExcerptWithSegments(body,"ABC999","CERTIFICATE ABC999",2200);
        assertThat(excerpt.text()).contains("CERTIFICATE ABC999").doesNotContain("[第 17 页]");
        assertThat(excerpt.segments().stream().filter(s->s.text().contains("ABC999")).findFirst().orElseThrow()
                .sourceStartOffset()).isGreaterThan(3000);
        try(var file=new ScoringFileSession(1L,"hash",new LoadedBidFile.InMemory(bytes,"bid.pdf","application/pdf"),
                text,images,deadline,()->{})) {
            file.extract();
            assertThat(file.imagesFor(excerpt,new ImageBudget(3,1024))).containsExactly(image);
            assertThat(file.imagesFor(excerpt,new ImageBudget(3,1024))).containsExactly(image);
        }
        verify(images).renderPages(same(bytes),eq("bid.pdf"),eq(Set.of(17)),eq(deadline));
        verifyNoMoreInteractions(images);
    }
    @Test void wordSupplementMatchesPartAndWholeAnchorRatherThanP1Prefix() throws Exception {
        var text=mock(SidecarStreamTextExtractor.class);
        var coverage=new ScoringExtractionReport(1,"v2","COMPLETE",2,2,2,0,0,0,List.of(),List.of(),List.of(),0);
        var refs=List.<Map<String,Object>>of(
                Map.of("partName","word/document.xml","anchor","p1","relationshipId","rId1","mediaName","word/media/missing.png"),
                Map.of("partName","word/header1.xml","anchor","p10","relationshipId","rId1","mediaName","word/media/missing2.png"));
        when(text.extractScoringDocument(any(byte[].class),anyString(),anyLong())).thenReturn(new ScoringDocument("text",refs,coverage));
        var png=new ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",png);
        var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/media/missing.png"));zip.write(png.toByteArray());zip.closeEntry();
        }
        var releases=new java.util.concurrent.atomic.AtomicInteger();
        try(var file=new ScoringFileSession(1L,"hash",new LoadedBidFile.InMemory(bytes.toByteArray(),"bid.docx","docx"),
                text,mock(SidecarImageExtractor.class),System.currentTimeMillis()+10000,releases::incrementAndGet)) {
            file.extract();
            // Both unrelated markers must result in no media decode (corrupt selected bytes would otherwise be read).
            assertThat(file.imagesFor(selection("[word/document.xml#p10 rId1]",0),new ImageBudget(3,1024))).isEmpty();
            assertThat(file.imagesFor(selection("[word/header1.xml#p1 rId1]",0),new ImageBudget(3,1024))).isEmpty();
        }
        assertThat(releases).hasValue(1);
    }
}
