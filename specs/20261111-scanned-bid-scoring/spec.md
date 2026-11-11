# Feature Specification: 扫描投标文件评分恢复

**Branch**: agent/codex/score580-scanned-bid
**Created**: 2026-11-11
**Status**: Approved for implementation by 用户“开始修复” (前序诊断修复边界)

## User Scenarios & Testing
### US1 完整扫描件评分 (P1)
项目580投标文件转换200但0字符；用户应能取得扫描页正文后进入既有评分。
1. 无文本层扫描PDF通过真实OCR输出各物理页文字，不因表格OCR30页上限截断。
2. 混合PDF保留文本页并OCR扫描页，文本页不重复OCR。
3. OCR缺失、失败或超时必须准确失败，不得返回部分成功或建议无依据重传。
4. 文本PDF及其他调用者保持既有提取路径；临时文件用后释放。
### US2 区分预估状态 (P2)
主观项显示待专家评审；无知识库匹配能力的客观项显示需人工评估；一般处理中仍待确认；已有0分必须显示0。

## Requirements
- FR001 评分大文件路径启用完整扫描页OCR，物理页码可追溯。
- FR002 OCR请求完整或明确失败；不静默截断页数、字符或异常。
- FR003 将OCR失败分类传至任务errorMessage及missedReason。
- FR004 预估不编造分值，显示原因且不影响实际分数展示。
- FR005 不部署、不改生产记录，不引入外部OCR费用或新依赖。

## Success Criteria
真实生成的多页扫描PDF在既有转换入口产出可识别正文；相同回归在旧实现失败、新实现通过；失败与混合/原生文字正常对照通过。

## Scope and Assumptions
复用已安装中文Tesseract及PyMuPDF。1225页全量性能验收单独报告，不能将小样本通过升级为项目580线上恢复。
