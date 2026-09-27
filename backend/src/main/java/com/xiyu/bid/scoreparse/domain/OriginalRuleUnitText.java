package com.xiyu.bid.scoreparse.domain;

import java.util.List;
import java.util.TreeMap;

/** Numbering inside a rule is not a boundary. Shared-cell boundaries require distinct located peer identities. */
public final class OriginalRuleUnitText {
    private OriginalRuleUnitText() {}
    public record Unit(String code,String name,String quote) {}
    public static String restore(String raw,String quote) {
        return EvidenceText.normalize(raw).uniqueSpan(quote).isPresent()?raw.strip():"";
    }
    public static String restore(String raw,Unit target,List<Unit> peers) {
        if(peers.size()==1) return restore(raw,target.quote());
        var boundaries=new TreeMap<Integer,Unit>();
        Integer targetStart=null;
        for(var unit:peers) {
            var quote=EvidenceText.normalize(raw).uniqueSpan(unit.quote());
            if(quote.isEmpty()) {
                if(!precedingContinuation(raw,target,unit) || boundaries.putIfAbsent(0,unit)!=null) return "";
                continue;
            }
            int start=raw.lastIndexOf('\n',Math.max(0,quote.get().start()-1))+1;
            int end=raw.indexOf('\n',quote.get().start());
            String heading=raw.substring(start,end<0?raw.length():end);
            // Identity comes from the actual sibling candidate graph, never from numbered material clauses.
            if(!identified(heading,unit) || boundaries.putIfAbsent(start,unit)!=null) return "";
            if(unit.equals(target)) targetStart=start;
        }
        if(targetStart==null) return "";
        int end=boundaries.higherKey(targetStart)==null?raw.length():boundaries.higherKey(targetStart);
        var quote=EvidenceText.normalize(raw).uniqueSpan(target.quote()).orElseThrow();
        return quote.end()<=end?raw.substring(targetStart,end).strip():"";
    }
    /** A prefix can be excluded only if another actual peer's quotation owns that entire continuation. */
    public static boolean precedingContinuation(String raw,Unit target,Unit peer) {
        var targetQuote=EvidenceText.normalize(raw).uniqueSpan(target.quote());
        if(targetQuote.isEmpty() || target.equals(peer)) return false;
        int end=raw.lastIndexOf('\n',Math.max(0,targetQuote.get().start()-1))+1;
        String prefix=raw.substring(0,end).strip();
        return !prefix.isBlank() && identified(prefix.lines().findFirst().orElse(""),peer)
                && EvidenceText.normalize(peer.quote()).uniqueSpan(prefix).isPresent();
    }
    private static boolean identified(String heading,Unit unit) {
        return unit.code()!=null && unit.code().length()>1 && heading.contains(unit.code())
                || unit.name()!=null && unit.name().length()>1 && heading.contains(unit.name());
    }

}
