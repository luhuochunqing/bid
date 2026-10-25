// Input: 招标原文或台账平台名
// Output: 按定义/投标上下文选择的具体平台候选与规范别名（纯函数）
// Pos: compliance/domain/policy/ - CA 平台名称提取与名称有效性边界
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 平台泛称不能成为企业未持有指定平台 CA 的证据。 */
public final class CaPlatformNamePolicy {
    private static final String PLATFORM_SUFFIX =
            "电子招标采购交易平台|电子招标采购交易网|招标采购交易平台|电子招投标交易平台|"
                    + "电子招标采购平台|电子招标采购系统|公共资源交易服务平台|公共资源交易中心|"
                    + "公共资源交易平台|电子商务平台|电子采购交易平台|电子招采平台|电子交易平台|"
                    + "交易平台|交易系统|公共服务平台|采购网";
    private static final Pattern PLATFORM = Pattern.compile(
            "([\\p{IsHan}A-Za-z0-9]{2,60}(?:" + PLATFORM_SUFFIX + ")|政采云|招采进宝|优质采)");
    private static final Pattern NAME_BOUNDARY = Pattern.compile(
            "^.*(?:通过|采用|登录|登陆|使用|访问|进入|依托|指定(?:为)?|名称(?:为)?|(?:本项目|投标人|供应商|须|应|需|按)[\\p{IsHan}]{0,40}在)[：:]?");
    private static final Pattern PREFIX = Pattern.compile("^(?:本项目|投标人|供应商|采购人|招标人|须|应|需|在|或称|以下简称|下称|简称)+");
    private static final Pattern GENERIC = Pattern.compile(
            "^(?:本|该|此|通用|指定|当前|目标|上述|相关|电子|招标|招投标|采购|公共|服务|网上|网络|交易|系统|平台)*$");
    private static final String REGION = "(?:北京|天津|上海|重庆|河北|山西|辽宁|吉林|黑龙江|江苏|浙江|安徽|福建|江西|山东|河南|湖北|湖南|"
            + "广东|海南|四川|贵州|云南|陕西|甘肃|青海|内蒙古|广西|西藏|宁夏|新疆|香港|澳门|台湾)(?:省|市)?";
    private static final Pattern REGIONAL_CA = Pattern.compile("(" + REGION + "\\s*CA(?:锁|数字证书|数字认证)?)");
    private static final Pattern VERIFIED_REGIONAL_IDENTITY = Pattern.compile(REGION
            + "(?:CA(?:锁|数字证书|数字认证)?|公共资源交易(?:服务)?(?:中心|平台)|政府采购网(?:[（(]政采云[）)])?)");
    private static final Pattern DEFINITION = Pattern.compile("^[）)]?\\s*[（(](?:以下)?(?:简称|下称|或称)");
    private static final Pattern OPERATING = Pattern.compile("投标|递交|上传|解密|CA|注册|登录|登陆|依托|通过|采用|指定");
    private static final Pattern PUBLICATION = Pattern.compile("发布|刊登|公告媒介");

    private CaPlatformNamePolicy() { }

    public static String clean(final String name) {
        if (name == null || name.isBlank()) return null;
        String value = NAME_BOUNDARY.matcher(name.trim()).replaceFirst("");
        value = PREFIX.matcher(value).replaceFirst("");
        String stem = value.replaceAll("(?:" + PLATFORM_SUFFIX + "|CA(?:锁|数字证书|数字认证)?)$", "");
        if (value.isBlank() || stem.isBlank() || GENERIC.matcher(stem).matches()) return null;
        return value;
    }

    /** 只对原文与台账已核实的同一平台全称/简称归一，不以机构相同推定平台互通。 */
    public static String identity(final String name) {
        String value = clean(name);
        if (value == null) return "";
        return switch (value) {
            case "招商局集团电子招标采购交易网", "招商局集团电子招标采购交易平台", "招商局集团电子招标采购平台",
                    "招商局集团招标采购交易平台", "招商局集团电子招采平台" -> "招商局集团电子招采平台";
            default -> value;
        };
    }

    /** 未覆盖的机构名称保留为候选，须人工核实，不以名称形态或台账 matched 标记推定身份。 */
    public static boolean hasVerifiedIdentity(final String name) {
        String value = identity(name);
        if (value.isEmpty()) return false;
        if (VERIFIED_REGIONAL_IDENTITY.matcher(value.replaceAll("\\s+", "")).matches()) return true;
        return switch (value) {
            case "招商局集团电子招采平台", "政采云", "招采进宝", "优质采", "国家电网", "国网",
                    "国家电网新一代电子商务平台", "国家电网新源电子商务平台", "南方电网", "南网",
                    "南方电网供应链统一服务平台" -> true;
            default -> false;
        };
    }

    static boolean hasRegionalCa(final String text) {
        return text != null && REGIONAL_CA.matcher(text).find();
    }

    /** 同优先级存在不同具体平台时返回全部候选，调用方必须转人工，不按全文顺序裁决。 */
    public static List<String> candidates(final String text) {
        if (text == null || text.isBlank()) return List.of();
        var names = new LinkedHashMap<String, String>();
        int priority = 0;
        for (String clause : text.split("[。；;，,\\r\\n]|(?=并(?:在|于)|同时(?:在|于))")) {
            Matcher matcher = PLATFORM.matcher(clause);
            while (matcher.find()) {
                String candidate = clean(matcher.group(1));
                if (candidate == null) continue;
                int rank = PUBLICATION.matcher(clause).find() ? 0
                        : DEFINITION.matcher(clause.substring(matcher.end())).find() ? 3
                        : OPERATING.matcher(clause).find() ? 2 : 1;
                if (rank < priority || rank == 0) continue;
                if (rank > priority) { names.clear(); priority = rank; }
                names.putIfAbsent(identity(candidate), candidate);
            }
            Matcher regional = REGIONAL_CA.matcher(clause);
            while (regional.find() && priority <= 1) {
                priority = 1;
                String name = regional.group(1).replaceAll("\\s+", "");
                names.putIfAbsent(identity(name), name);
            }
        }
        return List.copyOf(names.values());
    }
}
