package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.performance.domain.valueobject.CustomerType;
import com.xiyu.bid.performance.domain.valueobject.ProjectType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Executable facts for rough estimates; material composition remains an explicit review task. */
public record KnowledgeEvidenceConstraints(LocalDate signedAfter, LocalDate signedBefore, LocalDate brandValidUntil,
        BigDecimal minAmount, BigDecimal minArea, List<String> types, List<String> customers,
        List<String> regions, List<String> provinces, List<String> positions, List<String> brands, List<String> certificates,
        String pendingChecks) {
    private static final String DATE="(\\d{4})\\s*[年/.-]\\s*(\\d{1,2})\\s*[月/.-]\\s*(\\d{1,2})\\s*日?";

    public static KnowledgeEvidenceConstraints parse(String category,String detail,LocalDate day) {
        return parse(category,detail,day,true);
    }

    /** Formal scoring checks bid evidence; knowledge-library material warnings apply only to estimates. */
    public static KnowledgeEvidenceConstraints parseForScoring(String detail,LocalDate day) {
        return parse("PROJECT",detail,day,false);
    }

    private static KnowledgeEvidenceConstraints parse(String category,String detail,LocalDate day,boolean knowledgeEstimate) {
        String text=detail==null?"":detail;
        LocalDate after=null,before=null,brandUntil=null;
        BigDecimal amount=null;
        String pending="";
        if ("PROJECT".equals(category)) {
            after=ScoreItemConstraintExtractor.extractSignedAfter(text,day);
            var start=Pattern.compile(DATE+"\\s*(?:以来|以后|之后|起|至|到|—|~|～)").matcher(text);
            if(after==null && start.find()) after=date(start,1);
            var range=Pattern.compile(DATE+"\\s*(?:至|到|—|~|～)\\s*"+DATE).matcher(text);
            if(range.find()) { after=date(range,1);before=date(range,4); }
            var coarse=Pattern.compile("(\\d{4}年(?:\\d{1,2}月)?)\\s*(?:至|到|—|~|～)\\s*(\\d{4}年(?:\\d{1,2}月)?)").matcher(text);
            if(coarse.find()) { after=coarseDate(coarse.group(1),false);before=coarseDate(coarse.group(2),true); }
            var end=Pattern.compile("(?:截至|截止至|不晚于)\\s*"+DATE).matcher(text);
            if(end.find()) before=date(end,1);
            if(text.matches("(?s).*(?:年|近.{0,3}年).*") && after==null && before==null
                    && text.matches("(?s).*(?:签订|签约|业绩|合同).*")
                    && text.matches("(?s).*(?:以来|以后|之后|至|截至|近.{0,3}年|以前|之前).*") )
                throw new IllegalArgumentException("业绩日期条件未能可靠解析，请核验日期范围");
            if(after!=null && before!=null && after.isAfter(before))
                throw new IllegalArgumentException("业绩日期上下界冲突，请核验日期范围");
            if(text.contains("至今")||text.matches("(?s).*近\\s*[一二三两四五12345]\\s*年.*")) before=day;
            if(text.matches("(?s).*(?:首次报价|投标|响应文件提交|报价)截止(?:日期|时间|日).*"))
                pending="首次报价截止日或投标截止日期及资格条件尚待按招标原文核验";
            boolean contractAmountRequired=ScoreItemConstraintExtractor.hasContractAmountThreshold(text);
            amount=contractAmountRequired?ScoreItemConstraintExtractor.extractMinContractAmount(text, true):null;
            var materials=knowledgeEstimate?new ProjectEvidenceRequirementPolicy().assess(text).manualChecks():List.<String>of();
            if(!materials.isEmpty()) pending+=(pending.isEmpty()?"":"；")+"业绩材料尚待核验："+String.join("、",materials);
            if(amount==null && contractAmountRequired)
                throw new IllegalArgumentException("合同金额门槛未能可靠解析，请人工核验");
        }
        if("BRAND".equals(category)) {
            var validity=Pattern.compile("(?:授权)?有效(?:期|期限)?\\s*(?:至|到|不早于)\\s*"+DATE).matcher(text);
            if(validity.find()) brandUntil=date(validity,1);
            else if(text.matches("(?s).*有效(?:期|期限).*(?:至|到|不早于|不少于).*"))
                pending="品牌授权有效期限要求尚待人工核验";
        }
        List<String> types=java.util.Arrays.stream(ProjectType.values())
                .filter(type->declaresProjectType(text,type)).map(ProjectType::displayName).toList();
        List<String> customers=terms(text,List.of("央企","中央企业","国企","地方国企","政府","事业单位","民企","民营企业","民营"))
                .stream().map(KnowledgeEvidenceConstraints::canonicalCustomer).distinct().toList();
        List<String> regions=terms(text,List.of("华北","东北","华东","华中","华南","西南","西北"));
        List<String> provinces=terms(text,List.of("北京","天津","河北","山西","内蒙古","辽宁","吉林","黑龙江",
                "上海","江苏","浙江","安徽","福建","江西","山东","河南","湖北","湖南","广东","广西","海南",
                "重庆","四川","贵州","云南","西藏","陕西","甘肃","青海","宁夏","新疆","香港","澳门","台湾"));
        // A more specific title subsumes its generic substring; it must not become an OR alternative.
        List<String> positions=terms(text,List.of("高级工程师","中级工程师","工程师","项目经理","技术负责人","建造师"))
                .stream().filter(p->!p.equals("工程师") || !text.contains("高级工程师")&&!text.contains("中级工程师")).toList();
        var brands=new ArrayList<String>();
        var named=Pattern.compile("品牌(?:为|是|名称为|[:：])\\s*([\\p{IsHan}A-Za-z0-9·]+(?:[、,，][\\p{IsHan}A-Za-z0-9·]+)*)").matcher(text);
        if(named.find()) brands.addAll(List.of(named.group(1).split("[、,，]")));
        if(brands.isEmpty()) {
            var prefix=Pattern.compile("([\\p{IsHan}A-Za-z0-9·]{2,25})品牌(?:授权|代理)").matcher(text);
            if(prefix.find()) {
                String label=prefix.group(1).replaceFirst("^(?:(?:需|须|提供|具有|具备|取得|获得|持有))+", "")
                        .replaceFirst("^.*(?:每[个份项]|[0-9一二三四五六七八九十]+[个份项])", "");
                if(!label.isBlank()&&!label.matches("(?:每|任意|[0-9一二三四五六七八九十]+)(?:个|份|项)?")) brands.add(label);
            }
        }
        List<String> certificates="PERSON".equals(category)?CertificateScoreRule.identitiesForEstimate("",text):List.of();
        return new KnowledgeEvidenceConstraints(after,before,brandUntil,amount,
                "WAREHOUSE".equals(category)?ScoreItemConstraintExtractor.extractMinArea(text):null,
                "PROJECT".equals(category)?types:List.of(),
                "PROJECT".equals(category)?customers:List.of(),
                "WAREHOUSE".equals(category)?regions:List.of(),
                "WAREHOUSE".equals(category)?provinces:List.of(),
                "PERSON".equals(category)?positions:List.of(),
                "BRAND".equals(category)?List.copyOf(brands):List.of(),certificates,pending);
    }

    private static boolean declaresProjectType(String text,ProjectType type) {
        // Bare “其他” often describes additional materials, rather than a project type.
        boolean label=type==ProjectType.OTHER
                ? Pattern.compile("其他(?:类(?:型)?)?(?:项目|业绩|合同)|项目类型[为是：:\\s]*其他").matcher(text).find()
                : text.contains(type.displayName());
        return label || text.toUpperCase(java.util.Locale.ROOT).contains(type.name())
                || type==ProjectType.OFFICE && (text.contains("办公用品")||text.contains("办公类"))
                || type==ProjectType.INDUSTRIAL && text.toUpperCase(java.util.Locale.ROOT).contains("MRO");
    }
    private static String canonicalCustomer(String value) {
        if(value==null)return null;
        return switch(value) {
            case "民企", "民营企业", "民营" -> CustomerType.PRIVATE_ENTERPRISE.displayName();
            case "中央企业", "央企" -> CustomerType.CENTRAL_SOE.displayName();
            case "国企", "地方国企" -> CustomerType.LOCAL_SOE.displayName();
            default -> value;
        };
    }
    private static LocalDate coarseDate(String text,boolean upper) {
        var m=Pattern.compile("(\\d{4})年(?:(\\d{1,2})月)?").matcher(text);
        if(!m.matches())throw new IllegalArgumentException("日期范围未能解析");
        int year=Integer.parseInt(m.group(1));
        int month=m.group(2)==null?(upper?12:1):Integer.parseInt(m.group(2));
        try { var ym=java.time.YearMonth.of(year,month);return upper?ym.atEndOfMonth():ym.atDay(1); }
        catch(DateTimeException ex) { throw new IllegalArgumentException("日期范围无效",ex); }
    }
    private static List<String> terms(String text,List<String> options) {
        return options.stream().filter(text::contains).toList();
    }
    private static LocalDate date(java.util.regex.Matcher m,int start) {
        try { return LocalDate.of(Integer.parseInt(m.group(start)),Integer.parseInt(m.group(start+1)),Integer.parseInt(m.group(start+2))); }
        catch(DateTimeException ex) { throw new IllegalArgumentException("日期条件无效，请核验原文日期",ex); }
    }
    public boolean acceptsDate(LocalDate signingDate) {
        return signedAfter==null&&signedBefore==null || signingDate!=null
                && (signedAfter==null||!signingDate.isBefore(signedAfter))
                && (signedBefore==null||!signingDate.isAfter(signedBefore));
    }
    public boolean acceptsProject(LocalDate date,BigDecimal amount,String type,String customer) {
        boolean customerMatch=customers.isEmpty()||customer!=null&&customers.stream().anyMatch(c->
                canonicalCustomer(customer).contains(c));
        return acceptsDate(date) && (minAmount==null||amount!=null&&amount.compareTo(minAmount)>=0)
                && (types.isEmpty()||type!=null&&types.stream().anyMatch(type::contains)) && customerMatch;
    }
    public boolean acceptsWarehouse(String region,String province,BigDecimal area) {
        return (regions.isEmpty()||region!=null&&regions.stream().anyMatch(region::contains))
                && (provinces.isEmpty()||province!=null&&provinces.stream().anyMatch(province::contains))
                && (minArea==null||area!=null&&area.compareTo(minArea)>=0);
    }
    public boolean acceptsPerson(String title,List<String> heldCertificates) {
        return (positions.isEmpty()||title!=null&&positions.stream().anyMatch(title::contains))
                && certificates.stream().allMatch(id->heldCertificates!=null&&heldCertificates.stream().anyMatch(name->CertificateScoreRule.matchesEstimateIdentity(name,id)));
    }
    public boolean acceptsBrand(String name,LocalDate expiry) {
        return (brands.isEmpty()||name!=null&&brands.stream().anyMatch(name::contains))
                && (brandValidUntil==null||expiry!=null&&!expiry.isBefore(brandValidUntil));
    }
    public String description() {
        List<String> checked=new ArrayList<>();
        if(signedAfter!=null)checked.add("签约日期不早于"+signedAfter);
        if(signedBefore!=null)checked.add("签约日期不晚于"+signedBefore);
        if(brandValidUntil!=null)checked.add("品牌授权有效期不早于"+brandValidUntil);
        if(minAmount!=null)checked.add("合同金额不少于"+minAmount.toPlainString()+"元");
        if(minArea!=null)checked.add("仓库面积不少于"+minArea.toPlainString()+"平方米");
        if(!types.isEmpty())checked.add("项目类型"+String.join("、",types));
        if(!customers.isEmpty())checked.add("客户类型"+String.join("、",customers));
        if(!regions.isEmpty())checked.add("仓库区域"+String.join("、",regions));
        if(!provinces.isEmpty())checked.add("仓库省市"+String.join("、",provinces));
        if(!positions.isEmpty())checked.add("人员岗位"+String.join("、",positions));
        if(!certificates.isEmpty())checked.add("人员证书"+String.join("、",certificates));
        if(!brands.isEmpty())checked.add("品牌"+String.join("、",brands));
        return (checked.isEmpty()?"本项未提取到额外可执行筛选条件":"已筛选条件："+String.join("；",checked))
                +(pendingChecks.isEmpty()?"":"；待核验："+pendingChecks);
    }

    /** Each labelled fact in a mixed quote must comply; ambiguous record scope requires review. */
    public String quoteFailure(String quote) {
        if(quote==null)return "业绩证据缺失";
        if(!pendingChecks.isEmpty())return pendingChecks;
        if(signedAfter!=null||signedBefore!=null) {
            var dates=Pattern.compile("(?:合同)?签(?:订|约)(?:日期|时间)?\\s*[:：]?\\s*"+DATE).matcher(quote);
            boolean found=false;
            while(dates.find()) { found=true;if(!acceptsDate(date(dates,1)))return "业绩引用含不符合要求的签约日期，请逐份核验计分范围"; }
            if(!found)return "业绩引用缺少可核验签约日期";
        }
        if(minAmount!=null) {
            var amounts=Pattern.compile("(?:合同|协议)金额\\s*[:：为]?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(万元|亿元|元|万|亿)").matcher(quote);
            boolean found=false;
            while(amounts.find()) {
                found=true;
                BigDecimal value=ScoreItemConstraintExtractor.currencyAmount(amounts.group(1),amounts.group(2));
                if(value==null||value.compareTo(minAmount)<0)return "业绩引用合同金额不符合要求，请逐份核验计分范围";
            }
            if(!found)return "业绩引用缺少可核验合同金额";
        }
        return "";
    }
}
