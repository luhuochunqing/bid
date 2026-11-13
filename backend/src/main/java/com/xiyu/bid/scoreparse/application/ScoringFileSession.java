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
import java.util.List;
import java.util.Locale;
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
    private ScoringSourceIndex sourceIndex;
    private final java.util.LinkedHashMap<String,DocumentImage> imageCache=new java.util.LinkedHashMap<>();
    private long cachedBytes;

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
    public List<DocumentImage> imagesFor(ScoreDocExcerptExtractor.Excerpt excerpt,ImageBudget budget) {
        ScoringDeadline.check(deadline);
        if(images==null || document==null || budget.maxImagesPerItem()<=0)return List.of();
        if(sourceIndex==null)sourceIndex=new ScoringSourceIndex(document);
        var refs=sourceIndex.select(excerpt);
        Set<Integer> pages=new TreeSet<>();List<String> media=new ArrayList<>();
        for(var ref:refs) {
            if(ref.pageNumber()!=null)pages.add(ref.pageNumber());
            else if(ref.mediaName()!=null)media.add(ref.mediaName());
        }
        try {
            if(!pages.isEmpty()) {
                var selected=pages.stream().limit(budget.maxImagesPerItem()).toList();
                var resolved=new java.util.HashMap<Integer,DocumentImage>();
                Set<Integer> missing=new TreeSet<>();
                for(int page:selected) {
                    var cached=imageCache.get("page:"+page);
                    if(cached==null)missing.add(page);else resolved.put(page,cached);
                }
                if(!missing.isEmpty()) {
                    var result=file instanceof LoadedBidFile.InMemory memory
                            ?images.renderPages(memory.content(),file.fileName(),missing,deadline)
                            :images.renderPages(((LoadedBidFile.OnDisk)file).tempFile(),file.fileName(),missing,deadline);
                    for(var image:withinBudget(result.images(),budget)) {
                        if(image.pageHint()==null||!missing.contains(image.pageHint()))continue;
                        resolved.put(image.pageHint(),image);cache("page:"+image.pageHint(),image,budget);
                    }
                }
                return withinBudget(selected.stream().map(resolved::get).filter(Objects::nonNull).toList(),budget);
            }
            return readMedia(media,budget);
        } catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException("SCORING_INTERRUPTED",ex);}
        catch(IOException ex){return List.of();}
    }
    private void cache(String key,DocumentImage image,ImageBudget budget) {
        long bytes=image.base64().length();
        if(bytes>budget.maxImageBytesPerItem())return;
        var old=imageCache.remove(key);if(old!=null)cachedBytes-=old.base64().length();
        while(!imageCache.isEmpty()&&cachedBytes+bytes>budget.maxImageBytesPerItem()) {
            var eldest=imageCache.entrySet().iterator();var removed=eldest.next();
            cachedBytes-=removed.getValue().base64().length();eldest.remove();
        }
        imageCache.put(key,image);cachedBytes+=bytes;
    }
    private List<DocumentImage> readMedia(List<String> refs,ImageBudget budget)throws IOException {
        if(refs.isEmpty())return List.of();
        Set<String> names=new java.util.LinkedHashSet<>();
        for(String name:refs)if(name.startsWith("word/media/")&&!name.contains(".."))names.add(name);
        var result=new ArrayList<DocumentImage>();
        for(String name:names)if(imageCache.containsKey(name))result.add(imageCache.get(name));
        result=new ArrayList<>(withinBudget(result,budget));
        names.removeAll(imageCache.keySet());
        if(names.isEmpty()||result.size()>=budget.maxImagesPerItem())return List.copyOf(result);
        try(InputStream source=file instanceof LoadedBidFile.InMemory memory?new ByteArrayInputStream(memory.content()):Files.newInputStream(((LoadedBidFile.OnDisk)file).tempFile());
            var zip=new ZipInputStream(source)) {
            ZipEntry entry;long bytes=result.stream().mapToLong(image->image.base64().length()).sum();
            while((entry=zip.getNextEntry())!=null && result.size()<budget.maxImagesPerItem()) {
                ScoringDeadline.check(deadline);
                if(!names.contains(entry.getName()))continue;
                long left=Math.min((budget.maxImageBytesPerItem()-bytes)/4*3,8L*1024*1024);
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
                        var image=new DocumentImage(result.size(),"image/"+("jpg".equals(format)?"jpeg":format),entry.getName(),null,w,h,Base64.getEncoder().encodeToString(data));
                        result.add(image);cache(entry.getName(),image,budget);bytes+=image.base64().length();
                    } finally {reader.dispose();}
                }
            }
        }
        return withinBudget(result,budget);
    }
    private List<DocumentImage> withinBudget(List<DocumentImage> source,ImageBudget budget) {
        var result=new ArrayList<DocumentImage>();long bytes=0;
        for(var image:source) {
            long length=image.base64().length();
            if(result.size()>=budget.maxImagesPerItem() || bytes+length>budget.maxImageBytesPerItem())break;
            if((long)image.width()*image.height()>40_000_000)continue;
            result.add(image);bytes+=length;
        }
        return List.copyOf(result);
    }
    @Override public void close(){if(!closed){closed=true;imageCache.clear();cachedBytes=0;sourceIndex=null;release.run();}}
}
