// Input: 招标报价原文、结构化要求及单项结果
// Output: 完整绑定载体的直接指令保留系统报价原位证据；外层来源/条件或第二介词未绑定时保留完整限定并待确认；一次内部解析保留条款来源，免交冲突待确认，页号只绑定选中原文
// Pos: compliance/domain/policy/ - 报价渠道适用性纯核心
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.domain.QualityEvidence;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** 系统报价与纸面报价分开核验；输入只来自当前招标文件。 */
public final class PricingSubmissionPolicy {
    private static final Pattern CHANNEL = Pattern.compile("(?:在|于|通过|使用)([^。；;]{0,12}?)系统报价(?:表|单)");
    private static final Pattern DIRECT_CARRIER = Pattern.compile("(?:本次|本项目)?(?:招标|电子)?");
    private static final Pattern ACTION = Pattern.compile("填报|填写|报送|申报|录入|报(?!价)|填");
    private static final Pattern RATE_OBJECT_CANDIDATE = Pattern.compile("[^。；;]{0,8}折扣率");
    private static final Pattern DIRECT_RATE_OBJECT = Pattern.compile("(?:不含税|含税|不同)?折扣率");
    private static final Pattern NEGATIVE_PREDICATE = Pattern.compile(
            "不得|不应|无需|无须|不必|不能|禁止|严禁|勿|不要|不许|不准|不需要|不用|不可|不允许|不要求|不是|并非");
    private static final Pattern BARE_NEGATION = Pattern.compile("不(?:直接|再|另行|自行|擅自)?\\s*$");
    private static final Pattern MODIFIER = Pattern.compile(
            "(?:的|在|于)(?:(?!系统报价)[^，,。；;]){1,16}(?:栏目|字段|列)(?:中|内|上)?"
                    + "|(?:按照|按|依据|依照)(?:(?!系统报价)[^，,。；;]){1,16}(?:口径|方式|规则|要求)");
    private static final Pattern CONTRAST = Pattern.compile(
            "(?:但是|但|然而|不过)(?=(?:仍|还|也)?(?:须|应|需|必须|要求|请|要))|另(?:须|应|需)");
    private static final Pattern UNRESOLVED_CHANNEL = Pattern.compile("在|于|通过|使用");
    // 只承认已绑定的直接指令前缀；其余外层来源/条件不通过词法猜测确认为系统义务。
    private static final Pattern DIRECT_PREFIX = Pattern.compile(
            "(?:第[一二三四五六七八九十0-9]+卷经济标[：:（(]?)?"
                    + "(?:标段[一二三四五六七八九十0-9]+)?(?:要求)?(?:投标人|报价人|供应商)?"
                    + "(?:应当|必须|应|须|需|请|要求|要|也要|仍须|还须)?");
    private static final Pattern PAPER_TABLE = Pattern.compile("开标一览表|投标一览表|报价一览表|开标报价表|投标报价表|总报价表|分项报价明细表|分项明细表|分项报价表|报价明细表|报价表|报价单");
    record Instruction(int start, int end, boolean uncertain) { }
    private PricingSubmissionPolicy() { }

    static boolean hasSystemDiscountInstruction(String text) { return instruction(text).filter(hit -> !hit.uncertain()).isPresent(); }

    static String systemDiscountSource(String text) {
        return instruction(text).filter(hit -> !hit.uncertain())
                .map(hit -> text.substring(hit.start(), hit.end())).orElse("");
    }

    record PaperRequirements(List<String> requiredTables, List<String> waivedClauses, List<String> requiredClauses) { }
    record Analysis(PaperRequirements paper, Instruction instruction, String quote) {
        String systemDiscountSource() { return instruction != null && !instruction.uncertain() ? quote : ""; }
    }

    static Analysis analyze(String text) {
        var instruction = instruction(text).orElse(null);
        return new Analysis(paperRequirements(text), instruction,
                instruction == null ? "" : text.substring(instruction.start(), instruction.end()));
    }

    /** 只接受完整的直接提交条款，表名、模板及条件引用不证明义务。 */
    static PaperRequirements paperRequirements(String text) {
        List<String> required = new ArrayList<>();
        List<String> waived = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        if (text != null) for (String clause : text.split("[。；;]")) {
            String compact = clause.replaceAll("\\s+", "");
            var tables = PAPER_TABLE.matcher(compact);
            if (!tables.find()) continue;
            String prefix = compact.substring(0, tables.start());
            String objects = compact.substring(tables.start()).replaceFirst("，列明数量、单价和合价$", "");
            if (!objects.matches("(?:" + PAPER_TABLE.pattern() + ")(?:[及和、与](?:" + PAPER_TABLE.pattern() + "))*")) continue;
            String subject = "(?:标段[一二三四五六七八九十0-9]+)?(?:投标人|报价人|供应商|投标文件)?";
            if (prefix.matches(subject + "(?:还须|另须|必须|应当|须|应|需)(?:随投标文件)?(?:另附|提交|提供|附上)(?:纸质)?")) {
                do { required.add(tables.group()); } while (tables.find());
                sources.add(clause.strip());
            } else if (prefix.matches(subject + "(?:无需|无须|不必|不需要|不用|不要求)(?:提交|提供|附上)")) {
                waived.add(clause.strip());
            }
        }
        return new PaperRequirements(List.copyOf(required), List.copyOf(waived), List.copyOf(sources));
    }

    /** 删除空白仅用于匹配，索引映射始终回到同一份原文。 */
    private static Optional<Instruction> instruction(String text) {
        if (text == null) return Optional.empty();
        StringBuilder compact = new StringBuilder();
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) { compact.append(text.charAt(i)); offsets.add(i); }
        }
        String compactText = compact.toString();
        var channels = CHANNEL.matcher(compactText);
        while (channels.find()) {
            // 修饰语保留原位长度，但其中的动作字样不是实际填报动作。
            int tailEnd = Math.min(compact.length(), channels.end() + 40);
            for (char separator : new char[]{'。', '；', ';'}) {
                int boundary = compact.indexOf(String.valueOf(separator), channels.end());
                if (boundary >= 0) tailEnd = Math.min(tailEnd, boundary);
            }
            String tail = maskModifiers(compactText.substring(channels.end(), tailEnd));
            int sentenceStart = 0;
            for (char separator : new char[]{'。', '；', ';'}) {
                sentenceStart = Math.max(sentenceStart, compactText.lastIndexOf(separator, channels.start()) + 1);
            }
            var actions = ACTION.matcher(tail);
            while (actions.find() && actions.start() <= 24) {
                var object = RATE_OBJECT_CANDIDATE.matcher(tail).region(actions.end(), tail.length());
                if (!object.lookingAt() || ACTION.matcher(tail.substring(actions.end(), object.end())).find()) continue;
                if (!negated(maskModifiers(precedingClause(compactText, channels.end() + actions.start())))) {
                    boolean unresolvedObject = !DIRECT_RATE_OBJECT.matcher(tail.substring(actions.end())).matches();
                    int end = unresolvedObject ? tailEnd : channels.end() + object.end();
                    String outer = compactText.substring(sentenceStart, channels.start());
                    // 已知禁止动作后的明确转折可重置极性，但转折不能抹掉未知外层条件/来源。
                    String directOuter = NEGATIVE_PREDICATE.matcher(outer).lookingAt() ? afterContrast(outer) : outer;
                    boolean unresolvedOuter = !DIRECT_PREFIX.matcher(directOuter).matches()
                            && !NEGATIVE_PREDICATE.matcher(directOuter).matches();
                    boolean uncertain = unresolvedObject || !DIRECT_CARRIER.matcher(channels.group(1)).matches() || unresolvedOuter
                            || UNRESOLVED_CHANNEL.matcher(tail.substring(0, actions.start())).find();
                    int start = unresolvedOuter ? sentenceStart : channels.start();
                    var hit = new Instruction(offsets.get(start), offsets.get(end - 1) + 1, uncertain);
                    return Optional.of(hit);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean negated(String control) {
        return NEGATIVE_PREDICATE.matcher(control).find() || BARE_NEGATION.matcher(control).find();
    }

    private static String precedingClause(String text, int at) {
        int start = 0;
        for (char separator : new char[]{'。', '；', ';', '，', ','}) start = Math.max(start, text.lastIndexOf(separator, at) + 1);
        return afterContrast(text.substring(start, at));
    }

    private static String afterContrast(String control) {
        var contrast = CONTRAST.matcher(control);
        int reset = 0;
        while (contrast.find()) reset = contrast.end();
        return control.substring(reset);
    }

    private static String maskModifiers(String text) {
        StringBuilder view = new StringBuilder(text);
        var matcher = MODIFIER.matcher(text);
        while (matcher.find()) {
            for (int i = matcher.start(); i < matcher.end(); i++) view.setCharAt(i, ' ');
        }
        return view.toString();
    }

    static QualityCheckItemResult reconcile(QualityCheckItemResult result, DocumentContent tender, PriceRequirement req) {
        return reconcile(result, null, tender, req);
    }

    static QualityCheckItemResult reconcile(QualityCheckItemResult result, DocumentContent bid,
            DocumentContent tender, PriceRequirement req) {
        if (result == null || !BidQualityCheckItem.BID_OPENING_TABLE.getCode().equals(result.itemCode()) || tender == null) return result;
        var facts = analyze(tender.fullText());
        var paper = facts.paper();
        if (paper.requiredTables().isEmpty() && !paper.waivedClauses().isEmpty()) {
            return ManualReasons.manual(BidQualityCheckItem.BID_OPENING_TABLE, ManualReasons.AMBIGUOUS,
                    "招标原文存在报价材料免交条款，不能据表名或相冲突的抽取结果认定缺表",
                    "请核对免交条款的适用范围及实际报价提交材料",
                    List.of(source(tender, "报价材料免交条款待核对", paper.waivedClauses().getFirst())));
        }
        boolean requiresPaper = !paper.requiredTables().isEmpty() || req != null
                && (Boolean.TRUE.equals(req.requireBidOpeningTable()) || Boolean.TRUE.equals(req.requireItemizedList()));
        if (requiresPaper) return reconcileRequiredPaper(result, bid, tender, paper);
        if (req == null || facts.instruction() == null
                || !facts.instruction().uncertain() && !PriceRequirement.SYSTEM_DISCOUNT_METHOD.equals(req.pricingMethod())) return result;
        boolean uncertain = facts.instruction().uncertain();
        return ManualReasons.manual(BidQualityCheckItem.BID_OPENING_TABLE,
                uncertain ? ManualReasons.AMBIGUOUS : ManualReasons.EVIDENCE_INSUFFICIENT,
                uncertain ? "折扣率提交的渠道与条件尚未可靠绑定，不能确认系统或纸面材料已完成或缺失"
                        : "招标要求在系统报价表填报折扣率，Word正文不能证明系统已完成填报，不能按缺少传统金额报价表判定不通过",
                uncertain ? "请核对实际提交载体、适用条件及已填报材料；本项未自动识别为系统报价"
                        : "请核对招标系统中的折扣率填报与提交回执；另有纸面要求时按对应标段逐项复核",
                List.of(source(tender, uncertain ? "报价提交范围待确认" : "招标系统折扣率报价要求", facts.quote())));
    }

    private static QualityCheckItemResult reconcileRequiredPaper(QualityCheckItemResult result,
            DocumentContent bid, DocumentContent tender, PaperRequirements paper) {
        if (result.status() != QualityCheckStatus.PASS
                || bid != null && BidOpeningTableCheckPolicy.check(bid).status() == QualityCheckStatus.PASS) return result;
        var evidence = paper.requiredClauses().isEmpty() ? result.evidences()
                : List.of(source(tender, "明确纸面提交条款", paper.requiredClauses().getFirst()));
        return ManualReasons.manual(BidQualityCheckItem.BID_OPENING_TABLE, ManualReasons.EVIDENCE_INSUFFICIENT,
                "招标要求报价材料，但尚未定位到可核验的已填表格，不能仅凭模型结论判定齐全",
                "请核对原始表格或清单中的实际填报数据", evidence);
    }

    private static QualityEvidence source(DocumentContent tender, String title, String quote) {
        Integer page = tender.pages().stream().filter(p -> p.text().contains(quote))
                .map(DocumentContent.PageContent::pageNumber).findFirst().orElse(null);
        return QualityEvidence.tender(title, page, quote);
    }
}
