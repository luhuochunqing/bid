# Research: 实际评分恢复 v2

## Decision
按R01–R10修订为：完整覆盖目标、逐项证据判定、有界资源、真重评与可信保留。复用已有任务/Tesseract/SAX/请求配置/结果表；取消无条件全卷抽图，延期跨原件历史展示。

## Evidence and revision
- 41页真实OCR反例证明静默抽样丢证据，正常原件仍须完整覆盖及人工金标验收。PARTIAL按项并不允许模型自行宣告充分。
- FAILED结果可显示，但旧valid集合排除FAILED成功项，局部重试会丢未选值；v2分开preserveEligible与autoSkipEligible。
- 全待确认不是技术失败；原共享AutoFailCircuit合并PARSE/SCORING失败，因此正常待确认用COMPLETED+NEEDS_REVIEW，明确业务拒绝排除技术熔断。
- 原load会在返回后释放临时文件，又在返回前无条件整卷抽图；v2一次下载、先hash、按需图像、评分finally释放，不建缓存。
- SDK默认重试2次，另有降级；v2复用request config并设评分maxRetries=0，单项实际传输总计至多2次，全部受剩余deadline约束。
- run_summary是TEXT，500个合成Word位置JSON为84,783字节；v2摘要60KiB、必要字段不得裁剪，超限用4KiB失败报告且replacement=null。finish仍需复制runSummary并真实读回。
- Word已有SAX，转换已有隔离入口、图片已有头信息检查；分别扩展它们，不重建正文解析/调度/客户端。2000像素缩图不能代替解码前限制。
- 非SCORING_VERSION_V1会进入历史兼容，版本字段保持，仅scoring命名空间记录引擎。
- 原件变化局部请求明确拒绝ALL提示，直接来源身份足够首轮，不建设JSON历史评分系统；旧分仅保留存储，已确认不同原件的来源在读侧过期、不计当前总分。总截止统一createdAt+1800秒，排队不额外增加时间。

## Alternatives rejected
直接上线/增大超时不能解决漏证据与空分复用；新队列/向量库/持久OCR缓存无必要证据。合规适配器的限页/剥媒体不满足评分。Future.cancel不终止运行中任务，硬终止需求保留。摘要及证据规则进入纯核心，不能为少文件塞回应用编排。

## Measured and unmeasured
既有53项OCR测试及41页反例属于前序证据，本轮只修订文档。586MB原件字节/图数/耗时/RSS/模型准确率待Task1测量，完整恢复由Task8验收。v1工期估算作废，基线测完再排期。MemU连接失败，依据本会话现有证据与源码。


## 2026-11-16 PDF并行与empty页审查
Decision：PDF仍单线程调用PyMuPDF，只将渲染后PNG交给有界Tesseract线程任务；不在线程共享Document/Page对象。Rationale：官方multiprocessing文档明确不支持PyMuPDF多线程；既有Word已用1..4有界窗口。Alternatives：全流程多线程有崩溃风险，多进程PDF会扩大进程内存及去重复杂度，当前无必要。Source：https://pymupdf.readthedocs.io/en/latest/recipes-multiprocessing.html 。
证据：生产8核、Sidecar4GiB无CPU硬配额，仅是资源快照，不是并行速度证明；真实回放后决定默认并发。empty4不等于真实白页；既有最高档信用证明只支持四档Word表格，不能套用项目580的三档PDF承诺函。
