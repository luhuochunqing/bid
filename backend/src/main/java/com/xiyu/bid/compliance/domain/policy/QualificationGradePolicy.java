// Input: 招标资质要求字符串 / 资质证书名称与等级
// Output: GradeAssessment 等级兼容性评估结果（纯核心，FP-Java 纯函数）
// Pos: compliance/domain/policy/ - 资质等级层级判定纯核心策略
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 资质等级层级判定纯核心策略（FP-Java 纯函数）.
 *
 * <p>支持四大主流资质等级层级体系的判定与向下兼容覆盖：
 * 1. 工程施工承包体系：特级(4) > 一级(3) > 二级(2) > 三级(1)
 * 2. 设计/勘察/监理体系：综合资质/综合甲级(4) > 甲级(3) > 乙级(2) > 丙级(1) > 丁级(0)
 * 3. 网络安全等级保护体系：等保四级(4) > 等保三级(3) > 等保二级(2) > 等保一级(1)
 * 4. 软件能力成熟度体系：CMMI5(5) > CMMI4(4) > CMMI3(3) > CMMI2(2) > CMMI1(1)
 */
public final class QualificationGradePolicy {

    public record GradeAssessment(
            boolean compatible,
            String reqGrade,
            String certGrade,
            String remark) {

        public static GradeAssessment compatible(final String reqGrade, final String certGrade, final String remark) {
            return new GradeAssessment(true, reqGrade, certGrade, remark);
        }

        public static GradeAssessment incompatible(final String reqGrade, final String certGrade, final String remark) {
            return new GradeAssessment(false, reqGrade, certGrade, remark);
        }

        public static GradeAssessment unconstrained() {
            return new GradeAssessment(true, null, null, "");
        }
    }

    private enum SystemType {
        CONSTRUCTION,
        DESIGN,
        SECURITY_CLASSIFIED,
        CMMI
    }

    private record ExtractedGrade(SystemType system, int rank, String gradeName) {
    }

    private static final Pattern CMMI_PATTERN = Pattern.compile(
            "(?:cmmi|itss|csbm)[\\-\\s]*(?:dev)?[\\-\\s]*([1-5]|一|二|三|四|五)(?:级)?", Pattern.CASE_INSENSITIVE);

    private static final Pattern SECURITY_PATTERN = Pattern.compile(
            "(?:等保|等级保护|信息系统安全等级保护)[\\s第]*([1-4]|一|二|三|四)(?:级)?");

    private static final Pattern DESIGN_PATTERN = Pattern.compile(
            "(综合甲级|综合资质|甲级|乙级|丙级|丁级)");

    private static final Pattern CONSTRUCTION_PATTERN = Pattern.compile(
            "(特级|一级|二级|三级|[1-3]级|壹级|贰级|叁级)");

    private QualificationGradePolicy() {
    }

    public static boolean isGradeCompatible(final String req, final String certName) {
        return assessGrade(req, certName, null).compatible();
    }

    public static GradeAssessment assessGrade(final String req, final String certName) {
        return assessGrade(req, certName, null);
    }

    public static GradeAssessment assessGrade(final String req, final String certName, final String certLevel) {
        ExtractedGrade reqGrade = extractGrade(req);
        String fullCertText = (certName == null ? "" : certName) + " " + (certLevel == null ? "" : certLevel);
        ExtractedGrade certGrade = extractGrade(fullCertText);

        if (reqGrade == null) {
            return GradeAssessment.unconstrained();
        }
        if (certGrade == null) {
            return GradeAssessment.compatible(reqGrade.gradeName(), null, "证书未标明等级，需结合证书明细核验");
        }
        if (reqGrade.system() != certGrade.system()) {
            return GradeAssessment.incompatible(reqGrade.gradeName(), certGrade.gradeName(),
                    "资质等级体系不匹配（招标要求【" + reqGrade.gradeName() + "】，证书为【" + certGrade.gradeName() + "】）");
        }
        if (certGrade.rank() >= reqGrade.rank()) {
            String note = certGrade.rank() > reqGrade.rank()
                    ? "企业资质等级【" + certGrade.gradeName() + "】优于招标要求【" + reqGrade.gradeName() + "】，满足要求"
                    : "企业资质等级【" + certGrade.gradeName() + "】满足招标要求【" + reqGrade.gradeName() + "】";
            return GradeAssessment.compatible(reqGrade.gradeName(), certGrade.gradeName(), note);
        }
        return GradeAssessment.incompatible(reqGrade.gradeName(), certGrade.gradeName(),
                "资质等级不足（企业资质等级【" + certGrade.gradeName() + "】低于招标要求【" + reqGrade.gradeName() + "】）");
    }

    private static ExtractedGrade extractGrade(final String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String clean = text.trim();

        // 1. CMMI
        Matcher cmmi = CMMI_PATTERN.matcher(clean);
        if (cmmi.find()) {
            int rank = parseNumber(cmmi.group(1));
            return new ExtractedGrade(SystemType.CMMI, rank, "CMMI" + rank + "级");
        }

        // 2. 等保
        Matcher sec = SECURITY_PATTERN.matcher(clean);
        if (sec.find()) {
            int rank = parseNumber(sec.group(1));
            return new ExtractedGrade(SystemType.SECURITY_CLASSIFIED, rank, "等保" + rank + "级");
        }

        // 3. 设计/咨询
        Matcher des = DESIGN_PATTERN.matcher(clean);
        if (des.find()) {
            String name = des.group(1);
            int rank = switch (name) {
                case "综合甲级", "综合资质" -> 4;
                case "甲级" -> 3;
                case "乙级" -> 2;
                case "丙级" -> 1;
                default -> 0;
            };
            return new ExtractedGrade(SystemType.DESIGN, rank, name);
        }

        // 4. 工程施工承包
        Matcher con = CONSTRUCTION_PATTERN.matcher(clean);
        if (con.find()) {
            String name = con.group(1);
            int rank = switch (name) {
                case "特级" -> 4;
                case "一级", "1级", "壹级" -> 3;
                case "二级", "2级", "贰级" -> 2;
                default -> 1;
            };
            return new ExtractedGrade(SystemType.CONSTRUCTION, rank, name);
        }

        return null;
    }

    private static int parseNumber(final String text) {
        return switch (text) {
            case "5", "五" -> 5;
            case "4", "四" -> 4;
            case "3", "三" -> 3;
            case "2", "二" -> 2;
            default -> 1;
        };
    }
}
