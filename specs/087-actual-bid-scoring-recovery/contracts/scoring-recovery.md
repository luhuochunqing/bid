# Scoring recovery contracts v2（拟实现）

## Sidecar
既有POST /convert增加评分opt-in：X-Pdf-Ocr:true、X-Scoring-Report-Version:1；原普通JSON及/convert-stream文本契约不变。X-Scoring-Deadline-Epoch-Millis为Java按任务createdAt+1800秒产生的整任务UTC截止；服务端钳制并转monotonic，上传/排队不重置预算。截止覆盖排队与全部尝试，不能只控制OCR；startedAt只统计执行段，PENDING最多10分钟且不额外增加总预算。

转换通过既有_convert_document统一入口管理容量、错误和回收；保留隔离及请求硬终止，不建第二队列。容量初始1，忙时尽早返回503；不声称FastAPI multipart解析之后的检查能阻止已经上传的字节。超时/断连只回收本请求，不误杀其他请求。部署校验时钟偏差≤5秒并预留安全余量。

返回markdown/pages及extraction。Java流式JSON读取，正文≤500万字符，响应初始≤64MiB；HTTP上限与数据库上限分别校验。

```json
{
  "markdown": "[第 41 页] CERTIFICATE ABC999",
  "pages": [{"pageNumber": 41, "text": "CERTIFICATE ABC999"}],
  "extraction": {
    "schemaVersion": 1,
    "engineVersion": "scoring-ocr-v2",
    "coverageStatus": "COMPLETE",
    "enumerationComplete": true,
    "sourceUnitCount": 41,
    "processedUnitCount": 41,
    "uniqueOcrUnitCount": 41,
    "unreadUnitCount": 0,
    "failedUnitCount": 0,
    "emptyUnitCount": 0,
    "unreadUnits": [],
    "failedUnits": [],
    "emptyUnits": [],
    "elapsedMillis": 12000
  }
}
```
示例仅展开一页正文；正式响应必须与全部输入范围一致。PDF来源为物理页，Word为partName/段落或表格anchor/relationshipId/mediaName。COMPLETE表示应处理单元均已执行且无未读/失败，不等于语义100%识别。非白图OCR为空记录emptyUnits；enumerationComplete必须为true才可报告COMPLETE；schemaVersion须为1、engineVersion非空。缺失/无法验证报告为UNKNOWN。

PARTIAL按项判定：完整规则+可核验引用足以唯一确定结果，且未读部分无法改变结论，才允许该项数值；计数/缺失/否定条件未完整核验则NEEDS_REVIEW。模型自报充分不能放行；现有校验不能证明则待确认，本轮不新增自然语言规则引擎。UNKNOWN不能形成可信新分，配置/协议异常终结且replacement=null。

## 文件生命周期与补图
一次LoadedBidFile完成下载、hash、复用/拒绝判定、正文提取及必要补图；不能先contentHashOnly再load重复下载。OnDisk释放延后到整次评分最外层finally；包括SKIPPED、拒绝、异常、超时。普通调用者的load契约保留，通过评分入口使用资源作用域，不把临时文件路径放进domain值对象。

移除评分路径无条件extractImages。文本充分直接评分；需要视觉核验时按来源补图：PDF复用renderPages，Word使用现有ZIP关系定位只读所需媒体并复用图片处理。所有补图有字节/像素/图数/剩余时间预算，不重下载原件、不默认整卷再上传、不回退固定前3张图。无可信关联时该项待确认。

## Java接口与模型调用
评分专用extractScoringDocument(Path|byte[], fileName, deadlineEpochMillis)返回ScoringDocument(text, sourceRefs, extraction)；不含数据库实体、临时文件路径或IO行为。普通extract不变。HTTP/文件/事务在应用或基础设施边界。

复用OpenAiBidAgentRequestConfig派生timeout=min(配置,剩余预算)、maxRetries=0，仅评分用途生效。每项共用最多2次实际传输额度，外层可恢复重试、json_schema降级、图片降级均扣同一额度；现有transport显式接收评分预算，普通调用保留既有行为。不新建客户端，不把每层“重试一次”叠加。

## Trigger与复用
POST /api/projects/{projectId}/score-parse/scoring保持source/scope/itemIds。请求创建时存入run_summary.scoring.request；非法/跨项目itemIds拒绝。

- MANUAL=真重评，无force新参数，绕过三层SKIPPED，范围不扩张。
- preserveEligible：同文件/规则/引擎，单项outcome成功且引用/数值合法；所属任务FAILED不一概否决该成功项。主观合法分析可保留。
- autoSkipEligible：所有目标项有效、文件/规则/引擎一致、来源覆盖COMPLETE；不因FAILED里存在成功项就跳过整任务。
- 原件hash变化或旧身份未知的ITEMS/UNSATISFIED，返回SCORING_REQUIRES_ALL，提示用户ALL；既有结果不变。若hash异步才确定，终结现有任务并replacement=null；此业务拒绝排除技术熔断。不自动触发付费ALL。拒绝报告记录已核验的当前附件sourceDocumentId/fileHash，既有读侧投影据此将旧来源结果标待重评/来源过期，actualScore不作为当前有效值返回、总分不计入；仅保留底层旧行，不新增历史页面。

## 持久化
pipeline_version保持SCORING_VERSION_V1。完整run_summary序列化UTF-8≤61,440字节（60KiB），其中scoring为独立命名空间。必须保留request、input身份（含sourceDocumentId/fileHash）、engineVersion、准确覆盖计数、每项outcome和直接originTaskId/来源身份（含sourceModelPolicyFingerprint）。可选位置样例最多20个，说明每项最多256字符，超出标samplesTruncated；完整清单留受控工件。

必要字段超限：创建前拒绝过大请求；完成阶段不发布replacement，改成≤4KiB的REPORT_TOO_LARGE失败报告后finish(replacement=null)，不得继续复制超限detached.runSummary。失败报告仅任务/原因/基本身份，明确reportIncomplete，不伪装完整逐项诊断。旧结果保持。

同原件局部评分继续全项原子替换、每行scoring_task_id=新task，未选成功项复制保留。报告携带直接originTaskId与核验身份，不递归加载来源链，不存跨原件历史分数正文；首轮不新增历史展示。

## Results与终态
GET /api/projects/{projectId}/score-parse/results追加qualityStatus、coverage及objectiveScored/objectivePending/subjectivePending/failed/diagnosticsKnown；既有字段保留。qualityStatus=COMPLETE/NEEDS_REVIEW/PARTIAL/UNKNOWN，不等同任务终态；来源身份明确。

- 技术故障FAILED；已验证成功项保留，失败项不可复用。
- 正常执行但客观依据不足COMPLETED+NEEDS_REVIEW；NO_VALID_OBJECTIVE_SCORE只是质量原因，不作为技术失败。无绿色出分成功，不通过业务恢复验收。
- 主观全部待评仍按正常产品语义处理。
- 四类计数互斥守恒；合法0/部分分计objectiveScored。历史诊断缺失diagnosticsKnown=false，不把默认0解释为无失败。
- 共享AutoFailCircuit不新增状态机；正常待确认不会进入FAILED统计，SCORING_REQUIRES_ALL等明确业务拒绝由ScoreParseAutoPolicy排除，PARSE技术失败及历史未知错误保持原规则。
- 轮询绑定既有taskId；重开先GET，不POST；终态再读results。新任务替换时明确切换观察。
