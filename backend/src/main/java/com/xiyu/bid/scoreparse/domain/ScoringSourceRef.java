package com.xiyu.bid.scoreparse.domain;

import java.util.Map;
import java.util.Objects;

/** Typed view of the scoring wire source; location formatting has one owner. */
public record ScoringSourceRef(Integer pageNumber, String partName, String anchor,
        String relationshipId, String mediaName, String text, String kind) {
    public static ScoringSourceRef from(Map<String, Object> wire) {
        Object page=wire.get("pageNumber");
        return new ScoringSourceRef(page instanceof Number n?n.intValue():null,
                value(wire,"partName"),value(wire,"anchor"),value(wire,"relationshipId"),
                value(wire,"mediaName"),value(wire,"text"),value(wire,"kind"));
    }
    private static String value(Map<String,Object> wire,String key) {
        return Objects.toString(wire.get(key),null);
    }
    public String marker() {
        if(pageNumber!=null)return "[第 "+pageNumber+" 页]";
        return partName!=null&&anchor!=null&&relationshipId!=null
                ?"["+partName+"#"+anchor+" "+relationshipId+"]":null;
    }
    public boolean sameUnit(ScoringSourceRef other) {
        if(pageNumber!=null)return pageNumber.equals(other.pageNumber);
        return partName!=null&&partName.equals(other.partName)
                &&Objects.equals(anchor,other.anchor)&&Objects.equals(relationshipId,other.relationshipId);
    }
}
