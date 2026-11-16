package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.regex.Pattern;

/** A closed unconditional-delivery highest band; never a lowest-price or missing-evidence proof. */
public final class FreeDeliveryScoreProof {
    private static final String N="[0-9]+(?:\\.[0-9]+)?";
    private static final Pattern RULE=Pattern.compile("(?:[0-9]+\\|免费配送最小起订额度\\|(?<declared>"+N+")\\|)?"
            +"1\\)不受订单金额、配送范围限制全部免运费配送,得(?<top>"+N+")分;"
            +"2\\)免费配送订单金额为每批次订单金额最低者(?<next>"+N+")分,其它按低至高顺序递减(?<step>"+N+")分,最低得0分[。.]?");
    private static final Pattern NEGATIVE=Pattern.compile("不承诺|不保证|不提供|仅限|除外|不适用");
    private FreeDeliveryScoreProof() {}
    public static boolean highestBand(String detail,BigDecimal weight,BigDecimal raw,BigDecimal score,
                                      String quote,ScoringDocument document) {
        if(detail==null||weight==null||raw==null||score==null||quote==null
                ||raw.compareTo(weight)!=0||score.compareTo(weight)!=0
                ||!"COMPLETE".equals(document.extraction().coverageStatus())||!document.verifiedQuoteSource(quote))return false;
        var rule=RULE.matcher(normalize(detail));
        if(!rule.matches())return false;
        BigDecimal top=new BigDecimal(rule.group("top")),next=new BigDecimal(rule.group("next")),step=new BigDecimal(rule.group("step"));
        if(top.compareTo(weight)!=0||next.signum()<=0||next.compareTo(top)>=0||step.signum()<=0
                ||next.remainder(step).signum()!=0
                ||rule.group("declared")!=null&&new BigDecimal(rule.group("declared")).compareTo(weight)!=0)return false;
        String response=normalize(quote);
        if(NEGATIVE.matcher(response).find())return false;
        return response.contains("所有订单无最低起订金额限制、无配送区域限制,全程免费配送")
                ||response.contains("不受订单金额、配送范围限制全部免运费配送");
    }
    private static String normalize(String text) {
        return Normalizer.normalize(text,Normalizer.Form.NFKC).replaceAll("[\\s\\u3000]+","");
    }
}
