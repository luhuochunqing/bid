# 百度智能云表格识别（Table OCR）模块

## 职责定位
本包位于 `scoreparse/infrastructure/baidu`，负责对接百度智能云高精度表格识别（Table OCR）API，为系统提供 PDF 招投标文件的高保真表格提取能力。

## 解决的问题
传统开源或轻量 Sidecar 提取器在处理大型 PDF（特别是跨页续表、跨行跨列复杂大单元格）时容易出现表格截断、单元格漏失或坐标断裂（典型案例：项目 1392 续表中合规性与价格优惠多合一单元格丢失导致评分项原文缺失）。
本模块作为增强层，在系统开启百度 OCR 时，自动探测评分关键页并精准提取高保真表格结构，回填至 `DocumentArtifact`，解决下游评分事实提取无源证据可依的顽疾。

## 组件说明
- `BaiduTableOcrClient`: 百度 OCR HTTP 客户端，自动维护 access_token 缓存与鉴权；支持内存智能单页切片（PDFBox），极大降低大文件传输体积与堆内存占用。
- `BaiduTableArtifactAdapter`: 纯函数式适配器（FP-Java），负责将百度识别响应转换为系统通用的 `CanonicalTable` 与 `DocumentArtifact`，并智能推断合规的 `sourceSection`。
- `BaiduTableArtifactEnricher`: 文档增强器，智能扫描 PDF 评分关键页及续表候选页，调用百度 OCR 并与 baseline DocumentArtifact 执行平滑融合。
- `BaiduOcrProperties` / `BaiduOcrCredentialsProvider`: 配置属性与数据库动态凭证提供者契约。
