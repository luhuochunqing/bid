package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves logical scoring sections and the tables they own. */
final class OriginalScoringSchemePolicy {
    private static final Pattern SCHEME = Pattern.compile(
            ".*(?:评\\s*分\\s*办\\s*法|评\\s*审\\s*办\\s*法|评\\s*分\\s*方\\s*案|评\\s*标\\s*办\\s*法|评\\s*标\\s*方\\s*法|评\\s*审\\s*方\\s*法|评\\s*分\\s*规\\s*则|评\\s*分\\s*标\\s*准|评\\s*审\\s*标\\s*准|综\\s*合\\s*评\\s*分\\s*法).*",
            Pattern.DOTALL);
    private static final Pattern OTHER = Pattern.compile(".*(?:其他标包|其它标包|示例|样例|参考).*", Pattern.DOTALL);
    private static final Pattern CONTRACT_AUDIT = Pattern.compile(".*(?:合同履约考核|履约考核|合同绩效考核|服务考核).*", Pattern.DOTALL);
    private static final Pattern PRELIMINARY_REVIEW = Pattern.compile(
            ".*(?:形式评审标准|资格评审标准|响应性评审标准|符合性评审标准|初步评审标准|形式审查标准|资格审查标准|响应性审查标准).*",
            Pattern.DOTALL);
    private static final Pattern GENERIC_SECTION = Pattern.compile(".*(?:商务|技术|价格|报价|服务|资信|资格|响应|综合|评分|评标|分值).*", Pattern.DOTALL);
    private static final Pattern CATEGORY = Pattern.compile(".*(?:商务|技术|报价|价格|小计).*", Pattern.DOTALL);
    private static final Pattern PACKAGE = Pattern.compile("(?:第[一二三四五六七八九十百0-9]+标包|标包[一二三四五六七八九十百0-9]+|第[一二三四五六七八九十百0-9]+包)");
    private static final Pattern CHAPTER = Pattern.compile("^(第[一二三四五六七八九十百0-9]+章)[\\s　：:、.\\-—]*(.*)$");
    private static final Pattern TABLE_SUFFIX = Pattern.compile("(?:前附表|后附表|附表)$");

    private OriginalScoringSchemePolicy() {}

    static boolean owns(OriginalScoringContext context, DocumentArtifact.CanonicalTable table) {
        return owns(context.scheme(), table);
    }

    static boolean owns(String scheme, DocumentArtifact.CanonicalTable table) {
        if (table == null || otherScheme(table.sourceSection(), scheme)) return false;
        String section = table.sourceSection();
        if (section != null && CONTRACT_AUDIT.matcher(section).matches()) return false;
        if (section == null || section.isBlank()) return false;
        if (scheme == null || scheme.isBlank())
            return Boolean.TRUE.equals(table.isScoreTable()) && GENERIC_SECTION.matcher(section).matches();
        if (section.contains(scheme) || sameScheme(schemeTitleForTable(table), scheme)) return true;
        String root = section.split("[/＞>]", 2)[0].trim();
        if (isValidSchemeRoot(root) && !sameScheme(root, scheme)) return false;
        return Boolean.TRUE.equals(table.isScoreTable()) && GENERIC_SECTION.matcher(section).matches();
    }

    static String schemeId(String title) {
        if (title == null || title.isBlank()) return null;
        return "scheme-" + UUID.nameUUIDFromBytes(("score-scheme:" + title.trim()).getBytes(StandardCharsets.UTF_8));
    }

    static List<OriginalScoringContext.TextScope> textScopes(String text) {
        return OriginalScoringTextScopePolicy.textScopes(text);
    }

    static List<OriginalScoringContext.SchemeEvidence> schemes(DocumentArtifact artifact, String text) {
        if (artifact == null) return List.of();
        LinkedHashMap<String, String> titles = new LinkedHashMap<>();
        textScopes(text).stream().filter(scope -> "IN_SCHEME".equals(scope.scopeStatus()))
                .filter(scope -> scope.title() != null && !scope.title().isBlank())
                .forEach(scope -> titles.putIfAbsent(scope.schemeId(), scope.title()));
        if (titles.isEmpty()) artifact.tables().stream().map(OriginalScoringSchemePolicy::schemeTitleForTable)
                .filter(title -> !title.isBlank()).forEach(title -> titles.putIfAbsent(schemeId(title), title));
        if (titles.isEmpty()) {
            OriginalScoringContext context = OriginalScoringContext.resolve(artifact, text);
            boolean hasScoringTables = artifact.tables().stream().anyMatch(table -> table != null
                    && Boolean.TRUE.equals(table.isScoreTable()) && !CONTRACT_AUDIT.matcher(table.sourceSection()).matches()
                    && !isOtherSection(table.sourceSection()) && table.sourceSection() != null
                    && !table.sourceSection().isBlank());
            boolean ambiguous = context.problems().stream().anyMatch(problem -> "ORIGINAL_SCHEME_AMBIGUOUS".equals(problem.code()));
            if (hasScoringTables && !ambiguous && context.total().value() != null) {
                String title = "document-scheme:" + artifact.artifact().documentId();
                titles.put(schemeId(title), title);
            }
        }
        if (titles.isEmpty()) return List.of();
        List<OriginalScoringContext.SchemeEvidence> result = new ArrayList<>();
        for (var entry : titles.entrySet()) {
            List<DocumentArtifact.CanonicalTable> owned = artifact.tables().stream()
                    .filter(table -> table != null && tableBelongsTo(table, entry.getValue(), titles.values().stream().toList()))
                    .toList();
            List<OriginalScoringContext.TextScope> scopes = textScopes(text).stream()
                    .filter(scope -> entry.getKey().equals(scope.schemeId()) && "IN_SCHEME".equals(scope.scopeStatus())).toList();
            String scopedText = scopes.isEmpty() ? (titles.size() == 1 ? text : "")
                    : OriginalScoringTextScopePolicy.textWithinScopes(text, scopes);
            DocumentArtifact scopedArtifact = new DocumentArtifact(artifact.artifact(), owned, artifact.warnings(),
                    List.of(), artifact.schemaVersion(), artifact.documents());
            OriginalScoringContext context = OriginalScoringContext.resolve(scopedArtifact, scopedText);
            OriginalScoreEvidencePolicy.Total total = context.total();
            String state = total.value() != null ? "VERIFIED" : "CONFLICT".equals(total.source()) ? "CONFLICT" : "UNKNOWN";
            String layout = owned.size() > 1 ? "MULTI_TABLE_SAME_SCHEME" : owned.size() == 1 ? "SINGLE_TABLE" : "TEXT_ONLY";
            result.add(new OriginalScoringContext.SchemeEvidence(entry.getKey(), entry.getValue(),
                    owned.stream().map(DocumentArtifact.CanonicalTable::tableId).toList(), layout, state,
                    total.value(), total.quote(), total.locator()));
        }
        return List.copyOf(result);
    }

    static String schemeFrom(DocumentArtifact artifact, String text) {
        for (var scope : textScopes(text))
            if ("IN_SCHEME".equals(scope.scopeStatus()) && isValidSchemeRoot(scope.title())) return scope.title();
        for (var table : artifact.tables()) {
            if (table != null && table.sourceSection() != null) {
                String root = table.sourceSection().split("[/＞>]", 2)[0].trim();
                if (isValidSchemeRoot(root)) return root;
            }
        }
        return "";
    }

    static String schemeTitleForTable(DocumentArtifact.CanonicalTable table) {
        if (table == null || table.sourceSection() == null) return "";
        String root = table.sourceSection().split("[/＞>]", 2)[0].trim();
        return isValidSchemeRoot(root) ? root : "";
    }

    static OriginalScoringContext.TextScope textScopeAt(String text, int line) {
        return OriginalScoringTextScopePolicy.textScopeAt(text, line);
    }

    static Set<String> namedSchemes(DocumentArtifact artifact, String text, Set<String> packages) {
        Set<String> titles = new LinkedHashSet<>();
        artifact.tables().stream().filter(table -> table != null && table.sourceSection() != null)
                .map(table -> table.sourceSection().split("[/＞>]", 2)[0].trim())
                .filter(OriginalScoringSchemePolicy::isValidSchemeRoot).forEach(title -> addUniqueScheme(titles, title));
        for (var table : artifact.tables()) if (table != null && scoringSection(table.sourceSection())) addPackage(packages, table.sourceSection());
        bodySchemes(text, packages).forEach(title -> addUniqueScheme(titles, title));
        return titles;
    }

    static boolean otherScheme(String section, String scheme) {
        if (section == null) return false;
        if (isOtherSection(section) || CONTRACT_AUDIT.matcher(section).matches()) return true;
        Matcher sectionPackage = PACKAGE.matcher(section);
        Matcher schemePackage = PACKAGE.matcher(scheme == null ? "" : scheme);
        if (sectionPackage.find() && schemePackage.find() && !sectionPackage.group().equals(schemePackage.group())) return true;
        String root = section.split("[/＞>]", 2)[0].trim();
        if (scheme != null && !scheme.isBlank() && !section.isBlank() && !section.contains(scheme)
                && !section.contains("评分") && !section.contains("评标")
                && !section.contains("评审") && !section.contains("分值")) return true;
        return scheme != null && !scheme.isBlank() && isValidSchemeRoot(root) && !sameScheme(root, scheme);
    }

    static boolean category(String section) {
        if (section == null) return false;
        String[] parts = section.split("[/＞>]", 2);
        return parts.length > 1 && CATEGORY.matcher(parts[1]).matches();
    }

    static boolean categoryTitle(String title) { return title != null && CATEGORY.matcher(title).matches(); }

    static boolean isOtherSection(String title) {
        if (title == null) return false;
        String withoutReviewerPurpose = title.replaceAll("供(?:评委|评审人员|评标委员会)(?:打分|评分|评审)?参考", "");
        return OTHER.matcher(withoutReviewerPurpose).matches();
    }

    static boolean isSchemeTitle(String title) { return title != null && SCHEME.matcher(compact(title)).matches(); }

    static boolean scoringSection(String section) { return section != null && (section.contains("评分") || section.contains("评标")); }

    static void addPackage(Set<String> packages, String section) {
        if (section == null || isOtherSection(section)) return;
        Matcher matcher = PACKAGE.matcher(section);
        if (matcher.find()) packages.add(matcher.group());
    }

    private static Set<String> bodySchemes(String text, Set<String> packages) {
        Set<String> roots = new LinkedHashSet<>();
        if (text == null) return roots;
        int rootLevel = 0;
        String root = "";
        for (OriginalScoringTextScopePolicy.Heading heading : OriginalScoringTextScopePolicy.headings(text.split("\\R", -1))) {
            if (heading.level() <= rootLevel) root = "";
            addPackage(packages, heading.title());
            if (isValidSchemeRoot(heading.title()) && root.isBlank()) {
                addUniqueScheme(roots, heading.title());
                root = heading.title();
                rootLevel = heading.level();
            }
        }
        return roots;
    }

    private static boolean tableBelongsTo(DocumentArtifact.CanonicalTable table, String scheme, List<String> schemes) {
        String section = table.sourceSection() == null ? "" : table.sourceSection();
        if (CONTRACT_AUDIT.matcher(section).matches() || isOtherSection(section)) return false;
        if (section.contains(scheme) || sameScheme(schemeTitleForTable(table), scheme)) return true;
        if (schemeTitleForTable(table).isBlank()) {
            return schemes.size() == 1 && Boolean.TRUE.equals(table.isScoreTable())
                    && !section.isBlank() && GENERIC_SECTION.matcher(section).matches();
        }
        List<String> matchingSchemes = schemes.stream().filter(candidate -> sameScheme(schemeTitleForTable(table), candidate))
                .distinct().toList();
        return matchingSchemes.size() == 1 && sameScheme(matchingSchemes.get(0), scheme);
    }

    /** 正文或标题是否出现评分方案关键词（作用域判定复用同一文法，避免第二套正则）。 */
    static boolean mentionsScheme(String text) {
        return text != null && SCHEME.matcher(text).matches();
    }

    private static String compact(String title) {
        return title == null ? "" : title.replaceAll("[\\s　]+", "");
    }

    static boolean isValidSchemeRoot(String title) {
        if (title == null || title.isBlank()) return false;
        String compactTitle = compact(title);
        return SCHEME.matcher(compactTitle).matches() && !isOtherSection(compactTitle)
                && !CONTRACT_AUDIT.matcher(compactTitle).matches()
                && !PRELIMINARY_REVIEW.matcher(compactTitle).matches();
    }

    static boolean isContractAuditTitle(String title) {
        return title != null && CONTRACT_AUDIT.matcher(compact(title)).matches();
    }

    static boolean sameScheme(String first, String second) {
        if (first == null || second == null) return false;
        Matcher firstPackage = PACKAGE.matcher(first);
        Matcher secondPackage = PACKAGE.matcher(second);
        String firstPackageName = firstPackage.find() ? firstPackage.group() : "";
        String secondPackageName = secondPackage.find() ? secondPackage.group() : "";
        if (!firstPackageName.isBlank() && !secondPackageName.isBlank()
                && !firstPackageName.equals(secondPackageName)) return false;
        Matcher firstChapter = CHAPTER.matcher(first.trim());
        Matcher secondChapter = CHAPTER.matcher(second.trim());
        String firstNumber = firstChapter.matches() ? firstChapter.group(1) : "";
        String secondNumber = secondChapter.matches() ? secondChapter.group(1) : "";
        if (!firstNumber.isBlank() && !secondNumber.isBlank() && !firstNumber.equals(secondNumber)) return false;
        return schemeCore(first).equals(schemeCore(second));
    }

    private static String schemeCore(String title) {
        String value = title == null ? "" : title.trim();
        Matcher chapter = CHAPTER.matcher(value);
        if (chapter.matches()) value = chapter.group(2);
        value = value.replaceFirst("^(?:本项目|本标包)\\s*", "");
        value = TABLE_SUFFIX.matcher(value).replaceFirst("");
        value = OriginalScoringTextScopePolicy.TOC_TRAILER.matcher(value).replaceAll("");
        return value.replaceAll("[\\s　]+", "");
    }

    private static void addUniqueScheme(Set<String> schemes, String candidate) {
        if (candidate == null || candidate.isBlank()) return;
        if (schemes.stream().noneMatch(existing -> sameScheme(existing, candidate))) schemes.add(candidate);
    }

}
