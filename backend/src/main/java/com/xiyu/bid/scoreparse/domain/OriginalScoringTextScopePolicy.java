package com.xiyu.bid.scoreparse.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves text ranges inside, and after, named scoring schemes. */
final class OriginalScoringTextScopePolicy {
    private static final Pattern HEADING = Pattern.compile("^\\s*#{1,6}\\s*(.+?)\\s*$");
    private static final Pattern SOURCE_HEADING = Pattern.compile("^(第[一二三四五六七八九十百0-9]+章\\s*.+|[一二三四五六七八九十]+[、.]\\s*.+|\\([^)]{1,24}\\)\\s*.+)$");
    /**
     * Trailer of a dot-leader table-of-contents line (dot leaders plus a trailing page number).
     * The page number is optional on purpose: any dot-leader-terminated line is refused as a
     * heading, preferring a missing chapter anchor (manual review) over a TOC line becoming a
     * scheme root, which would make the whole document ambiguous.
     *
     * <p>This is the form {@link OriginalScoringSchemePolicy} strips before comparing two titles,
     * so it deliberately stays narrower than {@link #TOC_ENTRY}: only dot leaders are removed.
     */
    static final Pattern TOC_TRAILER = Pattern.compile("(?:[.…·•‥∙．⋯⋮⋱․‧]\\s*){2,}\\d{0,4}\\s*$");
    /**
     * A line that is a table-of-contents entry: a dot-leader trailer, or a title with a page
     * number to its right and no leaders at all — a shape at least as common as dot leaders.
     * Refusing such a line costs the chapter anchor (that chapter falls back to manual review);
     * accepting it buys a scheme root built from the table of contents, which makes every fact
     * in the document ambiguous. The bias is therefore always towards refusal.
     *
     * <p>Built from {@link #TOC_TRAILER} rather than repeating its literal: the two must hold
     * {@code TOC_ENTRY ⊇ TOC_TRAILER}. If the skipping rule missed a form that the stripping rule
     * removes, a TOC line could become a heading while still comparing equal to the body title —
     * exactly the ambiguity this class exists to prevent. Concatenation makes that structural.
     */
    private static final Pattern TOC_ENTRY = Pattern.compile(
            TOC_TRAILER.pattern() + "|\\s\\d{1,3}\\s*$");

    private OriginalScoringTextScopePolicy() {}

    static List<OriginalScoringContext.TextScope> textScopes(String text) {
        if (text == null || text.isBlank()) return List.of(new OriginalScoringContext.TextScope(null, "", "", "", 0, 0, ""));
        String[] lines = text.split("\\R", -1);
        List<Heading> headings = headings(lines);
        List<Heading> roots = new ArrayList<>();
        int activeRootLevel = Integer.MAX_VALUE;
        for (Heading heading : headings) {
            if (heading.level() <= activeRootLevel) activeRootLevel = Integer.MAX_VALUE;
            if (OriginalScoringSchemePolicy.isValidSchemeRoot(heading.title()) && activeRootLevel == Integer.MAX_VALUE) {
                boolean sameRoot = !roots.isEmpty()
                        && OriginalScoringSchemePolicy.sameScheme(roots.get(roots.size() - 1).title(), heading.title());
                if (!sameRoot) roots.add(heading);
                // A chapter-name list in prose must not displace its later explicit body heading.
                else if (lines[heading.line()].stripLeading().startsWith("#")
                        && !lines[roots.getLast().line()].stripLeading().startsWith("#"))
                    roots.set(roots.size() - 1, heading);
                activeRootLevel = heading.level();
            }
        }
        if (roots.isEmpty()) return List.of(new OriginalScoringContext.TextScope(null, "", "", "", 0, lines.length, text));

        int firstRootLine = roots.get(0).line();
        String preambleTotals = java.util.stream.IntStream.range(0, firstRootLine)
                .filter(i -> lines[i].matches("(?s).*(?:总分|满分).*\\d+(?:\\.\\d+)?\\s*分.*"))
                .mapToObj(i -> lines[i]).collect(java.util.stream.Collectors.joining("\n"));
        List<OriginalScoringContext.TextScope> scopes = new ArrayList<>();
        for (int index = 0; index < roots.size(); index++) {
            Heading root = roots.get(index);
            int nextRootLine = index + 1 < roots.size() ? roots.get(index + 1).line() : lines.length;
            int rootEnd = Math.min(nextSchemeBoundary(headings, root, lines.length), nextRootLine);
            List<Heading> audits = headings.stream().filter(h -> h.line() > root.line() && h.line() < rootEnd
                    && h.level() > root.level() && OriginalScoringSchemePolicy.isContractAuditTitle(h.title())).toList();
            int cursor = root.line();
            for (Heading audit : audits) {
                int auditEnd = nextBoundaryBefore(headings, audit, rootEnd);
                if (cursor < audit.line()) addScope(scopes, lines, root.title(), root.title(), "IN_SCHEME",
                        cursor, audit.line(), cursor == root.line() ? preambleTotals : "");
                addScope(scopes, lines, audit.title(), root.title(), "OUT_OF_SCHEME", audit.line(), auditEnd, "");
                cursor = Math.max(cursor, auditEnd);
            }
            if (cursor < rootEnd) addScope(scopes, lines, root.title(), root.title(), "IN_SCHEME",
                    cursor, rootEnd, cursor == root.line() ? preambleTotals : "");
            if (rootEnd < nextRootLine) {
                Heading outside = headings.stream().filter(h -> h.line() == rootEnd).findFirst().orElse(null);
                String title = outside == null ? "评分方案外文档内容" : outside.title();
                addScope(scopes, lines, title, root.title(), "OUT_OF_SCHEME", rootEnd, nextRootLine, "");
            }
        }
        for (Heading heading : headings) {
            if (!OriginalScoringSchemePolicy.isContractAuditTitle(heading.title())) continue;
            boolean alreadyScoped = scopes.stream().anyMatch(scope -> heading.line() >= scope.startLine()
                    && heading.line() < scope.endLine() && "OUT_OF_SCHEME".equals(scope.scopeStatus()));
            if (!alreadyScoped) addScope(scopes, lines, heading.title(), roots.get(0).title(), "OUT_OF_SCHEME",
                    heading.line(), nextBoundary(headings, heading, lines.length), "");
        }
        scopes.sort(java.util.Comparator.comparingInt(OriginalScoringContext.TextScope::startLine));
        return List.copyOf(scopes);
    }

    static OriginalScoringContext.TextScope textScopeAt(String text, int line) {
        return textScopes(text).stream().filter(scope -> line >= scope.startLine() && line < scope.endLine())
                .findFirst().orElse(null);
    }

    static String textWithinScopes(String text, List<OriginalScoringContext.TextScope> scopes) {
        if (text == null || text.isBlank()) return "";
        String[] lines = text.split("\\R", -1);
        boolean[] included = new boolean[lines.length];
        scopes.forEach(scope -> {
            int start = Math.max(0, scope.startLine());
            int end = Math.min(lines.length, scope.endLine());
            for (int line = start; line < end; line++) included[line] = true;
        });
        for (int line = 0; line < lines.length; line++) if (!included[line]) lines[line] = "";
        return String.join("\n", lines);
    }

    static List<Heading> headings(String[] lines) {
        List<Heading> result = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String title = stripEmphasis(lines[i]).trim();
            Matcher matcher = HEADING.matcher(title);
            int level;
            if (matcher.matches()) {
                String prefix = title.stripLeading();
                level = 0;
                while (level < prefix.length() && prefix.charAt(level) == '#') level++;
                title = matcher.group(1).trim();
            } else if (SOURCE_HEADING.matcher(title).matches()) {
                level = title.startsWith("第") ? 1 : 2;
            } else continue;
            if (TOC_ENTRY.matcher(title).find()) continue;
            result.add(new Heading(i, level, title));
        }
        return result;
    }

    private static int nextBoundary(List<Heading> headings, Heading current, int fallback) {
        return headings.stream().filter(next -> next.line() > current.line() && next.level() <= current.level())
                .mapToInt(Heading::line).min().orElse(fallback);
    }

    private static int nextSchemeBoundary(List<Heading> headings, Heading current, int fallback) {
        return headings.stream().filter(next -> next.line() > current.line() && next.level() <= current.level())
                .filter(next -> !OriginalScoringSchemePolicy.isValidSchemeRoot(next.title())
                        || !OriginalScoringSchemePolicy.sameScheme(next.title(), current.title()))
                .mapToInt(Heading::line).min().orElse(fallback);
    }

    private static int nextBoundaryBefore(List<Heading> headings, Heading current, int fallback) {
        return headings.stream().filter(next -> next.line() > current.line() && next.line() < fallback
                        && next.level() <= current.level())
                .mapToInt(Heading::line).min().orElse(fallback);
    }

    private static void addScope(List<OriginalScoringContext.TextScope> scopes, String[] lines, String title,
                                 String schemeTitle, String status, int start, int end, String prefix) {
        String body = String.join("\n", java.util.Arrays.copyOfRange(lines, start, end));
        String content = prefix == null || prefix.isBlank() ? body : prefix + "\n" + body;
        scopes.add(new OriginalScoringContext.TextScope(OriginalScoringSchemePolicy.schemeId(schemeTitle), title,
                status, "text:line[" + start + "]", start, end, content));
    }

    record Heading(int line, int level, String title) {}

    static String stripEmphasis(String value) {
        return value == null ? "" : value.replaceAll("\\*{1,2}([^*]+)\\*{1,2}", "$1")
                .replaceAll("_{1,2}([^_]+)_{1,2}", "$1").trim();
    }
}
