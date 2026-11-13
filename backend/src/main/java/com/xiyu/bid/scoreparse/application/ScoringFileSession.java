package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.DocumentImage;
import com.xiyu.bid.scoreparse.domain.ImageBudget;
import com.xiyu.bid.scoreparse.domain.ScoringDocument;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;

/** One downloaded file owns the entire scoring lifetime; extraction is lazy, after reuse checks. */
public final class ScoringFileSession implements AutoCloseable {
    private final Long documentId;
    private final String contentHash;
    private final LoadedBidFile file;
    private final SidecarStreamTextExtractor texts;
    private final SidecarImageExtractor images;
    private final com.xiyu.bid.biddraftagent.application.TenderDocumentTextExtractor nativeTexts;
    private final long deadline;
    private final Runnable release;
    private ScoringDocument document;
    private boolean closed;

    ScoringFileSession(Long id,String hash,LoadedBidFile file,SidecarStreamTextExtractor texts,
                       SidecarImageExtractor images,long deadline,Runnable release) {
        this(id,hash,file,texts,images,deadline,release,null);
    }
    ScoringFileSession(Long id,String hash,LoadedBidFile file,SidecarStreamTextExtractor texts,
            SidecarImageExtractor images,long deadline,Runnable release,
            com.xiyu.bid.biddraftagent.application.TenderDocumentTextExtractor nativeTexts) {
        this.documentId=id;this.contentHash=hash;this.file=file;this.texts=texts;
        this.images=images;this.deadline=deadline;this.release=release;this.nativeTexts=nativeTexts;
    }
    public Long documentId(){return documentId;}
    public String contentHash(){return contentHash;}
    public ScoringDocument extract() {
        ScoringDeadline.check(deadline);
        if(document!=null)return document;
        String name=Objects.toString(file.fileName(),"").toLowerCase(Locale.ROOT);
        if(!name.endsWith(".pdf")&&!name.endsWith(".docx")&&!"application/pdf".equalsIgnoreCase(file.contentType())
                &&!"application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(file.contentType())) {
            document=NativeScoringDocumentExtractor.extract(file,nativeTexts,deadline);return document;
        }
        try {
            document=file instanceof LoadedBidFile.InMemory memory
                    ? texts.extractScoringDocument(memory.content(),file.fileName(),deadline)
                    : texts.extractScoringDocument(((LoadedBidFile.OnDisk)file).tempFile(),file.fileName(),deadline);
            return document;
        } catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException("SCORING_INTERRUPTED",ex);}
        catch(IOException ex){throw new IllegalStateException("SCORING_EXTRACTION_FAILED",ex);}
    }
    public List<DocumentImage> imagesFor(String excerpt,ImageBudget budget) {
        ScoringDeadline.check(deadline);
        if(images==null || document==null || budget.maxImagesPerItem()<=0)return List.of();
        Set<Integer> pages=new TreeSet<>();List<Map<String,Object>> media=new ArrayList<>();
        var excerptPages=pagesContainingExcerpt(excerpt);
        var excerptRanges=uniqueExcerptRanges(excerpt);
        for(var ref:document.sourceRefs()) {
            Object page=ref.get("pageNumber");Object anchor=ref.get("anchor");
            if(page instanceof Number n && (excerpt.contains("[第 "+n.intValue()+" 页]")
                    || excerptPages.contains(n.intValue())))pages.add(n.intValue());
            else if(anchor!=null && ref.get("partName")!=null && ref.get("relationshipId")!=null
                    && ref.get("mediaName")!=null) {
                String marker="["+ref.get("partName")+"#"+anchor+" "+ref.get("relationshipId")+"]";
                if(excerpt.contains(marker) || imageSourceOverlaps(ref,excerptRanges))media.add(ref);
            }
        }
        try {
            if(!pages.isEmpty()) {
                var selected=pages.stream().limit(budget.maxImagesPerItem()).collect(java.util.stream.Collectors.toSet());
                var result=file instanceof LoadedBidFile.InMemory memory
                        ? images.renderPages(memory.content(),file.fileName(),selected,deadline)
                        : images.renderPages(((LoadedBidFile.OnDisk)file).tempFile(),file.fileName(),selected,deadline);
                return withinBudget(result.images(),budget);
            }
            return readMedia(media,budget);
        } catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException("SCORING_INTERRUPTED",ex);}
        catch(IOException ex){return List.of();}
    }
    private List<int[]> uniqueExcerptRanges(String excerpt) {
        String text=document.text();var ranges=new ArrayList<int[]>();
        for(String segment:excerpt.split("\n\n---\n\n")) {
            segment=segment.strip();if(segment.isEmpty())continue;
            int start=text.indexOf(segment);
            if(start>=0 && text.indexOf(segment,start+1)<0)ranges.add(new int[]{start,start+segment.length()});
        }
        return ranges;
    }
    private boolean imageSourceOverlaps(Map<String,Object> ref,List<int[]> ranges) {
        String marker="["+ref.get("partName")+"#"+ref.get("anchor")+" "+ref.get("relationshipId")+"]";
        int markerStart=document.text().indexOf(marker);
        if(markerStart<0 || document.text().indexOf(marker,markerStart+1)>=0)return false;
        if(!(ref.get("text") instanceof String sourceText) || sourceText.isBlank())return false;
        int sourceStart=document.text().indexOf(sourceText,markerStart+marker.length());
        if(sourceStart<0 || sourceStart-markerStart>marker.length()+1)return false;
        int sourceEnd=sourceStart+sourceText.length();
        return ranges.stream().anyMatch(range->range[0]<sourceEnd && range[1]>sourceStart);
    }
    private Set<Integer> pagesContainingExcerpt(String excerpt) {
        String text=document.text();
        var markers=new java.util.TreeMap<Integer,Integer>();
        var matcher=java.util.regex.Pattern.compile("(?m)^\\[第 (\\d{1,9}) 页]$").matcher(text);
        while(matcher.find())markers.put(matcher.start(),Integer.parseInt(matcher.group(1)));
        Set<Integer> pages=new HashSet<>();
        for(String segment:excerpt.split("\n\n---\n\n")) {
            segment=segment.strip();
            if(segment.isEmpty())continue;
            int start=text.indexOf(segment);
            // A repeated excerpt cannot identify one physical page reliably.
            if(start<0 || text.indexOf(segment,start+1)>=0)continue;
            var page=markers.floorEntry(start);
            var end=markers.higherKey(start);
            if(page!=null && (end==null || start+segment.length()<=end))pages.add(page.getValue());
        }
        return pages;
    }
    private List<DocumentImage> readMedia(List<Map<String,Object>> refs,ImageBudget budget)throws IOException {
        if(refs.isEmpty())return List.of();
        Set<String> names=new HashSet<>();
        for(var ref:refs) {
            String name=ref.get("mediaName").toString();
            if(name.startsWith("word/media/")&&!name.contains(".."))names.add(name);
        }
        var result=new ArrayList<DocumentImage>();
        try(InputStream source=file instanceof LoadedBidFile.InMemory memory?new ByteArrayInputStream(memory.content()):Files.newInputStream(((LoadedBidFile.OnDisk)file).tempFile());
            var zip=new ZipInputStream(source)) {
            ZipEntry entry;long bytes=0;
            while((entry=zip.getNextEntry())!=null && result.size()<budget.maxImagesPerItem()) {
                ScoringDeadline.check(deadline);
                if(!names.contains(entry.getName()))continue;
                long left=Math.min(budget.maxImageBytesPerItem()-bytes,8L*1024*1024);
                if(left<=0)break;
                byte[] data=zip.readNBytes((int)left+1);
                if(data.length>left)continue;
                try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
                    var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())continue;
                    var reader=readers.next();
                    try {
                        reader.setInput(input,true,true);int w=reader.getWidth(0),h=reader.getHeight(0);
                        if(w<=0||h<=0||(long)w*h>40_000_000)continue;
                        String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                        if(!Set.of("png","jpeg","jpg","gif","webp").contains(format))continue;
                        result.add(new DocumentImage(result.size(),"image/"+("jpg".equals(format)?"jpeg":format),entry.getName(),null,w,h,Base64.getEncoder().encodeToString(data)));
                        bytes+=data.length;
                    } finally {reader.dispose();}
                }
            }
        }
        return withinBudget(result,budget);
    }
    private List<DocumentImage> withinBudget(List<DocumentImage> source,ImageBudget budget) {
        var result=new ArrayList<DocumentImage>();long bytes=0;
        for(var image:source) {
            long length=image.base64().length()*3L/4;
            if(result.size()>=budget.maxImagesPerItem() || bytes+length>budget.maxImageBytesPerItem())break;
            if((long)image.width()*image.height()>40_000_000)continue;
            result.add(image);bytes+=length;
        }
        return List.copyOf(result);
    }
    @Override public void close(){if(!closed){closed=true;release.run();}}
}
