package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoringDocument;
import com.xiyu.bid.scoreparse.domain.ScoringSourceRef;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** File-scoped source ranges, built once and selected using existing excerpt offsets. */
final class ScoringSourceIndex {
    private record Span(ScoringSourceRef source,int start,int end) {}
    private final ScoringDocument document;
    private final List<Span> spans=new ArrayList<>();
    ScoringSourceIndex(ScoringDocument document) {
        this.document=document;
        var pages=new TreeMap<Integer,Integer>();
        var matcher=Pattern.compile("(?m)^\\[第 (\\d{1,9}) 页]$").matcher(document.text());
        while(matcher.find())pages.put(matcher.start(),Integer.parseInt(matcher.group(1)));
        for(var source:document.sources()) {
            if(source.pageNumber()!=null) {
                pages.forEach((start,page)->{
                    if(page.equals(source.pageNumber())) {
                        Integer next=pages.higherKey(start);
                        spans.add(new Span(source,start,next==null?document.text().length():next));
                    }
                });
            } else if(source.mediaName()!=null&&source.marker()!=null) {
                String marker=source.marker();int start=0;
                while((start=document.text().indexOf(marker,start))>=0) {
                    int end=start+marker.length();
                    if(source.text()!=null&&document.text().startsWith(" "+source.text(),end))end+=1+source.text().length();
                    spans.add(new Span(source,start,end));start=end;
                }
            }
        }
    }
    List<ScoringSourceRef> select(ScoreDocExcerptExtractor.Excerpt excerpt) {
        var selected=new java.util.LinkedHashSet<ScoringSourceRef>();
        for(var segment:excerpt.segments()) {
            int start=segment.sourceStartOffset(),end=start+segment.text().length();
            if(start<0||end>document.text().length()||!document.text().startsWith(segment.text(),start))continue;
            for(var span:spans)if(start<span.end&&end>span.start)selected.add(span.source);
        }
        return List.copyOf(selected);
    }
}
