// Input: BusinessQualification + level + alertDays + jump link base url
// Output: 蓝图 §4.1.3.8 资质到期提醒消息模板（Title + Body）
// Pos: Application/提醒消息模板（纯数据 record）
// 维护声明: 模板文案严格对齐蓝图 §4.1.3.8 消息模板章节，不引入文案外行为.
package com.xiyu.bid.businessqualification.application.view;

import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * §4.1.3.8 资质到期提醒消息模板。
 * <p>
 * 标题格式：【资质到期提醒】《证书名》还有 X 天到期
 * 正文必须包含：①证书名称 ②证书号 ③等级 ④认证机构 ⑤代理机构 ⑥代理联系方式 ⑦有效期至 ⑧剩余天数 ⑨跳转详情链接
 *
 * <p>纯数据 record；不读写数据库、不读时间（remainingDays 与 level 由调用方传入）。
 */
public record QualificationExpiryAlertMessage(
        String title,
        String body
) {

    /** 正文最大长度（保留 notification body 10_000 上限的安全余量）。 */
    private static final int MAX_BODY_LENGTH = 9_500;

    public static QualificationExpiryAlertMessage from(
            BusinessQualification qualification, long remainingDays, String level, String detailUrl) {
        if (qualification == null) {
            throw new IllegalArgumentException("qualification must not be null");
        }
        String name = nullSafe(qualification.name(), "(未命名证书)");
        String title = String.format("【资质到期提醒】《%s》还有 %d 天到期", name, remainingDays);

        String certNo = nullSafe(qualification.certificateNo(), "—");
        String levelText = nullSafe(level, "—");
        String issuer = nullSafe(qualification.issuer(), "—");
        String agency = nullSafe(qualification.agency(), "—");
        String agencyContact = nullSafe(qualification.agencyContact(), "—");
        String expiry = qualification.validityPeriod() != null
                && qualification.validityPeriod().getExpiryDate() != null
                ? qualification.validityPeriod().getExpiryDate().toString()
                : "—";
        String link = nullSafe(detailUrl, buildDefaultLink(qualification.id()));

        String body = String.join("\n",
                "尊敬的用户，您负责/关注的资质证书即将到期：",
                "① 证书名称：" + name,
                "② 证书号：" + certNo,
                "③ 等级：" + levelText,
                "④ 认证机构：" + issuer,
                "⑤ 代理机构：" + agency,
                "⑥ 代理联系方式：" + agencyContact,
                "⑦ 有效期至：" + expiry,
                "⑧ 剩余天数：" + remainingDays + " 天",
                "⑨ 跳转详情：" + link
        );

        if (body.length() > MAX_BODY_LENGTH) {
            body = body.substring(0, MAX_BODY_LENGTH);
        }
        return new QualificationExpiryAlertMessage(title, body);
    }

    public static String buildDefaultLink(Long qualificationId) {
        if (qualificationId == null) {
            return "/knowledge/qualification";
        }
        return "/knowledge/qualification?id=" + qualificationId;
    }

    public static long computeRemainingDays(LocalDate expiryDate, LocalDate today) {
        if (expiryDate == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(today, expiryDate);
    }

    private static String nullSafe(String raw, String fallback) {
        return (raw == null || raw.isBlank()) ? fallback : raw;
    }
}
