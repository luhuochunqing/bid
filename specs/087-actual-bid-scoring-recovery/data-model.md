# Data model v2

## 不新增表和列
复用score_parse_task.run_summary（TEXT），SCORING写scoring命名空间；pipeline_version保持SCORING_VERSION_V1。不新增历史评分模型。

## ScoringRunReport
- schemaVersion=1，engineVersion=actual-score-v2，modelPolicyFingerprint为非密钥配置摘要。
- request：source、scope、itemIds；必要字段不得截断。
- input：当前附件sourceDocumentId、原件fileHash、ruleFingerprint、extraction摘要；不存签名URL、凭据或完整正文。
- extraction摘要：coverageStatus、sourceUnitCount/processedUnitCount/uniqueOcrUnitCount/unreadUnitCount/failedUnitCount/emptyUnitCount、有限位置样例和samplesTruncated。计数必须准确。
- items：scoreItemId、outcome=SCORED/NEEDS_REVIEW/SUBJECTIVE/FAILED、reasonCode、直接originTaskId、sourceFileHash/sourceRuleFingerprint/sourceEngineVersion/sourceModelPolicyFingerprint/sourceCoverageStatus。源身份完整，不递归查历史链。证据长文保留在既有score_result.evidence/quote；报告只放有限辅助引用，不重复大段正文。
- timing：createdAt、startedAt、elapsedMillis、extractionMillis、assessmentMillis；taskQuality=COMPLETE/NEEDS_REVIEW/PARTIAL/UNKNOWN。

## 字节边界
run_summary整体序列化UTF-8≤61,440字节。只裁剪可选位置样例（最多20个）与说明（每项最多256字符），标samplesTruncated；不裁剪请求、身份、准确计数、逐项outcome与来源。必要字段超限则不发布结果：用≤4KiB小型REPORT_TOO_LARGE失败报告（reportIncomplete=true，不携带超限正文）finish(replacement=null)，旧分不变。输入请求超限在创建任务前拒绝。报告写入和结果在既有短事务中完成，不能用过大报告再次触发兜底失败。

## ScoringExtractionReport与来源
覆盖状态COMPLETE/PARTIAL/UNKNOWN；processedUnitCount≤sourceUnitCount。Word sourceUnit计实际引用位置，重复媒体只OCR一次、各引用均计覆盖；PDF计物理页。COMPLETE不保证OCR语义正确，emptyUnits仍是警告。覆盖和单项充分性分开，PARTIAL不是全局禁止出分，也不是允许模型自行补全。

ScoringDocument为纯数据：text、sourceRefs、extraction，不持有Path/文件租约。临时文件租约由应用边界持有至评分finally；逐项补图使用同一文件，不引入跨请求缓存。

## 单项保留与跨原件限制
preserveEligible按单项成功及相同文件/规则/引擎判断，允许FAILED任务的已验证成功项；autoSkipEligible另要求所有目标项有效且来源COMPLETE。全项原子替换仍要求所有行归新task，复制项保留直接来源身份，不制造新的覆盖声明。

换文件或旧身份未知的ITEMS/UNSATISFIED以SCORING_REQUIRES_ALL拒绝，replacement=null，旧结果不动；不自动扩成ALL、不另存历史数值供新页面展示。拒绝报告保存已验证的当前附件身份；读侧将不同原件来源的旧行投影为待重评/来源过期、当前actualScore为空且不计有效总分，底层旧行不改。恢复审计仍用既有受控快照。

## 执行与质量
技术失败FAILED；正常执行但部分/全部客观依据不足COMPLETED+NEEDS_REVIEW，NO_VALID_OBJECTIVE_SCORE是质量原因。PARTIAL技术超时/损坏可使任务FAILED但保留可信已算项；业务拒绝不计技术熔断。全待确认不能算恢复成功。总截止为createdAt+1800秒，含排队；startedAt仅记录执行阶段，PENDING10分钟上限不额外赠送预算。旧任务迟到仍禁止回写。

objectiveScored + objectivePending + subjectivePending + failed = 当前项总数。技术失败优先，其余客观合法0/部分分算scored，其余客观无数字pending，其余主观subjectivePending。diagnosticsKnown=false意味着旧数据未知；旧okCount/dangerCount/pendingCount仍只表达满足程度。纯分类扩展SummaryAggregator，Assembler负责DTO映射。
