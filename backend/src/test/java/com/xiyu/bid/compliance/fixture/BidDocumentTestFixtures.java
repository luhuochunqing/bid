package com.xiyu.bid.compliance.fixture;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.policy.TenderRequirements;

import java.util.ArrayList;
import java.util.List;

/**
 * 标书质量核查长标书验收测试夹具工厂类.
 *
 * <p>构造符合真实招投标卷册结构的 60 页、35,000+ 字符大型投标文件与招标文件，
 * 关键检查项线索精确分布在标书的前段、中段和后段（如第 16、25、48、49、52、56、58 页），
 * 用于全标书 RAG 语义检索与 LLM 结构化输出通道的端到端验收测试。
 */
public final class BidDocumentTestFixtures {

    private static final String FILLER_PARAGRAPH =
            "本章节内容为项目常规实施与组织管理支持性说明，展示投标人在日常运维、文档管理、后勤保障、"
            + "例会沟通、周报月报报送以及多方协同配合方面的标准化作业流程与通用规范。\n"
            + "项目组将严格遵循行业标准操作规程，配置专用办公资源与网络环境，确保项目在全生命周期内"
            + "具备高标准的沟通效率、文档可追溯性与交付质量管控能力。\n"
            + "投标人依托多年深耕行业的技术沉淀与大型系统交付经验，建立了完善的技术管理制度与服务保障体系，"
            + "从需求调研、架构设计、代码开发、系统测试到部署上线及后期运维，均设定量化质量考核指标。\n"
            + "针对本项目的高可用性与业务连续性要求，团队制定了完备的应急响应预案与定期巡检机制，"
            + "确保各项业务平稳运行与突发问题的高效闭环解决。\n";

    private BidDocumentTestFixtures() {
    }

    /**
     * 构造真实的 60 页、35,000+ 字符标准投标文件.
     *
     * @return 60 页完整结构化 DocumentContent 实例
     */
    public static DocumentContent createRealistic30kPlusBidDocument() {
        List<DocumentContent.PageContent> pages = new ArrayList<>(60);
        for (int p = 1; p <= 60; p++) {
            pages.add(buildRealisticPage(p, 2));
        }

        StringBuilder fullText = new StringBuilder(45000);
        for (DocumentContent.PageContent page : pages) {
            fullText.append(page.text()).append("\n\n");
        }

        return new DocumentContent(
                "大型综合数智化采购项目投标文件_西域数智.docx",
                "sha256_realistic_60p_bid_35k_plus",
                fullText.toString(),
                pages
        );
    }

    /**
     * 构造预算内 60 页标书（每页 ~450 字符，总字符量 ~27,000 字符 <= 60,000 预算）.
     */
    public static DocumentContent createWithinBudgetBidDocument() {
        List<DocumentContent.PageContent> pages = new ArrayList<>(60);
        for (int p = 1; p <= 60; p++) {
            pages.add(buildRealisticPage(p, 1));
        }

        StringBuilder fullText = new StringBuilder(30000);
        for (DocumentContent.PageContent page : pages) {
            fullText.append(page.text()).append("\n\n");
        }

        return new DocumentContent(
                "大型综合项目投标文件_预算内版.docx",
                "sha256_within_budget_60p_bid",
                fullText.toString(),
                pages
        );
    }

    /**
     * 构造超预算 60 页标书（每页 ~1350 字符，总字符量 ~81,000 字符 > 60,000 预算）.
     */
    public static DocumentContent createOverBudgetBidDocument() {
        List<DocumentContent.PageContent> pages = new ArrayList<>(60);
        for (int p = 1; p <= 60; p++) {
            pages.add(buildRealisticPage(p, 6));
        }

        StringBuilder fullText = new StringBuilder(90000);
        for (DocumentContent.PageContent page : pages) {
            fullText.append(page.text()).append("\n\n");
        }

        return new DocumentContent(
                "大型综合项目投标文件_超预算版.docx",
                "sha256_over_budget_60p_bid",
                fullText.toString(),
                pages
        );
    }

    /**
     * 构造标准招标文件（15 页结构化招标文件）.
     */
    public static DocumentContent createStandardTenderDocument() {
        List<DocumentContent.PageContent> pages = new ArrayList<>(15);
        pages.add(new DocumentContent.PageContent(1,
                "【招标文件第一章 招标公告】\n项目名称：西域数智化投标管理平台建设项目\n项目编号：XY-ZB-2026-0888\n招标人：西域智慧供应链集团\n",
                List.of("第一章 招标公告")));
        pages.add(new DocumentContent.PageContent(2,
                "【招标文件第二章 投标人须知前附表】\n"
                + "前附表条款1：项目总预算最高限价人民币壹仟万元整（￥10,000,000.00）。\n"
                + "前附表条款2：工期要求为合同签订后 90 日历天完成全系统上线。\n"
                + "前附表条款3：质保期要求为系统终验合格之日起免费维保 3 年。\n"
                + "前附表条款4：付款方式为按合同里程碑 3:3:3:1 支付，不得出现负偏离。\n",
                List.of("第二章 投标人须知前附表")));
        pages.add(new DocumentContent.PageContent(5,
                "【招标文件第五章 资格审查与一票否决条款】\n"
                + "强制性资格要求：投标人须具备国家认证的 ISO9001 质量管理体系认证证书（甲级）、CMMI5 认证及信息安全服务资质一级证书。\n"
                + "一票否决项：投标人未提供核心设备/软件制造商原厂授权书者，作废标处理。\n"
                + "实质性条款：存在任何实质性负偏离或未响应前附表关键条款者，作废标处理。\n",
                List.of("第五章 资格审查与一票否决条款")));
        pages.add(new DocumentContent.PageContent(8,
                "【招标文件第八章 评标办法与技术评分细则】\n"
                + "技术方案评分细则（满分 35 分）：\n"
                + "1. AI 智能分析引擎与微服务高可用架构设计方案（20 分）：具备多模态解析、语义召回算法与同城双活容灾方案者得满分。\n"
                + "2. 项目实施组织设计与售后服务体系方案（15 分）：实施计划详尽周密、配备专业驻场团队与 7x24 小时服务响应机制者得满分。\n",
                List.of("第八章 评标办法与技术评分细则")));
        pages.add(new DocumentContent.PageContent(15,
                "【招标文件第 1 号澄清答疑公告与补遗书】\n"
                + "澄清事项：针对投标人提出的系统安全等级保护与分布式数据库兼容性要求，招标人确认需支持国密算法与信创环境。"
                + "请各投标人于投标文件中出具针对第 1 号澄清答疑文件的点对点响应确认函。\n",
                List.of("第 1 号澄清答疑公告与补遗书")));

        for (int p = 3; p <= 14; p++) {
            if (p != 5 && p != 8) {
                pages.add(new DocumentContent.PageContent(p,
                        "招标文件第 " + p + " 章通用规范与标准条款约定。\n" + FILLER_PARAGRAPH,
                        List.of("第 " + p + " 章通用规范")));
            }
        }
        pages.sort((a, b) -> Integer.compare(a.pageNumber(), b.pageNumber()));

        StringBuilder full = new StringBuilder();
        pages.forEach(pg -> full.append(pg.text()).append("\n\n"));
        return new DocumentContent("招标文件_标准版.docx", "sha256_tender_standard", full.toString(), pages);
    }

    /**
     * 构造标准招标结构化要求.
     */
    public static TenderRequirements createStandardTenderRequirements() {
        return new TenderRequirements(
                List.of(
                        new TenderRequirements.ScoringItem("AI 智能分析引擎与微服务高可用架构设计方案", 20.0, "提供语义召回与同城双活方案"),
                        new TenderRequirements.ScoringItem("实施组织与售后运维服务体系", 15.0, "配备专业驻场团队与7x24小时响应")
                ),
                List.of("ISO9001质量管理体系认证（甲级）", "CMMI5", "信息安全服务资质一级"),
                List.of(
                        new TenderRequirements.VetoClause("未提供制造商原厂授权书者废标", List.of("原厂授权书", "制造商授权")),
                        new TenderRequirements.VetoClause("存在实质性负偏离者废标", List.of("负偏离", "不满足"))
                ),
                List.of("包含近三年财务审计报告与类似业绩合同关键页"),
                List.of("投标人须知前附表关键条款响应表格式规范，加盖公章与法人章"),
                false
        );
    }

    /** 单页文本与标题生成核心分派. */
    private static DocumentContent.PageContent buildRealisticPage(final int pageNumber, final int fillerMultiplier) {
        String filler = FILLER_PARAGRAPH.repeat(Math.max(1, fillerMultiplier));
        return switch (pageNumber) {
            case 1 -> new DocumentContent.PageContent(1,
                    "【投标文件 第一分卷：商务与资格卷】\n"
                    + "项目名称：西域数智化投标管理平台建设项目\n"
                    + "项目编号：XY-ZB-2026-0888\n"
                    + "投标人名称：西域数智化供应链技术有限公司\n"
                    + "统一社会信用代码：91310000775244192P\n"
                    + "法定代表人：张西域\n"
                    + "投标日期：2026年08月30日\n"
                    + "投标总报价：人民币玖佰捌拾万元整（￥9,800,000.00）\n" + filler,
                    List.of("投标文件封面"));
            case 2 -> new DocumentContent.PageContent(2,
                    "【第一章 投标函】\n"
                    + "致：西域智慧供应链集团（招标人）\n"
                    + "1. 我方已仔细研究了「西域数智化投标管理平台建设项目」（项目编号：XY-ZB-2026-0888）招标文件的全部内容。\n"
                    + "2. 我方愿以投标总报价人民币玖佰捌拾万元整（￥9,800,000.00）承担本项目的全部建设与交付任务。\n"
                    + "3. 我方承诺工期为 90 日历天完成全系统上线，工程质量达到合格及以上标准，完全满足招标文件要求。\n"
                    + "4. 本投标文件投标有效期为自投标截止之日起 90 日历天。\n"
                    + "投标人：西域数智化供应链技术有限公司（盖章）\n法定代表人或授权委托人：（签字或盖章）\n" + filler,
                    List.of("第一章 投标函"));
            case 3 -> new DocumentContent.PageContent(3,
                    "【第一章 投标函附录】\n"
                    + "条款号 / 条款名称 / 招标文件要求 / 投标文件响应承诺：\n"
                    + "1. 履约担保金额：中标合同金额的 10%，采用银行保函形式。\n"
                    + "2. 付款方式响应：合同签订生效后支付30%预付款，中期验收合格支付30%，终验合格支付30%，质保期满支付10%尾款。\n"
                    + "3. 缺陷责任与免费维保期：自系统终验合格并取得验收证书之日起 3 年（36 个月）。\n" + filler,
                    List.of("第一章 投标函附录"));
            case 4 -> new DocumentContent.PageContent(4,
                    "【第二章 法定代表人身份证明书】\n"
                    + "单位名称：西域数智化供应链技术有限公司\n"
                    + "法定代表人姓名：张西域  性别：男  年龄：45岁  职务：总经理\n"
                    + "身份证号码：31010419810818291X\n"
                    + "特此证明张西域同志系我公司法定代表人，代表我公司签署投标文件、合同及处理一切相关事宜。\n"
                    + "投标人：西域数智化供应链技术有限公司（加盖公章）\n日期：2026年08月30日\n" + filler,
                    List.of("第二章 法定代表人身份证明书"));
            case 5 -> new DocumentContent.PageContent(5,
                    "【第二章 授权委托书】\n"
                    + "本授权委托书声明：法定代表人张西域特授权项目总监李数智（身份证号：310115198805126633）为我公司合法代理人，"
                    + "全权代表我公司办理「西域数智化投标管理平台建设项目」投标、谈判、签约等相关事宜，本授权书自签署之日起 90 天内有效。\n"
                    + "法定代表人：（签字盖章）  被授权人：（签字）\n加盖单位公章：西域数智化供应链技术有限公司\n" + filler,
                    List.of("第二章 授权委托书"));
            case 6 -> new DocumentContent.PageContent(6,
                    "【第三章 营业执照与基础登记信息】\n"
                    + "企业名称：西域数智化供应链技术有限公司\n"
                    + "统一社会信用代码：91310000775244192P\n"
                    + "公司类型：有限责任公司（自然人投资或控股）\n"
                    + "注册资本：人民币伍仟万元整（￥50,000,000.00）\n"
                    + "经营范围：计算机软硬件技术开发、技术咨询、大数据与人工智能应用软件开发、供应链数字化系统集成等。\n"
                    + "营业期限：2015年03月18日 至 无固定期限（长期有效）。\n" + filler,
                    List.of("第三章 营业执照与基础登记信息"));
            case 7 -> new DocumentContent.PageContent(7,
                    "【第四章 开标一览表与报价明细汇总表】\n"
                    + "项目编号：XY-ZB-2026-0888  项目名称：西域数智化投标管理平台建设项目\n"
                    + "1. 投标总报价（大写）：人民币玖佰捌拾万元整\n"
                    + "2. 投标总报价（小写）：￥9,800,000.00 元（币种：人民币 CNY）\n"
                    + "3. 分项报价明细：AI 智能核查核心引擎 3,500,000.00 元；业务中台集成 2,800,000.00 元；"
                    + "前端交付与定制 2,000,000.00 元；实施与 3 年维保服务 1,500,000.00 元。\n"
                    + "大小写金额完全一致，无算术性错误，未超过最高限价。\n" + filler,
                    List.of("第四章 开标一览表与报价明细汇总表"));
            case 8 -> new DocumentContent.PageContent(8,
                    "【第五章 投标保证金缴纳凭证与退款账户信息】\n"
                    + "投标保证金汇款凭证：\n"
                    + "付款人名称：西域数智化供应链技术有限公司（基本账户）\n"
                    + "付款人开户行：中国工商银行上海市陆家嘴支行  账号：1001202609008888888\n"
                    + "收款人：西域智慧供应链集团保证金专户  汇款金额：人民币伍拾万元整（￥500,000.00）\n"
                    + "回款备注/摘要：XY-ZB-2026-0888投标保证金\n" + filler,
                    List.of("第五章 投标保证金缴纳凭证与退款账户信息"));
            case 16 -> new DocumentContent.PageContent(16,
                    "【第八章 资格性评审：企业同类项目业绩一览表】\n"
                    + "类似业绩合同证明材料汇总：\n"
                    + "1. 业绩合同一：国家能源集团数字化智能投标中台系统（合同金额：1280 万元，签署日期：2024年6月，已终验并提供业主良好履约评价函）。\n"
                    + "2. 业绩合同二：中铁装备集团供应链协同与智能风控平台（合同金额：860 万元，签署日期：2025年2月，已稳定运行）。\n"
                    + "包含近三年同类业绩合同关键页、中标通知书及验收证书复印件，完全满足资格性评审条款响应要求。\n" + filler,
                    List.of("第八章 资格性评审：企业同类项目业绩一览表"));
            case 25 -> new DocumentContent.PageContent(25,
                    "【第十二章 技术方案架构：AI 智能分析引擎与微服务高可用架构设计】\n"
                    + "响应招标文件技术评分标准与评分细则（满分 35 分标准响应）：\n"
                    + "1. AI 智能分析引擎架构设计：采用多阶段语义页面检索（RAG）结合大语言模型结构化输出技术，"
                    + "实现对标书封面、偏离表、资质认证、答疑确认函的全标书深度分析与真实页码定位。\n"
                    + "2. 微服务与同城双活高可用架构：后端基于 Java 21 与 Spring Boot 3 分布式微服务，具备同城双活容灾与故障自愈能力，"
                    + "系统 SLA 承诺达到 99.99%，完全满足并优于评分标准。\n" + filler,
                    List.of("第十二章 技术方案架构：AI 智能分析引擎与微服务高可用架构设计"));
            case 48 -> new DocumentContent.PageContent(48,
                    "【第二十一章 资质附件：ISO 管理体系认证证书分卷】\n"
                    + "企业资质证书与体系认证证明材料：\n"
                    + "1. ISO9001 质量管理体系认证（甲级）证书（证书编号：00123Q34567R0M，认证范围覆盖软件开发与系统集成，处于有效期内）。\n"
                    + "2. ISO14001 环境管理体系认证证书（证书编号：00123E34567R0M，有效期至 2028年）。\n"
                    + "3. ISO45001 职业健康安全管理体系认证证书（证书编号：00123S34567R0M，有效期至 2028年）。\n"
                    + "以上证书均通过国家认证认可监督管理委员会（CNCA）官方查验有效，完全满足招标文件资质匹配要求。\n" + filler,
                    List.of("第二十一章 资质附件：ISO 管理体系认证证书分卷"));
            case 49 -> new DocumentContent.PageContent(49,
                    "【第二十二章 实质性条款响应：商务偏离表与技术偏离表】\n"
                    + "1. 商务偏离表：\n"
                    + "序号 / 招标文件商务条款 / 投标文件响应内容 / 偏离说明\n"
                    + "1.1 / 投标总报价及限价要求 / 投标报价为 980 万元，低于最高限价 / 无偏离\n"
                    + "1.2 / 支付条款与里程碑付款 / 付款方式无负偏离，完全满足招标文件要求 / 无偏离\n"
                    + "1.3 / 工期要求 90 日历天 / 承诺 90 日历天完成上线交付 / 无偏离\n"
                    + "2. 技术偏离表：\n"
                    + "对招标文件全部技术规范逐项核对，本投标文件全部指标无负偏离，无不满足项，完全满足响应性评审条款要求。\n" + filler,
                    List.of("第二十二章 实质性条款响应：商务偏离表与技术偏离表"));
            case 52 -> new DocumentContent.PageContent(52,
                    "【第二十四章 投标人须知前附表关键条款响应表与承诺盖章表】\n"
                    + "投标人须知前附表关键条款响应表：\n"
                    + "前附表关键条款逐项完全响应，无空项、无漏项。项目概况、标段划分、工期 90 天、质量标准合格、质保期 3 年全部完全响应。\n"
                    + "承诺书盖章合规声明：本投标文件所有承诺类文件、声明书均已加盖投标人单位公章及法定代表人印章。\n"
                    + "投标人：西域数智化供应链技术有限公司（加盖企业公章）\n法定代表人：张西域（加盖印章）\n" + filler,
                    List.of("第二十四章 投标人须知前附表关键条款响应表与承诺盖章表"));
            case 56 -> new DocumentContent.PageContent(56,
                    "【第二十六章 澄清与答疑文件响应承诺函】\n"
                    + "致招标人：关于招标文件第 1 号澄清答疑文件的确认与点对点响应函\n"
                    + "本投标人已完整收悉招标人于 2026 年 8 月发布的第 1 号澄清答疑文件与补遗书，"
                    + "针对第 1 号澄清公告中关于国密算法支持与信创适配要求已完成点对点响应与技术架构升级，"
                    + "特此出具正式确认函完全响应，承诺全面履行澄清文件约定的各项技术与商务要求。\n"
                    + "投标人：西域数智化供应链技术有限公司（盖章）\n" + filler,
                    List.of("第二十六章 澄清与答疑文件响应承诺函"));
            case 58 -> new DocumentContent.PageContent(58,
                    "【第二十八章 高等级资质附件与原厂授权书】\n"
                    + "资格条件与一票否决项关键证明材料：\n"
                    + "1. CMMI5 软件能力成熟度模型集成五级认证证书（国际主任评估师签发，处于有效期内）。\n"
                    + "2. 中国网络安全审查技术与认证中心颁发的信息安全服务资质一级证书（安全工程类一级）。\n"
                    + "3. 核心硬件与基础软件设备制造商出具的正规原厂授权书及售后服务承诺函，完全满足一票否决强制性要求。\n" + filler,
                    List.of("第二十八章 高等级资质附件与原厂授权书"));
            default -> new DocumentContent.PageContent(pageNumber,
                    "【第 " + pageNumber + " 章 项目标准化实施与交付管理规范】\n"
                    + "本页属于投标文件第 " + pageNumber + " 页常规文档内容。\n" + filler,
                    List.of("第 " + pageNumber + " 章 项目标准化实施与交付管理规范"));
        };
    }
}
