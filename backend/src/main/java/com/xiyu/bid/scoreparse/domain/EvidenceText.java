package com.xiyu.bid.scoreparse.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Layout-only normalization with reversible UTF-16 source offsets. No spelling correction. */
public record EvidenceText(String text, List<Integer> offsets) {
    public EvidenceText { offsets = List.copyOf(offsets); }
    public record Span(int start, int end) {}

    public static EvidenceText normalize(String raw) {
        if (raw == null) return new EvidenceText("", List.of());
        StringBuilder out = new StringBuilder();
        List<Integer> offsets = new ArrayList<>();
        int pending = -1;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (i + 1 < raw.length() && (c == '*' || c == '_') && raw.charAt(i + 1) == c) {
                i++;
                continue;
            }
            if (c == '\\' && i + 1 < raw.length()) {
                char next = raw.charAt(i + 1);
                // Markdown escapes a literal multiplication symbol; keep its physical source offset.
                if (next == '*') {
                    int runStart = i;
                    while (runStart > 0 && raw.charAt(runStart - 1) == '\\') runStart--;
                    if ((i - runStart) % 2 == 0) { c = next; i++; }
                }
                if (next == 'n' || next == 'r' || next == 't') {
                    if (pending < 0) pending = i;
                    i++;
                    continue;
                }
            }
            // A period between Chinese characters is a sentence-stop glyph variant, never a decimal point.
            if (c == '.' && i > 0 && i + 1 < raw.length() && han(raw.charAt(i - 1)) && han(raw.charAt(i + 1))) c = '。';
            if ((c == '。' || c == '．') && i > 0 && Character.isDigit(raw.charAt(i - 1)) && nextNonWhitespaceIsHan(raw, i + 1)) c = '.';
            c = comparisonOperator(c);
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                if (pending < 0) pending = i;
                continue;
            }
            if (pending >= 0 && !out.isEmpty()
                    && !noSpace(out.charAt(out.length() - 1), c)) {
                out.append(' ');
                offsets.add(pending);
            }
            pending = -1;
            out.append(c);
            offsets.add(i);
        }
        return new EvidenceText(out.toString(), offsets);
    }

    public Optional<Span> uniqueSpan(String quote) {
        String target = normalize(quote).text();
        if (target.isBlank()) return Optional.empty();
        int start = text.indexOf(target);
        if (start < 0 || text.indexOf(target, start + 1) >= 0) return Optional.empty();
        Integer[] originalOffsets = offsets.toArray(Integer[]::new);
        return Optional.of(new Span(originalOffsets[start], originalOffsets[start + target.length() - 1] + 1));
    }

    /** Comparison operators and CJK parentheses/quotes differ only by glyph between OCR and PDF text. */
    private static char comparisonOperator(char c) {
        return switch (c) {
            case '﹤', '＜' -> '<';
            case '﹥', '＞' -> '>';
            case '(' -> '（';
            case ')' -> '）';
            case '‘', '’', '“' -> '”';
            default -> c;
        };
    }

    private static boolean nextNonWhitespaceIsHan(String raw, int start) {
        for (int j = start; j < raw.length(); j++) {
            char next = raw.charAt(j);
            if (Character.isWhitespace(next) || Character.isSpaceChar(next)) continue;
            return han(next);
        }
        return false;
    }

    private static boolean noSpace(char a, char b) {
        return (layout(a) && layout(b))
                || cjkPunctuation(a) || cjkPunctuation(b)
                || (a == '.' && han(b))
                || (Character.isDigit(a) && isNonLatin(b))
                || (isNonLatin(a) && Character.isDigit(b));
    }

    /** Line breaks around CJK punctuation carry no meaning; the source cell has no whitespace there. */
    private static boolean cjkPunctuation(char c) {
        return "，。；：！？、（）【】《》“”‘’".indexOf(c) >= 0;
    }

    private static boolean isNonLatin(char c) {
        return han(c) || c > 127 || "，。；：！？（）【】、《》、“”‘’%％".indexOf(c) >= 0;
    }

    private static boolean layout(char c) {
        return han(c) || "，。；：！？（）【】、".indexOf(c) >= 0;
    }

    private static boolean han(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
    }
}
