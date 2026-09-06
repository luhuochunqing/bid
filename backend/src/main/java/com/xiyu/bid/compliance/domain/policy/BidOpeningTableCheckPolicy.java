// Input: 投标文件分页内容 (DocumentContent) + 可选开标一览表要求
// Output: 开标一览表/报价表核查结果 (QualityCheckItemResult: PASS / FAIL / MANUAL_REVIEW)
// Pos: compliance/domain/policy/ - 开标一览表与报价表核验策略（纯核心 FP-Java）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标书开标一览表/报价表核查策略 (FP-Java 纯核心).
 */
public final class BidOpeningTableCheckPolicy {

    private static final String[] TABLE_HEADERS = {
            "开标一览表", "投标一览表", "报价一览表", "开标报价表", "投标报价一览表", "投标报价表"
    };

    private static final String[] STRUCTURE_ELEMENTS = {
            "项目名称", "项目编号", "投标报价", "投标总价", "投标总报价", "交货期", "工期", "服务期", "法定代表人", "授权代表", "盖章", "签字", "分项报价", "大写", "小写"
    };

    private static final Pattern UNFILLED_PLACEHOLDER_PATTERN = Pattern.compile(
            "【此处(?:填写|粘贴|附).*?(?:一览表|报价表).*?】|"
                    + "（此处(?:填写|粘贴|附).*?(?:一览表|报价表).*?）|"
                    + "\\[此处(?:填写|粘贴|附).*?(?:一览表|报价表).*?\\]|"
                    + "开标一览表(?:附后|见下页|另附)|"
                    + "(?:投标总价|投标报价)[^\\n\\r]{0,20}[_—]{4,}"
    );

    private static final Pattern PRICE_NUMBER_PATTERN = Pattern.compile(
            "([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:元)?"
    );

    private BidOpeningTableCheckPolicy() {
    }

    // REUSE-OK: * — 质检开标一览表核查入口，业务域独立
    public static QualityCheckItemResult check(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.BID_OPENING_TABLE;
        if (bidDoc == null || bidDoc.pages().isEmpty()) {
            return QualityCheckItemResult.fail(item, "投标文件为空，未检测到开标一览表", "请上传完整的投标文件正文分卷", Collections.emptyList());
        }

        DocumentContent.PageContent placeholderPage = findPlaceholderPage(bidDoc);
        if (placeholderPage != null) {
            List<QualityEvidence> evidences = List.of(QualityEvidence.bid(
                    "开标一览表占位页", placeholderPage.pageNumber(),
                    BasicInfoCheckPolicy.snippet(placeholderPage.text(), 200)));
            return QualityCheckItemResult.fail(
                    item,
                    "开标一览表存在未填写的占位模板或空白下划线，未实质响应报价要求！",
                    "请根据招标文件格式要求完整填写开标一览表并加盖公章与签字",
                    evidences);
        }

        DocumentContent.PageContent tablePage = findBestTablePage(bidDoc);
        if (tablePage != null) {
            List<QualityEvidence> evidences = List.of(QualityEvidence.bid(
                    "开标一览表所在页", tablePage.pageNumber(),
                    BasicInfoCheckPolicy.snippet(tablePage.text(), 250)));
            return QualityCheckItemResult.pass(
                    item,
                    "包含规范完整的开标一览表，结构要素与关键字段齐全",
                    evidences);
        }

        // 仅在目录中提及但正文未找到
        boolean mentionedInCatalog = bidDoc.pages().stream()
                .anyMatch(p -> BidLetterCheckPolicy.isCatalogOrOutlinePage(p.text()) && containsAny(p.text(), TABLE_HEADERS));
        if (mentionedInCatalog) {
            return QualityCheckItemResult.fail(
                    item,
                    "仅在目录中检索到开标一览表条目，正文中未检测到正式填写的开标一览表！",
                    "请在投标文件中加入实质填写的《开标一览表》章节",
                    Collections.emptyList());
        }

        return QualityCheckItemResult.fail(
                item,
                "未检测到开标一览表或投标报价一览表",
                "请根据招标文件要求在投标文件中补充《开标一览表》",
                Collections.emptyList());
    }

    /**
     * 从开标一览表页面中提取投标总报价数值（用于与投标函及最高限价联动核对）.
     */
    public static BigDecimal extractTablePrice(final DocumentContent bidDoc) {
        if (bidDoc == null) {
            return null;
        }
        DocumentContent.PageContent tablePage = findBestTablePage(bidDoc);
        if (tablePage == null) {
            return null;
        }
        return parsePriceFromPage(tablePage.text());
    }

    private static DocumentContent.PageContent findBestTablePage(final DocumentContent bidDoc) {
        for (DocumentContent.PageContent page : bidDoc.pages()) {
            String text = page.text();
            if (BidLetterCheckPolicy.isCatalogOrOutlinePage(text)) {
                continue;
            }
            if (UNFILLED_PLACEHOLDER_PATTERN.matcher(text).find()) {
                continue;
            }
            if (!containsAny(text, TABLE_HEADERS)) {
                continue;
            }
            // 真实表格要素校验：至少命中 2 个关键要素
            int hitCount = 0;
            for (String elem : STRUCTURE_ELEMENTS) {
                if (text.contains(elem)) {
                    hitCount++;
                }
            }
            if (hitCount >= 2) {
                return page;
            }
        }
        return null;
    }

    private static DocumentContent.PageContent findPlaceholderPage(final DocumentContent bidDoc) {
        for (DocumentContent.PageContent page : bidDoc.pages()) {
            String text = page.text();
            if (BidLetterCheckPolicy.isCatalogOrOutlinePage(text)) {
                continue;
            }
            if (containsAny(text, TABLE_HEADERS) && UNFILLED_PLACEHOLDER_PATTERN.matcher(text).find()) {
                return page;
            }
        }
        return null;
    }

    private static BigDecimal parsePriceFromPage(final String pageText) {
        if (pageText == null || pageText.isBlank()) {
            return null;
        }
        // 查找包含“总价”或“投标报价”或“元”后跟随的数字
        for (String line : pageText.split("\\R")) {
            if (line.contains("总价") || line.contains("报价") || line.contains("总报价")) {
                Matcher m = PRICE_NUMBER_PATTERN.matcher(line);
                while (m.find()) {
                    String num = m.group(1).replace(",", "");
                    if (num.matches("^[0-9]+(\\.[0-9]{1,2})?$")) {
                        BigDecimal val = new BigDecimal(num);
                        if (val.compareTo(BigDecimal.valueOf(100)) > 0) {
                            return val;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean containsAny(final String text, final String... keywords) {
        if (text == null) return false;
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
