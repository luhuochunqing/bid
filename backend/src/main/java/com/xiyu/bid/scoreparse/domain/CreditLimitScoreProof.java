package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Closed four-band credit-limit rule; only a proved highest-band response can publish a score. */
public final class CreditLimitScoreProof {
    private static final String NUMBER="(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?";
    private static final String POINTS="[0-9]+(?:\\.[0-9]+)?";
    private static final String METRIC="赊销额度|授信额度|信用额度";
    private static final String AMOUNT="(?:人民币|CNY)?"+NUMBER+"(?:亿元|万元|元)";
    private static final String H="[ \\t\\u3000]*";
    private static final Pattern RULE=Pattern.compile(
            "(?:[0-9]+\\|(?<declared>"+POINTS+")\\|)?根据(?<metric>"+METRIC+")X进行评审:"
            +"\\(1\\)X≥(?<a>"+AMOUNT+"),得(?<p>"+POINTS+")分;"
            +"\\(2\\)(?<a2>"+AMOUNT+")>X≥(?<b>"+AMOUNT+"),得(?<q>"+POINTS+")分;"
            +"\\(3\\)(?<b2>"+AMOUNT+")>X≥(?<c>"+AMOUNT+"),得(?<r>"+POINTS+")分;"
            +"\\(4\\)X<(?<c2>"+AMOUNT+"),得0(?:\\.0+)?分[。.]?");
    private static final Pattern AMOUNT_VALUE=Pattern.compile("(?:人民币|CNY)?("+NUMBER+")(亿元|万元|元)");
    private static final Pattern ROW=Pattern.compile("^\\|"+H+"(?:(?<index>[0-9]+)"+H+"\\|"+H+")?"
            +"(?<metric>"+METRIC+")"+H+"\\|"+H+"(?<amount>"+AMOUNT+")"+H+"\\|$",Pattern.MULTILINE);
    private static final Pattern METRIC_ROW=Pattern.compile("^\\|"+H+"(?:[0-9]+"+H+"\\|"+H+")?"
            +"(?<metric>"+METRIC+")"+H+"\\|[^\\r\\n]*\\|$",Pattern.MULTILINE);
    private CreditLimitScoreProof() {}
    public record Result(boolean highestBandVerified,BigDecimal score) {}

    /** Candidate evidence stays verbatim; it is not a score or a substitute for the full rule checks. */
    public static String evidenceRow(String detail,ScoringDocument document) {
        if(detail==null)return null;
        Matcher rule=RULE.matcher(normalizeRule(detail));
        if(!rule.matches())return null;
        String selected=null;Matcher rows=ROW.matcher(document.text());
        while(rows.find()) {
            if(!rows.group("metric").equals(rule.group("metric"))||!document.verifiedNativeTableRow(rows.group(),cells(rows)))continue;
            if(selected!=null)return null;
            selected=rows.group();
        }
        return selected;
    }

    public static Result assess(String detail,BigDecimal weight,BigDecimal rawScore,BigDecimal resolvedScore,
                                String quote,ScoringDocument document) {
        if(detail==null||!detail.contains("额度"))return new Result(false,null);
        Result pending=new Result(false,null);
        if(weight==null||quote==null||quote.contains("\n"))return pending;
        String normalized=normalizeRule(detail);
        Matcher rule=RULE.matcher(normalized),row=ROW.matcher(quote.strip());
        if(!rule.matches()||!row.matches()||!rule.group("metric").equals(row.group("metric")))return pending;
        BigDecimal top=amount(rule.group("a")),middle=amount(rule.group("b")),lower=amount(rule.group("c"));
        BigDecimal points=new BigDecimal(rule.group("p")),midPoints=new BigDecimal(rule.group("q")),lowPoints=new BigDecimal(rule.group("r"));
        if(top==null||middle==null||lower==null||lower.signum()<=0||top.compareTo(middle)<=0||middle.compareTo(lower)<=0
                ||top.compareTo(amount(rule.group("a2")))!=0||middle.compareTo(amount(rule.group("b2")))!=0
                ||lower.compareTo(amount(rule.group("c2")))!=0||points.compareTo(weight)!=0
                ||points.compareTo(midPoints)<=0||midPoints.compareTo(lowPoints)<=0||lowPoints.signum()<=0
                ||rule.group("declared")!=null&&new BigDecimal(rule.group("declared")).compareTo(weight)!=0)return pending;
        BigDecimal observed=amount(row.group("amount"));
        if(observed==null||observed.compareTo(top)<0)return pending;
        if(!document.verifiedNativeTableRow(quote,cells(row)))return pending;
        Matcher rows=METRIC_ROW.matcher(document.text());
        while(rows.find()) {
            if(!rows.group("metric").equals(row.group("metric")))continue;
            Matcher other=ROW.matcher(rows.group());
            if(!other.matches())return pending;
            BigDecimal value=amount(other.group("amount"));
            if(value==null||value.compareTo(observed)!=0)return pending;
        }
        boolean modelConsistent=rawScore!=null&&resolvedScore!=null&&rawScore.compareTo(points)==0&&resolvedScore.compareTo(points)==0;
        return new Result(true,modelConsistent?points:null);
    }
    private static List<String> cells(Matcher row) {
        List<String> cells=new ArrayList<>();
        if(row.group("index")!=null)cells.add(row.group("index"));
        cells.add(row.group("metric"));cells.add(row.group("amount"));return cells;
    }
    private static String normalizeRule(String detail) {
        return Normalizer.normalize(detail,Normalizer.Form.NFKC).replaceAll("[\\s\\u3000]+","").replace(">=","≥");
    }
    private static BigDecimal amount(String literal) {
        Matcher value=AMOUNT_VALUE.matcher(literal);
        return value.matches()?ScoreItemConstraintExtractor.currencyAmount(value.group(1),value.group(2)):null;
    }
}
