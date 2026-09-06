# PR !2557 Follow-up 实施笔记（agent/codex/pr2557-followup）

> 基线：origin/main（含 !2557 已合入）；范围：Code Review 报告的 3 Major + 4 Minor。
> 验证：`mvn -Dtest='com.xiyu.bid.compliance.**.*Test'` 376/376 PASS + ArchitectureTest 32/32 PASS。

## 1. 修复清单与决策

### Major-1：`check(bidDoc, requirement)` 消费契约（`BusinessLicenseCheckPolicy.java`）
- `requirement == null` → 回落 `standard()`（保持旧行为，旧代码等价于忽略参数）。
- `required == false` → 直接 PASS（结论"招标文件未要求…"），先例：保证金免收直通 PASS。
- `allowCertificates` 非空时过滤：主分支要求本页出现清单内证照（`matchAllowedCertificate`），命中者同时作为结论 `docType` 标签（此前非事业单位一律标"营业执照"，现执业许可证等可正确署名）；事业单位特例分支要求清单为空或包含"事业单位法人证书"。
- `requireOriginalOrCopy` 未做硬拦截：确定性文本无法验证"原件"，公章要素已由 `hasAuthorityOrSeal` 覆盖，`description` 已进 LLM 准则 6 上下文（`LlmAdjudicator.summarizeRequirements`）。有意不做，避免误伤无"公章"字样的真实扫描页。
- 联动修改 `BidDocumentQualityEngine` 步骤 6：`licenseNotRequired` 时跳过 LLM 裁决，否则步骤 1 的直接 PASS 会被 LLM 覆盖（保证金免收走步骤 3、无此问题，执照是唯一需要守步骤 6 的项）。

### Major-2：PASS 要求同页证照关键词
- 新增 `hasLicenseKeyword`（营业执照/法人证书/登记证书/执业许可证），进主分支 AND 条件。业绩合同页（仅对方 USCC + 法定代表人 + 地址）不再直通。
- 事业单位特例分支天然含关键词（字面量"事业单位法人证书"），未加条件。

### Major-3：占位符正则泛化（`UNFILLED_PLACEHOLDER_PATTERN`）
- 【】分支：`请粘贴营业执照` → `(?:请|此处)?粘贴营业执照`，`营业执照复印件` → `营业执照(?:副本)?(?:复印件|扫描件)`。旧用例全部仍命中（已逐条验证），新增命中【此处粘贴营业执照】/【此处粘贴营业执照副本复印件】。
- `91…X{6,}` 分支允许中间空白（`91[0-9A-Z]{6}\s*X{6,}`）：6 连 X 在真实代码中不可能出现（X 仅作校验位），fail-closed 安全。

### Minor-5：目录过拦截
- `MULTI_DOT_PATTERN` 散点分支仅为弱信号：含 USCC 的页直接判"非目录"。目录点线行强判据（`CATALOG_DOT_LINE_PATTERN`，要求行尾页码）与标题判据保持不变。目录页本身不会出现 18 位代码，fail-closed 不变。

### Minor-6：登记要素弱信号收紧
- 去掉裸 `长期`（改 营业期限/有效期限/有效期至/长期有效/至长期）与裸 `地址`（改 住所/营业场所/注册地址）。"投标有效期""联系地址""长期合作"不再计数。
- 现有 PASS 用例均含 ≥2 强要素或登记机关，不受影响（已验证）。

### Minor-4/Minor-7：lessons §231
- 修正"GB 正则防身份证误判"表述：纯数字 18 位串仍可命中正则（已实测 `110105199003078888` 命中），真正防线是三重 AND。另清掉文件尾部 7 个多余空行（`git diff --check` 干净）。

### 未修：SemanticPageRetriever 格式+功能混排
- 已合入无法拆分，记为纪律项（功能本身无误）。

## 2. 附带发现（本任务内已修）
- **origin/main 全量 compliance 套件本就有 2 红**：`DeterministicCheckPolicyTest.testBusinessLicense_WithUSCC` 与 `evidenceQuote_…` 在干净基线（stash 验证）即 FAIL——!2557 收紧 PASS 标准（要求登记要素）后未同步更新这两处旧断言（旧页仅 USCC、无要素）。按 !2557 意图（反假收紧）修测试页（补法定代表人/登记机关），不放松代码。教训：PR 合入前必须跑全量包，不止定向单测。

## 3. Tradeoffs
- `required=false` 的 PASS 无 evidence：步骤 6 强直通要求非空 evidence，本应"无证据 PASS 被 LLM 复核"，但此处复核语义错误（招标都不要求），故用 `licenseNotRequired` 显式守卫而非补假 evidence。
- 占位符/目录判据继续 fail-closed（可疑→MANUAL/FAIL），误工不误放；误报成本留给后续按真实标书样本调参。
- 未碰 `DeterministicBusinessLicenseExtractor` 与 Parser：审查无 finding。

## 4. 测试证据
- 新增：`BusinessLicenseCheckPolicyTest` +6（免收直通/清单排除/合同页/裸占位符/省略号页/弱信号），`BidDocumentQualityEngineTest` +1（免收不调 LLM）。
- 存量修复：`DeterministicCheckPolicyTest` 2 页补要素。
- `com.xiyu.bid.compliance.**.*Test`：376/376 PASS；`ArchitectureTest`：32/32 PASS。
