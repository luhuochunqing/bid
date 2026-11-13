# 087评分恢复合成夹具来源

这些夹具均为本次回归生成，不含客户原件、OCR正文或生产凭据。

- `native-scoring-text.doc`：两句合成英文（Native certificate ABC999 valid until 2099-12-31 / Second paragraph...）经macOS textutil转DOC，测试真实HWPF原生文本分派。
- `native-scoring-image.doc`：合成RTF内嵌1x1透明PNG，经textutil转DOC，测试旧DOC含图不能伪报语义完整。
- `scoring-sidecar-v2-contract.json`：python-docx生成含NATIVE CONTRACT VALUE 72000000的合成文件，调用真实Python `_convert_worker(..., scoring_report=True)`所得响应；用于防止Java手写最小响应漏掉真实扩展字段。

生成发生于Git外临时目录；输入/转换工具来源由实现日志记录。生产1123原件仅存Git外私有目录，不能替换这些资源。
