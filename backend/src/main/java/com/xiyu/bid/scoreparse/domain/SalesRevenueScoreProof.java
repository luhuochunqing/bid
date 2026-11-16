package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.regex.Pattern;

/** Only a closed annual platform-revenue rule and a proved highest band may bypass unrelated empty images. */
public final class SalesRevenueScoreProof {
    private static final String N="[0-9]+(?:\\.[0-9]+)?";
    private static final Pattern HEADER=Pattern.compile("(?:[0-9]+\\|销售额\\|(?<declared>"+N+")\\|)?"
            +"平台工业品(?<year>20[0-9]{2})年销售额,(?<top>"+N+")亿元\\(含\\)以上得(?<points>"+N+")分,");
    private static final Pattern BAND=Pattern.compile("(?<low>"+N+")\\(含\\)-(?<high>"+N+")亿元得(?<points>"+N+")分,");
    private static final Pattern ZERO=Pattern.compile("低于(?<low>"+N+")亿元得0分[。.]?");
    private static final Pattern REVENUE=Pattern.compile("(?<year>20[0-9]{2})年平台工业品的?销售额(?:为|是)(?<value>"+N+")亿元");
    private static final Pattern UNVERIFIED_REVENUE=Pattern.compile("不|未|预计|预测|目标|可能|竞争对手|集团|合并|非平台");
    private SalesRevenueScoreProof() {}

    public static boolean highestBand(String detail,BigDecimal weight,BigDecimal rawScore,BigDecimal score,
                                      String quote,ScoringDocument document) {
        if(detail==null||weight==null||rawScore==null||score==null||quote==null
                ||rawScore.compareTo(weight)!=0||score.compareTo(weight)!=0
                ||!"COMPLETE".equals(document.extraction().coverageStatus())||!document.verifiedQuoteSource(quote))return false;
        String normalizedQuote=normalize(quote);
        if(UNVERIFIED_REVENUE.matcher(normalizedQuote).find())return false;
        String rule=normalize(detail);
        var header=HEADER.matcher(rule);
        if(!header.lookingAt())return false;
        BigDecimal top=new BigDecimal(header.group("top")),points=new BigDecimal(header.group("points"));
        if(top.signum()<=0||points.compareTo(weight)!=0
                ||header.group("declared")!=null&&new BigDecimal(header.group("declared")).compareTo(weight)!=0)return false;
        int offset=header.end(),bands=0;
        BigDecimal upper=top,previousPoints=points;
        var band=BAND.matcher(rule);
        while(band.region(offset,rule.length()).lookingAt()) {
            BigDecimal low=new BigDecimal(band.group("low")),high=new BigDecimal(band.group("high"));
            BigDecimal currentPoints=new BigDecimal(band.group("points"));
            if(low.signum()<=0||high.compareTo(upper)!=0||low.compareTo(high)>=0
                    ||currentPoints.signum()<=0||currentPoints.compareTo(previousPoints)>=0)return false;
            upper=low;previousPoints=currentPoints;offset=band.end();bands++;
        }
        var zero=ZERO.matcher(rule.substring(offset));
        if(bands==0||!zero.matches()||new BigDecimal(zero.group("low")).compareTo(upper)!=0)return false;
        var evidence=REVENUE.matcher(normalizedQuote);
        if(!evidence.find()||!evidence.group("year").equals(header.group("year")))return false;
        BigDecimal value=new BigDecimal(evidence.group("value"));
        if(value.compareTo(top)<0||evidence.find())return false;
        var declarations=REVENUE.matcher(normalize(document.text()));
        int declarationsSeen=0;
        while(declarations.find()) {
            if(declarations.group("year").equals(header.group("year"))) {
                declarationsSeen++;
                if(new BigDecimal(declarations.group("value")).compareTo(value)!=0)return false;
            }
        }
        return declarationsSeen>0;
    }
    private static String normalize(String text) {
        return Normalizer.normalize(text,Normalizer.Form.NFKC).replaceAll("[\\s\\u3000]+","");
    }
}
