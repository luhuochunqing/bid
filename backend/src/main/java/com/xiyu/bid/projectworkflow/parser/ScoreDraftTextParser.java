package com.xiyu.bid.projectworkflow.parser;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ScoreDraftTextParser {

    public List<ParsedSection> parse(String fileName, String extractedText) {
        List<String> lines = ScoreDraftLineClassifier.normalizeLines(extractedText);
        Map<String, List<String>> sections = ScoreDraftLineClassifier.splitSections(lines);
        List<ParsedSection> parsedSections = new ArrayList<>();
        int sectionIndex = 0;

        for (Map.Entry<String, List<String>> entry : sections.entrySet()) {
            List<DraftSeed> seeds = switch (entry.getKey()) {
                case "business" -> parseBusinessSection(entry.getValue());
                case "technical" -> parseTechnicalSection(entry.getValue());
                case "price" -> parsePriceSection(entry.getValue());
                default -> List.of();
            };
            parsedSections.add(new ParsedSection(entry.getKey(), sectionIndex++, seeds));
        }
        return parsedSections;
    }

    private List<DraftSeed> parseBusinessSection(List<String> sectionLines) {
        return parseSerialChunks(sectionLines, false);
    }

    private List<DraftSeed> parsePriceSection(List<String> sectionLines) {
        return parseSerialChunks(sectionLines, true);
    }

    private List<DraftSeed> parseSerialChunks(List<String> sectionLines, boolean scoreBeforeRule) {
        List<String> lines = sectionLines.stream()
                .map(ScoreDraftLineClassifier::normalizeLineForParsing)
                .filter(line -> !line.isBlank())
                .filter(line -> !ScoreDraftLineClassifier.isTableHeader(line))
                .takeWhile(line -> !line.startsWith("3.9 ") && !line.equals("合计"))
                .toList();

        List<List<String>> chunks = new ArrayList<>();
        List<String> current = null;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (ScoreDraftLineClassifier.looksLikeRowStart(lines, index)) {
                if (current != null && !current.isEmpty()) {
                    chunks.add(current);
                }
                current = new ArrayList<>();
            }
            if (current != null) {
                current.add(line);
            }
        }
        if (current != null && !current.isEmpty()) {
            chunks.add(current);
        }

        List<DraftSeed> drafts = new ArrayList<>();
        for (List<String> chunk : chunks) {
            drafts.addAll(parseSerialChunk(chunk, scoreBeforeRule));
        }
        return drafts;
    }

    private List<DraftSeed> parseSerialChunk(List<String> chunk, boolean scoreBeforeRule) {
        if (chunk.size() < 2) {
            return List.of();
        }

        String title = ScoreDraftLineClassifier.cleanTitle(chunk.get(1));
        if (title.isBlank()) {
            return List.of();
        }

        List<String> remaining = new ArrayList<>(chunk.subList(2, chunk.size()));
        if (remaining.isEmpty()) {
            return List.of();
        }

        if (scoreBeforeRule
                && ScoreDraftLineClassifier.isScoreOnlyLine(remaining.get(0))
                && remaining.size() > 1) {
            String score = ScoreDraftSeedFactory.formatScoreText(remaining.remove(0));
            return List.of(ScoreDraftSeedFactory.buildSeed(
                    title,
                    title,
                    ScoreDraftLineClassifier.joinLines(remaining),
                    score
            ));
        }

        List<DraftSeed> results = new ArrayList<>();
        List<String> buffer = new ArrayList<>();
        int unitIndex = 1;
        for (String line : remaining) {
            if (ScoreDraftLineClassifier.isScoreOnlyLine(line) && !buffer.isEmpty()) {
                String unitTitle = unitIndex == 1 ? title : title + "（子项" + unitIndex + "）";
                results.add(ScoreDraftSeedFactory.buildSeed(
                        unitTitle,
                        title,
                        ScoreDraftLineClassifier.joinLines(buffer),
                        ScoreDraftSeedFactory.formatScoreText(line)
                ));
                buffer.clear();
                unitIndex++;
            } else {
                buffer.add(line);
            }
        }

        if (!buffer.isEmpty()) {
            String merged = ScoreDraftLineClassifier.joinLines(buffer);
            String inferredScore = ScoreDraftSeedFactory.inferScoreText(merged);
            results.add(ScoreDraftSeedFactory.buildSeed(
                    unitIndex == 1 ? title : title + "（子项" + unitIndex + "）",
                    title,
                    merged,
                    inferredScore
            ));
        }
        return results;
    }

    private List<DraftSeed> parseTechnicalSection(List<String> sectionLines) {
        List<String> lines = sectionLines.stream()
                .map(ScoreDraftLineClassifier::normalizeLineForParsing)
                .filter(line -> !line.isBlank())
                .filter(line -> !ScoreDraftLineClassifier.isTableHeader(line))
                .takeWhile(line -> !line.equals("合计") && !line.startsWith("3.8 "))
                .toList();

        List<DraftSeed> drafts = new ArrayList<>();
        int index = 0;
        while (index < lines.size()) {
            String title = ScoreDraftLineClassifier.cleanTitle(lines.get(index));
            if (title.isBlank()) {
                index++;
                continue;
            }
            if (index + 1 >= lines.size() || !ScoreDraftLineClassifier.isScoreOnlyLine(lines.get(index + 1))) {
                index++;
                continue;
            }

            String score = ScoreDraftSeedFactory.formatScoreText(lines.get(index + 1));
            index += 2;
            List<String> ruleLines = new ArrayList<>();
            while (index < lines.size()) {
                if (index + 1 < lines.size()
                        && !ScoreDraftLineClassifier.isScoreOnlyLine(lines.get(index))
                        && ScoreDraftLineClassifier.isScoreOnlyLine(lines.get(index + 1))) {
                    break;
                }
                if (lines.get(index).equals("合计")) {
                    break;
                }
                ruleLines.add(lines.get(index));
                index++;
            }

            String ruleText = ScoreDraftLineClassifier.joinLines(ruleLines);
            if (!ruleText.isBlank()) {
                drafts.addAll(buildTechnicalSeeds(title, ruleText, score));
            }
        }
        return drafts;
    }

    private List<DraftSeed> buildTechnicalSeeds(String title, String ruleText, String score) {
        List<String> clauses = ScoreDraftLineClassifier.splitNumberedClauses(ruleText);
        List<String> scoredClauses = clauses.stream()
                .map(String::trim)
                .filter(clause -> !clause.isBlank() && ScoreDraftSeedFactory.inferScoreText(clause) != null)
                .toList();

        if (scoredClauses.size() <= 1) {
            return List.of(ScoreDraftSeedFactory.buildSeed(title, title, ruleText, score));
        }

        List<DraftSeed> seeds = new ArrayList<>();
        for (int index = 0; index < scoredClauses.size(); index++) {
            String clauseTitle = title + "（子项" + (index + 1) + "）";
            seeds.add(ScoreDraftSeedFactory.buildSeed(
                    clauseTitle,
                    title,
                    scoredClauses.get(index),
                    ScoreDraftSeedFactory.inferScoreText(scoredClauses.get(index))
            ));
        }
        return seeds;
    }
}
