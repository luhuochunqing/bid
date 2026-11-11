# Research
G1: 生产任务d4c579d0-c267-4818-91c7-fe29afb0a72d，2026-11-10 17:45:49，extractOnDisk:140空正文。Sidecar 185837065 bytes/10050ms/chars=0/low_text_density。
G2: convert_pdf_document只读文本层；已有artifact OCR受30页限制，不能复用于全卷。现有Docker已含chi_sim+eng、PyMuPDF。采用评分专用OCR opt-in；安全失败代码跨进程保留，禁止部分全文打分。
