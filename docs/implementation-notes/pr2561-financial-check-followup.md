# PR !2561 follow-up：财务核查不得短路资格性评审

> 审查：Gitee !2561 合入后审查。本笔记只记规格里没有写死、实现时必须拍板的决定。

## 规格缺口与拍板

1. **第一阶段只 FAIL，不 PASS。**  
   `QUALIFICATION_REVIEW` 在 spec 047 是「企业信誉、类似项目业绩门槛」。财务三表齐全不能结束第 14 项。对齐 `vetoFirstStage`：硬信号只拦截造假，放行交给 LLM。

2. **占位符协议不用中文子串。**  
   结论前缀 `[UNFILLED_PLACEHOLDER]`，由 `FinancialStatementCheckPolicy.isHardAntiFakeFail` 判断。仅当「有占位且没有任何有效报表/审计页」才上浮 FAIL。

3. **占位页 + 真报表 → MANUAL（政策层），第一阶段不拦截。**  
   常见装订是目录范本页后面跟真报表。一票否决会假 FAIL。

4. **报表识别改为标题 ∧ 表内要素。**  
   审计意见页点名三表不得算已附报表。

5. **年份：绝对年份必须全部命中；抽不到年份不能当覆盖成功；「近三年」不能 `contains("近")` 短路。**  
   绝对 token 用 `find(20xx)` 规范化「年/年度」。仅相对年份时规则层永不 PASS，交给 LLM（2015 或 2024 都不能凭「近三年」三个字直通）。页面抽取要求带「年」，避免把金额 2018万 当成年度。

6. **免除冲突偏「必须提供」。**  
   全文既有「免于提供财务状况」又有「须提供审计报告/三表」时，不走 `notRequired`。`fuse` / `fuseWithDeterministic` 与保证金一样，冲突时 `required=true`。

7. **「健全的财务会计制度」不再单独启动提取。**  
   政府采购法第 22 条套话几乎每份招标都有，会把第一阶段误挂到所有项目。

8. **未点名三表时不默认塞满三表。**  
   只要求「经审计的财务报告」时，`requiredStatements` 留空。

9. **执照门槛不在本 PR 改。**  
   `agent/codex/pr2557-followup` 正在收紧 `BusinessLicenseCheckPolicy`（`count >= 2` + 同页证照关键词）。本分支碰它会双写冲突。

10. **`sanitizeQualifications` 恢复 `cleanItem` + `LinkedHashSet`。**  
    这是 !2561 为 300 行瘦身误伤的资格主路径。
