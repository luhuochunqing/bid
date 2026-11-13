package com.xiyu.bid.scoreparse.domain;

import java.util.List;
import java.util.Map;

/** Pure extraction data; file ownership stays in the application scope. */
public record ScoringDocument(String text, List<Map<String,Object>> sourceRefs, ScoringExtractionReport extraction) {
    public ScoringDocument {
        sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
        extraction = extraction == null ? ScoringExtractionReport.unknown() : extraction;
    }    public boolean verifiedQuoteSource(String quote) {
        if(quote==null||quote.isBlank())return false;
        int position=text.indexOf(quote);
        if(position<0||text.indexOf(quote,position+1)>=0)return false;
        for(var ref:sourceRefs) {
            boolean bound=false;
            if(ref.get("pageNumber")!=null) {
                int marker=text.lastIndexOf("[第 ",position);
                bound=marker>=0 && text.startsWith("[第 "+ref.get("pageNumber")+" 页]",marker);
            } else if("TEXT".equals(ref.get("kind"))) {
                bound=ref.get("text") instanceof String unit && unit.contains(quote);
            } else if(ref.get("partName")!=null&&ref.get("anchor")!=null&&ref.get("relationshipId")!=null) {
                String marker="["+ref.get("partName")+"#"+ref.get("anchor")+" "+ref.get("relationshipId")+"]";
                int start=text.lastIndexOf(marker,position);
                int next=start<0?-1:text.indexOf("[word/",start+marker.length());
                bound=start>=0&&(next<0||next>position)&&ref.get("text") instanceof String unit&&unit.contains(quote);
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
        for(int i=0;i+cells.size()<=sourceRefs.size();i++) {
            var first=sourceRefs.get(i);Object part=first.get("partName");
            String anchor=java.util.Objects.toString(first.get("anchor"),"");
            if(!(part instanceof String name)||!name.startsWith("word/")||!anchor.matches("p[1-9][0-9]{0,8}"))continue;
            int number=Integer.parseInt(anchor.substring(1));boolean bound=true;
            for(int cell=0;cell<cells.size()&&bound;cell++) {
                var ref=sourceRefs.get(i+cell);
                bound="TEXT".equals(ref.get("kind"))&&part.equals(ref.get("partName"))
                        &&("p"+(number+cell)).equals(ref.get("anchor"))
                        &&ref.get("text") instanceof String value&&cells.get(cell).equals(value.strip())
                        &&verifiedSource(ref);
            }
            if(bound)matches++;
        }
        return matches==1;
    }
    private boolean verifiedSource(Map<String,Object> ref) {
        return java.util.stream.Stream.of(extraction.unreadUnits(),extraction.failedUnits(),extraction.emptyUnits())
                .flatMap(List::stream).noneMatch(bad->sameSource(ref,bad));
    }
    private static boolean sameSource(Map<String,Object> a,Map<String,Object> b) {
        if(a.get("pageNumber")!=null)return java.util.Objects.toString(a.get("pageNumber"),"").equals(java.util.Objects.toString(b.get("pageNumber"),""));
        return a.get("partName")!=null&&java.util.Objects.equals(a.get("partName"),b.get("partName"))
                &&java.util.Objects.equals(a.get("anchor"),b.get("anchor"))
                &&java.util.Objects.equals(a.get("relationshipId"),b.get("relationshipId"));
    }

}
