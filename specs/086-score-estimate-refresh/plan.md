# 知识库满足度预计得分 V1 Implementation Plan

Goal：按用户确认的“有/无/部分比例”恢复可用的预计得分，先让已支持知识库项有数值。
Architecture：复用五类匹配服务和现有JPA资料源；PartialScorePolicy纯计算M/N×weight；EstimatedScoreService只编排；复用ScoreStatusPolicy、CAS保存及刷新回执。零新依赖、零迁移、零新HTTP接口。
Tech Stack：Java21 / Spring Boot / JPA / MySQL8 / Vue3 / Vitest。
Spec：specs/086-score-estimate-refresh/spec.md（最新版为唯一当前口径）。
Controller：none；已按用户批准完成S4→清理→S5→S6，本地working_tree交付，不自动commit。

## 原方案处置

- 保留：因素名兼容、全部项刷新、权限、CAS、摘要竞态保护、证书输入一致性。
- 删除：本分支ScoreEstimateLimitPolicy及其测试；新口径下“复杂细则必须整项无分”的测试断言按规格替换，保留历史RED作为旧方案证据。
- 改用：预计分走简单计数；不进入固定六区域公式、不以严格证书全文语法作为出粗估分的前提。
- 保留共享契约：ScoreItemAssessor的computeStage2Score及正式评分链不变。strict match接口既有防护不放宽；严格策略是否仍有调用须先rg核对，只有无调用的新增/临时代码才删除。
- 先前.omc/review-1371.md和141项结果仅对应旧口径；新实现必须重新RED/GREEN、清理和审查。

## 本轮实施范围

| 文件 | 责任 |
|---|---|
| backend/src/main/java/com/xiyu/bid/scoreparse/domain/PartialScorePolicy.java | 增加estimateByCount(weight,M,N)，不改computeStage2Score |
| backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreItemConstraintExtractor.java | 数量目标提取，只接受明确计数，移除用于粗估的“首个数字”兜底 |
| backend/src/main/java/com/xiyu/bid/scoreparse/application/EstimatedScoreService.java | 各类统一取M/N计分，撤除严格短路及固定六区分值分支 |
| backend/src/main/java/com/xiyu/bid/scoreparse/application/CertificateScoreEstimateSupport.java | 简单证书身份去重计数，保持新鲜度封印字段 |
| backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreEstimateFreshness.java | 与新粗估输入/版本同步 |
| backend/src/main/java/com/xiyu/bid/scoreparse/application/match/{Cert,Person,Project,Warehouse,Brand}MatchService.java | 复用既有repository/spec；只修改实际需要的粗估取数和去重，避免泛因素名被当具体品牌/仓名关键词。严格match不变 |
| backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreEstimateLimitPolicy.java | 删除本分支新增文件 |
| src/views/Project/stages/components/ScoreParseDrawer.vue | 更正为知识库粗估说明，保留当前统计和按钮 |
| src/views/Project/stages/components/ScoreItemDetailModal.vue | 预计分依据与零分显示，实际模式不变 |
| 对应PartialScorePolicyTest、EstimatedScoreServiceTest、ScoreEstimateFreshnessTest、ScoreEstimateRefreshMysqlTest及各匹配测试 | 正反例与真实边界 |
| src/views/Project/stages/components/ScoreParseDrawer.spec.js、src/composables/projectDetail/useScoreEstimateRefresh.spec.js | 数值0/部分分/摘要与实际模式不变 |
| specs/086-score-estimate-refresh/及已有Wiki/教训条目 | 需求、测试证据和交付口径 |

不预先新建中间服务、统一证据框架或规则注册表。新增文件仅限现有测试确实无法承载的必要回归；数量解析职责已有ScoreItemConstraintExtractor，不另造第二解析器。

## 切片一：纯计分与取数（T013—T014）

- [x] 先在PartialScorePolicyTest写RED：15×2/3=10.00；1×1/2=0.50；10×0/1=0；10×3/2=10。
- [x] 先以现有`compute(new BigDecimal("1"), "PARTIAL", 50, "OBJECTIVE")`应等于`0.50`锁定业务RED，执行`mvn -q -Dtest=PartialScorePolicyTest test`观察现有最小1分钳位失败；新方法不存在导致的编译错误不计RED。随后引入下面的数量入口并补M/N精度、封顶等断言。
- [x] 在现有PartialScorePolicy新增以下纯入口，M为已去重计数，N已由调用者取明确数量或默认1：

```java
public BigDecimal estimateByCount(BigDecimal weight, int matchedCount, int requiredCount) {
    int target = Math.max(1, requiredCount);
    int matched = Math.min(Math.max(0, matchedCount), target);
    return weight.multiply(BigDecimal.valueOf(matched))
            .divide(BigDecimal.valueOf(target), 2, java.math.RoundingMode.HALF_UP);
}
```

- [x] 数量目标用spec规则：证书身份数→明确资源数量/简单满分数量→默认1。先对年份、金额、多个冲突数量写断言。旧extractProjectRequiredCount含“首个数字+单位”退化，不能不加区分地复用。
- [x] 使用已有库状态和实体ID/brandId/证书身份做可用性与去重；泛因素名用于选资料库，不作为具体品牌名等LIKE词。需要有无读取时复用现有repository和筛选构造器，增加预判专用事实查询而不改变严格match接口。
- [x] 证书不再要求CertificateScoreRule.parse对整条全文supported才出预计分。沿用事实取数/身份识别及有效性校验，按独立要求身份命中数计分。
- [x] GREEN：纯计算、数量、去重和有效性测试通过。严格实际评分测试仍按原契约通过。

## 切片二：预判主链路（T015）

- [x] 在EstimatedScoreServiceTest先写RED：复杂条款仍按M/N出数值；无面积明确要求时有可用仓即可出分；无资料0；异常不伪造0；历史code名称进入正确库。
- [x] 删除临时限制类及调用，把所有受支持客观知识库项接到estimateByCount；预计分不再调用固定六区15+5策略或严格业绩材料/排名短路。
- [x] 复用ScoreStatusPolicy：满分OK、0分DANGER、部分PENDING（有数值）；纯主观/无类别/财务仍单独待评，不影响同批其他项。
- [x] 依据使用现有estBasis，格式如“知识库粗估：要求3项，命中2项，满足66.67%，预计10.00/15.00；未核验正式评审附加条件”。未明确数量时写明N默认1。
- [x] 粗估输入指纹加入simple-estimate-v1版本；计算与读侧一致，不把旧严格计分缓存沿用成新粗估结论。
- [x] MySQL RED→GREEN：真实资料库→刷新→CAS保存→回读，核对数值与摘要，原规则/候选/stage2逐字段不变。

## 切片三：页面和交付（T016—T018）

- [x] Vitest先锁住0分、部分分与预计/实际模式隔离；页面仅调整说明和资料依据展示，不新建交互流程。
- [x] 页面固定说明“预计得分为知识库粗估，最终评审另算”；保留本次处理/数值项/无分值项，0计入数值项。不要把有分的PENDING一律显示成无分。
- [x] 执行ai-slop-cleaner写模式后，重跑下列命令；新source/test变更使旧验证与审查失效。

```bash
cd backend
mvn -q -Dtest=PartialScorePolicyTest,KnowledgeCategoryPolicyTest,EstimatedScoreServiceTest,ScoreEstimateFreshnessTest,ScoreEstimateRefreshMysqlTest,ScoreEstimateReadRegressionTest,CertMatchServiceTest,PersonMatchServiceTest,ProjectMatchServiceTest,WarehouseMatchServiceTest,BrandMatchServiceTest,ArchitectureTest,FPJavaArchitectureTest test
cd ..
npx vitest run src/views/Project/stages/components/ScoreParseDrawer.spec.js src/composables/projectDetail/useScoreEstimateRefresh.spec.js
npm run build
git diff --check
```

- [x] 两路独立规范/需求审查；最多三轮，之后按统一流程升级处理。记录本次新口径S5/S6证据，不能复用旧MERGE_READY。
- [ ] 本地交付停止在working_tree。只有后续授权部署后，才执行项目1371真实按钮→MySQL→API/UI验收；四个已识别客观知识库项应有数值，实际值按当时资料计算。

## 已知基线与范围纪律

此前扩展集3条旧日期/旧证书match桩测试在be462ee87固定基线也失败，保留原证据，不能冒充本次新增失败或靠放宽断言抹平。若新语义确实影响测试，必须逐条引用本规格说明预期变化。
本方案不修全仓解析、不新建知识库、不增加模型主通道、不做复杂排名预测。资料非空不等于正式评审合格；依据须明确这是数量/存在性粗估。

## 本地执行结果（2026-11-12）

- 182项后端目标测试通过，0失败/错误/跳过；含15项真实MySQL刷新测试、30项架构与8项纯核心架构检查。
- 57项前端测试通过，npm run build、line-budget、git diff --check通过。
- 第1轮独立审查发现证书明确身份相关性/别名去重和数量冲突；以实际编译类反例RED确认，修复后同一断言GREEN，第2轮两路LGTM。
- 共享strict match四类实现及computeStage2Score与be462ee87直接源码比较一致。新增粗估查询复用既有资料源。
- 同形1371四类真实MySQL用例：两项业绩10/10、同品牌去重后授权5/10、无仓0/10，处理4项/数值4项/无值0项。这是本地合成资料验收，不是测试服务器1371的实际分值。
- 证据：.omc/verify-1371-simple.md、review-1371-simple.md；原始执行日志/tmp/xiyu-simple-estimate-20261112/。T018远端验收未执行。
