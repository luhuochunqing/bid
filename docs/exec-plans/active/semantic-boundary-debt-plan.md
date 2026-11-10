# Semantic boundary 技术债拆分计划

状态：已获用户批准并实施，进入最终验证与审查。日期：2026-11-10。

目标：保留业务放行与明确缺陷契约，将人工语言变形降为观察语料，随后以两个独立步骤降低正则和语义状态复杂度。

环境：`/Users/user/xiyu/worktrees/xiyu-claude-semantic-boundary-debt`；分支 `agent/claude/semantic-boundary-debt`；基线 `e49dcc4e37a01a17244b6d5627b1a2e548d984ea`。Gitee `origin` 为主仓库，GitHub 镜像不操作。非开发宿主，不启动服务。

## 事实与边界

- `git fetch origin` 后，main 最新为 !2994（e49dcc4e3），!2993 合入点为 4c1fe8871；这是远端 Git 合并历史证据，未额外查询 PR API。
- claude-init 工作区干净，落后16提交的旧任务分支 `agent/claude/root-cause-quality-check-unblock` 保留原状；新任务从 origin/main 建独立临时 worktree，不复用旧分支。
- 无 `.codegraph/`，本次不调用 CodeGraph；MemU 连接失败，已使用当前仓库源码与历史核对。
- 不改 A/B/C/D 既有放行决策、不补新句式、不扩接口、不部署或重算。先交付清单，用户已要求实施；采用 Markdown 便于逐方法核对。
- 用户提及的 A/B/C/D 字母映射未在已查规格中找到同名定义，实施前须从 !2993 原始需求/验收记录补上字母与四个决策入口的映射，禁止自行编造映射。当前明确保护身份证双面/OCR、授权完整要素与签署、资质库匹配/一票否决及其各自反例。

## 逐项用例清单

见 [完整清单](semantic-boundary-debt-inventory.md)，覆盖全部151个方法，列出基线行号、静态调用槽、目标类、保留参数与迁移理由。

| 目标测试类 | 原方法数 | 默认门禁 | 责任 |
|---|---:|---|---|
| Quality1360BusinessContractTest | 67 | 是 | 业务明确条件、原位证据、放行与明确反例 |
| QualityHtmlEvidenceContractTest | 12 | 是 | HTML清洗、单次解码、实体及比较符保护 |
| QualityVerdictParsingContractTest | 10 | 是 | JSON顶层字段、截断、非法结构保护 |
| SemanticHeuristicCorpusTest | 62＋上述参数余项 | 否 | 人工语言变形、跨句跨页上下文观察 |

共89个门禁方法候选。参数化门禁保留清单指定代表项；方法内循环仍保留，因此89不是实际执行断言数。80～100是契约规模目标，不能为凑数删除实证保护。真实样本尚待原件来源核验，不把当前字符串统称真实标书。

## 阶段1：固定契约并迁移测试（生产源码零改动）

- [ ] 编码前按 PLANS.md 完成 speckit-specify → speckit-plan → speckit-tasks；复用本清单作为输入，明确A/B/C/D映射与现有规格冲突。
- [ ] 在当前基线执行原测试并保存 Surefire XML：`mvn -f backend/pom.xml -Dtest=Quality1360UnblockTest test`。记录运行JDK、SHA、实际测试数、失败/错误/跳过，不把静态计数当运行证据。
- [ ] 按清单拆成上述四类；同包、不新增公共业务API，不引入模拟业务数据源。测试内构造的DocumentContent属于纯核心输入，不新增线上Mock路径。
- [ ] 每个原方法/参数输入建立一一对应记录，断言与数据迁移前后相同；保留循环及顺序。先全量跑新四类（含语料）证明迁移没有丢输入，再执行排除语料的门禁。
- [ ] 语料类加 `@Tag("heuristic-corpus")`；backend/pom.xml 的 Surefire 默认配置增加 `excludedGroups` 对应属性，默认值 `heuristic-corpus`，提供显式覆盖。仅加标签不会自动退出门禁。
- [ ] 显式语料运行：`mvn -f backend/pom.xml -Dtest=SemanticHeuristicCorpusTest -Dgroups=heuristic-corpus -DexcludedGroups= test`。确认实际有运行记录，不能把0例当成功。
- [ ] 删除被完整替代的旧测试壳；不能同时保留旧类导致语料仍进入默认门禁。

验收：每个旧输入有去向，四类合计覆盖原输入，所有旧断言先原样通过；默认test实际排除语料，而业务/HTML/解析三类均运行。

## 阶段2：纯结构重构（行为严格不变）

- [ ] 修改 `SemanticEvidenceBoundary.java`、`AuthorizationFieldPolicy.java`，先按词义命名片段常量，再拼接Pattern；源码每行≤120字符。不得改变锚点、分组编号、量词、匹配标志和备选优先级。
- [ ] SemanticEvidenceBoundary 只保留裁决入口与结果转换；如展开后超过仓库行预算，将已有证书关联扫描整体移至同包包可见 `CertificateEvidenceContextPolicy.java`，不改变调用顺序和状态机。不得为了300行压行或另建通用解析框架。
- [ ] 两个类与迁出的类一起检查最长行、总行、方法复杂度；Pattern数量是观察指标，不作为机械删规则目标。
- [ ] 同时跑门禁与显式语料，保持原期望与证据字段不变。比较 status、conclusion、suggestion、evidences 与 details；有差异立即回到结构修改本身，不修语料来掩盖差异。

## 阶段3：撤回跨句指代能力（单独行为差异）

- [ ] 该阶段与阶段2分别提交。只根据本句明确对象/证书身份核对事实；裸“已过期”“未到场”以及跨页省略主语不再推断归属，输出人工复核，保留原页码和摘录。
- [ ] 明确同句缺陷和真实直接FAIL继续保留；授权人/被授权人/范围/期限缺项与身份证双面证据规则不变。不把未知默认为PASS，不把所有跨页直接证据一概丢掉。
- [ ] 从语料迁移项中固定最小能力边界：明确同句对象、裸续句、跨页裸续句、明确其他对象切换各1例。新增的是范围契约，不增加自然语言同义变形枚举。
- [ ] 列出旧→新结果差异，只允许已批准的未知关联转人工；若出现既有A/B/C/D主路径变化，停止该阶段并回报。语料旧断言失败作为差异记录，不驱动正则补丁。
- [ ] 完成 speckit-analyze，确保规格、计划、任务、执行证据一致；更新相关docs及Wiki，不宣称单测等同生产UAT。

## 后续审查：复用 !2994，不重写门禁

已核对 `scripts/review-workflow.py`：每次最多3轮；第2/3轮传入历史review报告与fix摘要；第三轮失败停止；同分支连续2次耗尽三轮触发熔断。`scripts/lib/review_agent.py` 已注入“启发式变形最高Minor/Info”和前序历史约束，无需新增启动参数启用。

实施后在任务worktree执行（本轮未执行）：

```bash
python3 scripts/review-workflow.py run \
  --verify '["git","diff","--check"]' \
  --verify '["mvn","-f","backend/pom.xml","-DexcludedGroups=heuristic-corpus","test"]' \
  --verify '["npm","run","build"]'
```

- 遵循ai-slop-cleaner写入 → 真验证 → 独立google-code-review → 独立xiyu-code-review既有顺序。
- 审查验收围绕主路径、明确反例、证据定位与结构约束；不以新造变形句扩展能力。但真实崩溃、越权、架构违规仍按门禁要求处理，不能用“只看主路径”忽略。
- 将本计划与清单交给审查上下文；Minor/Info必须写接受/延期理由。收到ESCALATE、第三轮失败、越界或进程故障停止，不重开流程洗历史。
- 不删除 `git rev-parse --git-path review-gate` 定位的状态/历史，不自传 `--allow-rerun`；只有触发熔断后用户再次明确裁定才可使用。
- 当前清单交付没有业务变更，不启动会写代码的run，不提交、不push、不建PR。后续代码完成后先上述审查，再按仓库正常提交与PR门禁推进。

## 本轮验证记录

已完成远端fetch、合并提交核对、分支隔离、151方法清单对账、当前门禁历史/熔断源码核对。未运行Maven/JUnit，未验证生产报告、部署或UAT。清单中的归类是可审阅实施决策，不冒称业务样本来源已全部证实。

## 实施补充

当前实施契约与任务位于`specs/083-semantic-boundary-debt/`。四维度原始依据已在decisions.tsv定位；静态685计数以实际773更正。原始全量、迁移、结构阶段已逐方法对账。跨句混合断言级归类修正与基线遗留失败见清单末节及analysis.md。后续审查验证采用compliance域＋三架构测试，并明确隔离已在原policy复现的FullDocumentSemanticRetrievalAcceptanceTest夹具；不以全后端测试无关失败驱动越界修复。
