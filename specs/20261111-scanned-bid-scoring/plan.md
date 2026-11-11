# Implementation Plan: 扫描投标文件评分恢复
## Technical Context
Python FastAPI/ProcessPoolExecutor/PyMuPDF/Tesseract；Java21 HttpClient；Vue3/Vitest。无新增依赖、DB变更。
## Constitution Check
FP-Java：Java仅HTTP错误转换/任务编排，Python承担文件与OCR IO，前端纯展示分支。真实OCR测试，不将合成样本代替客户全卷验收。
## Design
评分extract(file,name)发X-Pdf-Ocr:true，其他显式文本上限调用不启用。Sidecar仅opt-in PDF进入完整逐页OCR转换，原生文本直接使用；每页渲染后即释放。全部完成才返回正文；全卷OCR有明确时间上限，失败转稳定code而非部分正文。Java仅按稳定code映射安全原因，评分任务保留该原因；未知故障保持既有安全文案。
## Alternatives
不采用百度表格OCR：仅表格且扫描页数约束不适用于全卷。不直接忽略空正文或抽样前30页：会误评分。复用本机OCR且不改变默认转换路径。
## Verification
新增扫描/混合/超过30页/失败与deadline测试；真实本机生成PDF完整OCR；HTTP opt-in和错误契约；Java HttpServer测试；Vue主观/不支持/0分/实际分数回归；相邻测试与npm run build。
## Delivery
仅任务worktree实现与验证。独立审查后留可评审差异；不push、不建PR、不合并、不部署。全量1225页性能如未执行必须明确记录。
