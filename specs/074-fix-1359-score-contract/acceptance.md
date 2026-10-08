# 项目1359恢复验收

2026-10-08，用户授权范围：直接修改项目1359评分标准；不部署、不打开版本生效开关；AI实际打分暂不可用，按钮未强制禁用。

## 结果

正式数据库及Chrome页面实测 **39项、100分**：商务20、技术50、价格30。旧38项ID1719–1756原样保留，补PRICE-1折扣率评价。两项负扣分原文保留，不用除2修正。
逐项数据库/页面来源、名称、权重交叉一致；旧38项14个受保护字段逐一与备份一致。task207原始200和历史运行摘要保留，追加manualRecovery及39条人工审计；最新task295仍待复核、未改时间。其他项目471项/4130分及15审计保持；本项目active_version/result均0。服务PID29513和版本开关false保持，未触发实际评分/重新解析。

## 根因及最小修复

历史把技术、商务两张原始100分表直接相加，遗漏按50%/20%换算及价格30%；最新模型仅产出1条总括事实，待复核不覆盖正式结果保护保留了旧200。另有独立源码缺陷：合计读取最低列12/38，随后与最高列100冲突。
本地仅OriginalScoringContext的6行新增/1行替换与纯核心ScoreSubtotalColumnPolicy修正列语义；未知/空列继续阻断，来源、父子去重、方案资格与发布保护代码不改。分部完整性及比例由真实原文恢复契约独立校验。本地代码在agent/codex/074-fix-1359-score-contract；用户后续授权提交Gitee PR，仍不授权合并或部署。

## 验证

| 验证 | 实测 |
|---|---|
| 相关Java八套件 |117 tests /0 failure /0 error /0 skip，exit0|
| 真实1359独立来源契约 |11测试覆盖最小/最大列、换序、未知/空列、冲突、横幅等，另已注册Golden来源契约|
| 原Golden抽取全集 |27案、TP122/FP0/FN0/F1=100%；1359来源契约不冒充抽取PRF|
| 数据恢复材料 |10 Python tests通过；MySQL8七场景通过|
| 前端构建 |npm run build exit0，前后构建门禁通过|
| 全量后端 |975 tests /2 failures /42 errors /0 skip，FAIL|
| 精确基线精选对照 |75 tests /4 failures /0 errors /0 skip；原2断言failure均基线存在；42缺失class error在干净基线未复现，不能声称当前全库已通过|
| 全Wiki检查 |165条已有日期/健康违例，未批量假刷新；本次新页面无独立违例|

全量失败详情及精确基线日志单列，未给整体源码MERGE_READY。独立Spec和Reviewer B都给仅评分标准恢复DATA_READY。

## 执行/回滚材料

- 原文：../../.omc/evidence/project-1359-document-1138.docx；SHA c30713962fa2c76f2e36cef36849b361f7c2e15c602c32e872ec33273bdb3c23。
- 备份：../../.omc/evidence/project1359-before.sql；SHA486d1cb268c5bdc741da1f48f47987d0c7c11ab4f40588df0e292de833d41233。
- 实际已执行：../../.omc/evidence/recovery/restore-executed.sql；SHA115ee0377f24fb84d346848efeb960908941dece3ca3670ef1a11ca82249bfff。
- 最终回滚：../../.omc/evidence/recovery/rollback.sql；SHA9ee2495b56d9b4b911d7d9635060e86999cd6d78286097b9c62ba53de44cd628。发生后续漂移会拒绝，绝不盲目覆盖；目前未在线上执行回滚。
- 本地最新版restore.sql增加完整BINARY漂移守卫，仅作为归档，不对已恢复项目重复执行。
- live-apply.log、live-after-ui.log、ui-1359-rules.json、ui-1359-price-total100-crop.jpg；源码/数据日志均在../../.omc/evidence/。

## Spec Kit只读一致性复核结论

通过SPECIFY_FEATURE=074-fix-1359-score-contract正常运行官方check-prerequisites --require-tasks --include-tasks；命名适配仓库agent/前缀，不修改脚本。FR001–006及SC001–004均有任务覆盖（10/10），任务14项、需求覆盖100%，无剩余规格冲突/宪法冲突；无整体绿色声明。T011记录实际失败而非假勾通过，T012数据范围审查与源码交付分开。

## 使用范围

只恢复评分标准，不代表线上解析程序已修复。AI实际打分暂不使用（按钮仍可达）。没有部署、服务重启或版本开关变更。

## PR授权补充

源码PR仅包含小计列策略、真实来源契约/Golden回归、SpecKit及知识说明；scripts/scoreparse/的两个一次性运维工具和.omc/evidence原文快照/执行SQL均留在本地，路径仅指本次操作工作台，不是PR承诺提供的应用功能。已同步到501b5e160的main，以下PR门禁记录将单独追加，不把旧验证冒充同步后的新鲜结果。

## 提交PR前的新鲜验证（2026-10-08，基于main501b5e160）

- 修复后相关Java及架构164 tests/0fail/0error/0skip（含1359契约14项、Golden37项），源最高分括号/比例注释与Context共享原有解析语法；可信最高分数/评分分值列头兼容且未知列仍拒绝。两个原文语法回归及明确列头兼容均先实测RED再GREEN。
- 新版根因回放门禁九套件实测通过，包含完整规则、跨格式、真实线上事实全管线及原文身份保护；与旧全量失败记录单列，不继承旧绿报告。
- 候选复跑此前失败类75tests/4fail/0error/0skip，四项失败与精确干净基线一致；42个class缺失错误未在候选精选复跑出现，仍不宣称全库通过。
- 前端单元3321pass/3skip、384测试文件pass/1skip，真实exit0；lint0errors/232已有warnings，构建和文档/行预算门禁通过。
- 只补本机门禁CLI flock与隔离Python docx/lxml环境，不修改测试队列/门禁代码，不启动服务。Gitee提交授权不包含部署/合并/再次数据库恢复。
