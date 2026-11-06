# 百度智能云表格识别（Table OCR）模块

## 职责定位
本包位于 `scoreparse/infrastructure/baidu`，负责对接百度智能云高精度表格识别（Table OCR）API，为系统提供 PDF 招投标文件的高保真表格提取能力。

## 解决的问题
传统开源或轻量 Sidecar 提取器在处理大型 PDF（特别是跨页续表、跨行跨列复杂大单元格）时容易出现表格截断、单元格漏失或坐标断裂（典型案例：项目 1392 续表中合规性与价格优惠多合一单元格丢失导致评分项原文缺失）。
本模块作为增强层，在系统开启百度 OCR 时，自动探测评分关键页并精准提取高保真表格结构，回填至 `DocumentArtifact`，解决下游评分事实提取无源证据可依的顽疾。

## 组件说明
- `BaiduTableOcrClient`: 百度 OCR HTTP 客户端，通过 APIHub `/user/login` 获取并缓存 token，使用 `satoken` 请求头调用 `/search`；支持内存智能单页切片（PDFBox），极大降低大文件传输体积与堆内存占用。
- `BaiduTableArtifactAdapter`: 纯函数式适配器（FP-Java），负责将百度识别响应转换为系统通用的 `CanonicalTable` 与 `DocumentArtifact`，并智能推断合规的 `sourceSection`。
- `BaiduTableArtifactEnricher`: 文档增强器，智能扫描 PDF 评分关键页及续表候选页，调用百度 OCR 并与 baseline DocumentArtifact 执行平滑融合。
- `BaiduScanPageSelector`: 评分扫描区间选择器，基于连续候选页聚类与基线评分表锚点重合识别，实现跨页双向连通。
- `BaiduOcrProperties` / `BaiduOcrCredentialsProvider`: 配置属性与数据库动态凭证提供者契约。
- `BaiduRepeatedHeaderPolicy`: 重复表头续表必须由原始网格确认两页属于同一表，且编号连续、存在续行、前表未封口。
- `BaiduOcrReplacementGuard`: OCR 将两项合为一行时，只有原网格在同页逐项对应编号、分值和名称，且不存在同页额外 OCR 表或越界表，才保留该网格。

## APIHub 中转契约（2026-11-05）

- `/search` 请求固定 `code=BAIDU_OCR_DOC_API`、`noCache=1`，`params` 为原 `pdf_file` / `pdf_file_num` 参数的 JSON 字符串。
- 保留目标页切片：成功切片后 `pdf_file_num="1"`。不改变百度响应到表格的纯函数适配。
- 检查外层 `success/code` 和 `data.isSuccess`；`data.result` 支持 JSON 字符串或对象。HTTP/登录/中转失败抛 IOException，交由现有增强器记录失败。
- 前端 OCR 卡片可配置用户名/密码并加密保存；未配置时回退 `APP_SCORE_PARSE_BAIDU_OCR_USERNAME` / `PASSWORD`。不在仓库保存账号密码。不再使用百度 API Key/Secret Key 登录。
- 登录字段默认 `username`；部署环境如使用 `userName`，配置 `APP_SCORE_PARSE_BAIDU_OCR_LOGIN_USERNAME_FIELD=userName`。
- token 按 `tokenRemainTime` 缓存并提前刷新。动态设置 SPI 提供开关及已保存的中转凭据；修改账号后 token 缓存失效，旧 Key 字段不控制中转凭据。
- 中转仅允许测试环境使用，本地契约测试不代表真实 OCR 识别或业务验收。

- 部署边界：`APP_SCORE_PARSE_BAIDU_OCR_GATEWAY_ENABLED` 默认 false；仅测试部署显式设为 true。已有数据库 OCR 启用状态不能绕过该开关，生产原直连路径不再自动使用，不会静默回落直连。
- 真实 OCR 诊断另需 `XIYU_OCR_GATEWAY_LIVE_TEST=true` 及外部中转账号；普通本地测试不访问真实 OCR。真实诊断保留原断言，不将跳过记作验收通过。

- 能力入口 `isEnabled()` 同时要求部署中转开关和动态配置开关开启（无动态提供者时使用属性开关）。关闭部署时不扫描评分页、不追加 OCR 失败警告，Sidecar 不可用继续传播原异常；登录连接测试仍可独立验证账号。

- 结果完整性：明确非零整数 `error_code` 保留业务失败；成功结果要求非负整数 `table_num`，非零时必须有等长 `tables_result` 数组且元素为对象。零表格允许省略数组，但显式 null 或不一致数组均抛 IOException。结果解析和登录请求/响应处理独立于同步缓存协调。

## 2026-11-06 实测协议兼容修复

- 实测登录：`code="0"`、`msg="success"`，`token/tokenRemainTime` 位于顶层；search 同样 `code="0"`，业务结果仍在 `data.isSuccess/result`。
- 客户端同时支持实际格式和文档 `success=true/code="000000"/data.token` 格式。明确 `success=false`、非成功码、缺失/非字符串 token、失败业务状态及畸形表格继续拒绝。
- 真实失败根因：旧 `readEnvelope` 把上游成功 code=0 判为 IOException，设置服务再包装为通用“登录失败”。HTTP 200 和通用提示均不能独立判断账号或网络。
- 仅修改响应适配，不改用户名、密码、启用开关、PDF 参数或评分规则；不把单张测试表识别成功当作业务文档验收。
