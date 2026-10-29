package com.xiyu.bid.scoreparse.domain;

import java.util.List;
import java.util.TreeMap;

/** Numbering inside a rule is not a boundary. Shared-cell boundaries require distinct located peer identities. */
public final class OriginalRuleUnitText {
    private static final java.util.regex.Pattern SIBLING_CODE = java.util.regex.Pattern.compile("(?:^|[-.])(\\d+)$");
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
            int start=resolveStart(raw, unit, quote.get().start());
            int end=raw.indexOf('\n',quote.get().start());
            if (end >= 0 && EvidenceText.normalize(raw.substring(start, end)).text().length() < 2) {
                end = raw.indexOf('\n', end + 1);
            }
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
    /** Inline boundary is trusted only when the quoted sibling number matches its own graph identity. */
    private static int resolveStart(String raw, Unit unit, int quoteStart) {
        int lineStart = raw.lastIndexOf('\n', Math.max(0, quoteStart - 1)) + 1;
        var code = SIBLING_CODE.matcher(unit.code() == null ? "" : unit.code());
        if (!code.find()) return lineStart;
        String num = java.util.regex.Pattern.quote(code.group(1));
        String quote = unit.quote() == null ? "" : unit.quote().stripLeading();
        if (isSentenceEndBefore(raw, quoteStart) && quote.matches("(?s)^" + num + "[.、。．]\\s*.*")) {
            return quoteStart;
        }
        if (quoteStart > 0) {
            var prefix = java.util.regex.Pattern.compile("(?s)(" + num + "[.、。．]\\s*)$")
                    .matcher(raw.substring(0, quoteStart));
            if (prefix.find()) {
                int prefixStart = prefix.start(1);
                if (prefixStart == 0 || raw.charAt(prefixStart - 1) == '\n' || isSentenceEndBefore(raw, prefixStart)) {
                    return prefixStart;
                }
            }
        }
        return lineStart;
    }
    private static boolean isSentenceEndBefore(String raw, int pos) {
        if (pos <= 0) return false;
        int previous = pos - 1;
        while (previous >= 0 && (raw.charAt(previous) == ' ' || raw.charAt(previous) == '\t')) previous--;
        if (previous < 0) return false;
        char before = raw.charAt(previous);
        return "。；;".indexOf(before) >= 0 || ("）)".indexOf(before) >= 0
                && previous > 0 && "。！？；;".indexOf(raw.charAt(previous - 1)) >= 0);
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
        if (unit.code()!=null && unit.code().length()>1 && heading.contains(unit.code())) return true;
        if (unit.name()!=null && unit.name().length()>1 && heading.contains(unit.name())) return true;
        String firstLine = unit.quote() == null ? "" : unit.quote().lines()
                .map(String::strip)
                .filter(line -> EvidenceText.normalize(line).text().length() >= 2)
                .findFirst().orElse("");
        if (!firstLine.isBlank()) {
            String normHeading = EvidenceText.normalize(heading).text();
            String normFirst = EvidenceText.normalize(firstLine).text();
            if (!normHeading.isBlank() && normHeading.length() >= 2 && normFirst.length() >= 2) {
                if (normHeading.contains(normFirst) || normFirst.contains(normHeading)) return true;
            }
        }
        return false;
    }

}
