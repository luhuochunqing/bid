# Spec Kit一致性检查
FR001对应converter完整页序列、内存/磁盘extractForScoring及真实扫描/混合PDF测试。
FR002对应完整返回与deadline/OCR失败测试；旧默认转换调用仍保留。
FR003对应白名单异常映射和task/missedReason一致性JUnit。
FR004对应ScoreParseTable状态/0分/actual回归。
FR005无DB/配置库/外部OCR调用变更，无部署或生产重算。
本地受影响测试和构建通过；独立门禁首次因沙箱Mockito限制停止，用户授权换真实进程适配器重跑。全卷性能需分开记录。

## 第1轮修复：混合页局部扫描证据遗漏
单图覆盖50%才触发OCR会漏掉原生标题下的财务截图及分块扫描正文。新增600×800页、540×243扫描区域的真实Tesseract回归（单图/分块两种），修改前两项均只返回标题而失败。评分专用路径改为含图片即走既有整页OCR，移除面积启发式；无图片原生页仍不调用OCR，默认转换和失败分类不变。原14项保护先通过；本轮只运行无需监听端口的Sidecar测试，Java监听测试交主执行器verify阶段。含装饰图片的原生页也会消耗OCR预算，全卷性能仍待验收。

## fix阶段第1/3轮：内联图片漏检
完整基线diff及未跟踪原任务文件复核后，新增合法PDF内联图片与原生标题共页回归。真实PyMuPDF确认该页get_images为空、get_image_info检出1张图片；修复前测试失败，正文只有标题。检测改用get_image_info，复用现有整页OCR，无新增依赖。原10项保护先通过；修复后test_scanned_bid_ocr.py全部11项通过（无跳过），包含真实Tesseract内联财务正文、标题保留及物理页码断言。命令：`uv run --with pymupdf --with 'pdfplumber>=0.11,<0.12' --with pillow --with pytest --with pytest-asyncio python -m pytest tests/test_scanned_bid_ocr.py -q`（Sidecar目录）。默认Python缺PyMuPDF、备用Conda缺pdfplumber，最终使用独立uv缓存环境；存在PyMuPDF及pytest-asyncio弃用警告。未运行监听端口测试、未启动服务；跨模块验证交主执行器verify阶段，客户全卷验收边界不变。

## fix阶段第1/3轮：稀疏扫描文字被空白阈值吞掉
复核基线8175e09fb190c8d995676895d8eab9ec6a730f71至工作区完整diff及未跟踪文件。原11项扫描PDF保护通过后，新增真实A4大小PDF：第一页原生正文，第二页仅12pt合同编号的栅格扫描。`test_mixed_pdf_keeps_sparse_scanned_contract_number`修改前失败，第二页正文为空。将黑色像素低于0.1%的跳过条件替换为RGB所有像素均为纯白才确认空白；任何非白像素继续OCR，识别为空仍明确失败。修改后上述同一uv命令运行全部12项通过、无跳过，保留纯白扫描页、内联图片、局部扫描、31页及失败路径保护；编号及物理第二页标记均断言保留。`git diff --check`通过。已有PyMuPDF/pytest-asyncio弃用警告不影响结果。仅修改转换器、本回归与本记录，未启动服务、提交或生成审查凭证；监听端口及跨模块测试交主执行器verify阶段。非纯白的噪声空页会进入OCR，无法识别时明确失败，不能用面积阈值冒充空白证据；177MB原件和全卷性能仍未验收。
