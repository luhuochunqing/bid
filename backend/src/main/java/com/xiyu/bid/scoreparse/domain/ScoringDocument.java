package com.xiyu.bid.scoreparse.domain;

import java.util.List;
import java.util.Map;

/** Pure extraction data; file ownership stays in the application scope. */
public final class ScoringDocument {
    private final String text;
    private final List<Map<String,Object>> sourceRefs;
    private final List<ScoringSourceRef> sources;
    private final ScoringExtractionReport extraction;
    public ScoringDocument(String text,List<Map<String,Object>> sourceRefs,ScoringExtractionReport extraction) {
        this.text=text;
        this.sourceRefs=sourceRefs==null?List.of():sourceRefs.stream().map(Map::copyOf).toList();
        this.sources=this.sourceRefs.stream().map(ScoringSourceRef::from).toList();
        this.extraction=extraction==null?ScoringExtractionReport.unknown():extraction;
    }
    public String text(){return text;}
    public List<Map<String,Object>> sourceRefs(){return sourceRefs;}
    public List<ScoringSourceRef> sources(){return sources;}
    public ScoringExtractionReport extraction(){return extraction;}
    public boolean verifiedQuoteSource(String quote) {
        if(quote==null||quote.isBlank())return false;
        int position=text.indexOf(quote);
        if(position<0||text.indexOf(quote,position+1)>=0)return false;
        for(var ref:sources) {
            boolean bound=false;
            if(ref.pageNumber()!=null) {
                int marker=text.lastIndexOf("[第 ",position);
                bound=marker>=0 && text.startsWith(ref.marker(),marker);
            } else if("TEXT".equals(ref.kind())) {
                bound=ref.text()!=null && ref.text().contains(quote);
            } else if(ref.partName()!=null&&ref.anchor()!=null&&ref.relationshipId()!=null) {
                String marker=ref.marker();
                int start=text.lastIndexOf(marker,position);
                int next=start<0?-1:text.indexOf("[word/",start+marker.length());
                bound=start>=0&&(next<0||next>position)&&ref.text()!=null&&ref.text().contains(quote);
            }
            if(bound && verifiedSource(ref))return true;
        }
        return false;
    }
    /** The complete unique Markdown row must correspond to consecutive native cells in one Word part. */
    public boolean verifiedNativeTableRow(String quote,List<String> cells) {
        if(quote==null||quote.isBlank()||quote.contains("\n")||"UNKNOWN".equals(extraction.coverageStatus()))return false;
        int position=text.indexOf(quote);
        if(position<0||text.indexOf(quote,position+1)>=0)return false;
        int start=text.lastIndexOf('\n',position)+1,end=text.indexOf('\n',position+quote.length());
        if(end<0)end=text.length();
        if(!text.substring(start,end).strip().equals(quote.strip()))return false;
        int matches=0;
        for(int i=0;i+cells.size()<=sources.size();i++) {
            var first=sources.get(i);Object part=first.partName();
            String anchor=java.util.Objects.toString(first.anchor(),"");
            if(!(part instanceof String name)||!name.startsWith("word/")||!anchor.matches("p[1-9][0-9]{0,8}"))continue;
            int number=Integer.parseInt(anchor.substring(1));boolean bound=true;
            for(int cell=0;cell<cells.size()&&bound;cell++) {
                var ref=sources.get(i+cell);
                bound="TEXT".equals(ref.kind())&&part.equals(ref.partName())
                        &&("p"+(number+cell)).equals(ref.anchor())
                        &&ref.text()!=null&&cells.get(cell).equals(ref.text().strip())
                        &&verifiedSource(ref);
            }
            if(bound)matches++;
        }
        return matches==1;
    }
    private boolean verifiedSource(ScoringSourceRef ref) {
        return java.util.stream.Stream.of(extraction.unreadUnits(),extraction.failedUnits(),extraction.emptyUnits())
                .flatMap(List::stream).map(ScoringSourceRef::from).noneMatch(ref::sameUnit);
    }
}
