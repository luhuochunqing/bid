# Sidecar 内存泄漏根因分析：mammoth base64 图片内联（docx→HTML 转换路径）

> 日期: 2026-08-21
> 排查者: trae
> 修复 PR: `!2373`（分支 `agent/trae/sidecar-memory-leak-root-cause`）/ 前置止血条目 [lessons-learned.md §143](./lessons-learned.md)

---

## 现场还原

**症状素描**：第 139 次测试环境部署（172.16.38.78）后 20 分钟，sidecar 容器内存膨胀至 4.7GB，挤占同机后端 JVM native 内存致其崩溃（详见 §143）。当时以 `docker update --memory` 容器限制止血，本篇是后续的根治任务闭环。

**边界划定**：
- 泄漏只发生在 **docx → HTML/markdown 转换路径**（`POST /convert`、`POST /convert-stream`）❌
- 图片提取路径（`POST /convert-images`）内存平稳 ✅
- txt/pdf 等非 docx 格式无泄漏 ✅
- 容器限制只是把"宿主被拖死"变成"容器被内核 OOM 杀掉重启"，转换大 docx 时 sidecar 仍会反复死亡循环 ❌

---

## 本地复现（取证数据）

构造含 400 张 JPEG 的 docx（单图 ~150KB），走 `/convert` 转换：

| 指标 | 抑制前 | 抑制后 |
|------|--------|--------|
| HTML 输出体积 | **65.7MB** | ~0.5MB |
| peak RSS | **724MB** | **~49MB** |
| 内存放大倍数 | 输入文件的 ~12 倍 | 与输入同量级 |

65.7MB 的 HTML 意味着什么：400 张图片全部以 `data:image/jpeg;base64,...` 形式内联进了输出字符串。base64 编码本身有 4/3 膨胀，加上 Python 字符串拼接过程中的中间副本，峰值 RSS 达到 724MB——这就是生产环境 20 分钟膨胀至 4.7GB 的元凶（多个大 docx 连续转换 + 无释放窗口）。

---

## 零号病人定位

### 第一行错误

```python
# markitdown 0.1.6 内部（_docx_converter.py:80-82）
# mammoth.convert_to_html 未传 convert_image 参数
# → mammoth 使用默认 converter：mammoth.images.data_uri
# → 每张图片读出字节流 → base64 编码 → 内联进 <img src="data:...">
```

### 必然性解释

```
用户上传含大量图片的 .docx
  ↓
markitdown md.convert(path) → mammoth.convert_to_html(fileobj, style_map=...)
  ↓
mammoth 默认 convert_image = images.data_uri（base64 内联）
  ↓
N 张图片 × (原始字节 + base64 字符串 + HTML 拼接中间副本)
  ↓
单次转换峰值 RSS = 文件体积的 10 倍以上；连续转换无释放 → RSS 单调爬升
  ↓
cgroup 无限制时挤占宿主（§143 事故）；有限制时容器被 OOM kill 反复重启
```

### markitdown 分层断裂（为什么不能靠配置关掉）

markitdown 0.1.6 提供了 `keep_data_uris=False` 选项，看似能解决——但它存在**分层断裂**：

- 该选项只在 `_markdownify.py:107` 的 markdownify 层生效（转换已有 data URI 的 HTML 时剥离）
- `_docx_converter.py:80-82` 调用 mammoth 时**不透传该选项**，只传 style_map
- 即：docx 路径下，图片在 mammoth 层就已经被 base64 内联成巨型 HTML 字符串，markdownify 层再剥离为时已晚——内存峰值已经发生

结论：无法通过 markitdown 公开配置按调用方意图分流（文本转换要抑制、图片提取要保留），必须在 mammoth 层动手。

---

## 修复设计：进程隔离天然分流

核心矛盾：同一个 sidecar 进程里，两条路径对 mammoth 的语义要求相反——

| 路径 | 对 mammoth 的要求 |
|------|------------------|
| `/convert`、`/convert-stream`（文本转换） | **抑制**图片内联（占位符即可） |
| `/convert-images`（图片提取） | **保留** data URI（父进程用 image_extractor 提取原图） |

解法：**利用进程池隔离 patch 的作用域**。

```
父进程（不 patch，mammoth 干净）
├── /convert-images → image_extractor 直接提取，data URI 可用 ✅
└── ProcessPoolExecutor(max_workers=1, max_tasks_per_child=16)
    └── worker 子进程（_convert_worker 首行执行 _suppress_docx_image_inlining()）
        └── /convert、/convert-stream → md.convert() 在 worker 内执行，patch 生效 ✅
```

### 手术六刀（PR !2373 diff 摘要）

1. **`_suppress_docx_image_inlining()` 幂等版**：`_MAMMOTH_PATCHED` 全局 guard + `kwargs.setdefault("convert_image", placeholder)`（占位 img，src 为空串）；docstring 明确 MUST NOT be applied in the parent process
2. **`_convert_worker()` 首行插入 suppression 调用**：patch 只在 worker 进程内生效
3. **进程池设施**：`ProcessPoolExecutor(max_workers=1, max_tasks_per_child=16)`——单 worker 吸收 cgroup OOM 保住 PID 1；`max_tasks_per_child=16` 定期回收累积的内存碎片；env `SIDECAR_MAX_CONVERSIONS_PER_WORKER` 可调
4. **async seam**：`async def _convert_document(path)` 作为所有测试的 patch 目标（AsyncMock 无法跨进程边界）
5. **BrokenProcessPool 自愈**：worker 被 OOM kill 时捕获异常 → 池置 None（下次请求懒重建）→ 返回 503「文档转换服务繁忙，请稍后重试」而非 500/挂死
6. **HTTPException 吞没陷阱修复**：`/convert-stream` 内层 try 补 `except HTTPException: raise`，防止自愈 503 被兜底 `except Exception → 500` 吞掉（`/convert` 存量外层已有该先行分支）

### 幂等 guard 在三种启动模式下的正确性

| 模式 | guard 行为 | 结果 |
|------|-----------|------|
| fork（Linux 默认） | 子进程继承 `_MAMMOTH_PATCHED=False`（父进程从未 patch） | worker 正常执行 patch ✅ |
| spawn（macOS/Windows） | 子进程重新 import app module，全局标志重置为 False | worker 正常执行 patch ✅ |
| max_tasks_per_child 重启 | 新 worker 进程全新初始化 | 重新 patch ✅ |

---

## 验证与修复

### 测试改造

- 原 suppression 测试直调 `md.convert`（绕过 worker，patch 不生效）→ 改为直调 `_convert_worker(path)` 并在 finally 中还原父进程 mammoth + 重置 `_MAMMOTH_PATCHED`（因为测试进程本身就是"父进程"，`/convert-images` 依赖干净状态）
- 新增 `test_broken_worker_returns_503_and_self_heals`：MagicMock submit 抛 BrokenProcessPool → 断言 503 + detail 文案 + `_CONVERT_POOL is None`
- 既有 4 处 `md.convert` patch 迁移到 `_convert_document` AsyncMock seam（test_app.py ×4、test_convert_stream.py ×3）
- 回归结果：**29 passed, 0 failed**

### 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| 零号病人已定位 | mammoth 默认 `images.data_uri`（markitdown `_docx_converter.py:80-82` 未传 convert_image） | ✅ |
| 必然性已证明 | 400 JPEGs → 65.7MB HTML / 724MB peak RSS 本地复现 | ✅ |
| 配置层不可修已证明 | markitdown `keep_data_uris` 不透传至 mammoth 层（分层断裂） | ✅ |
| 修复 diff 已提供 | PR `!2373` 六刀 | ✅ |
| 防复发测试已设计 | suppression 直调 worker 测试 + BrokenProcessPool 503 自愈测试 + 29 passed | ✅ |
| 双路径兼容已验证 | `/convert-images` 父进程 mammoth 保持干净，data URI 提取不受影响 | ✅ |

**Verdict**: ✅ **PASS**

---

## 为什么之前没有提前发现

1. **开发期测试样本太小**：日常联调用的 docx 只有几张截图，base64 内联的内存放大不显著
2. **RSS 泄漏是量变引发质变**：单次转换的峰值内存在小文件下完全正常，只有"大文件 × 连续转换 × 无释放"叠加才爆发
3. **§143 止血掩盖了症状**：容器加限后宿主不再崩溃，但 sidecar 开始 OOM 死亡循环——如果只看"服务自动恢复了"就会漏过真实根因
4. **markitdown 表面提供了开关**：`keep_data_uris=False` 的存在让人误以为配置可解，实际分层断裂使其对 docx 路径无效

---

## 防复发规范

1. **Python 侧 patch 模块级共享符号前，必须枚举所有调用方对该符号原语义的依赖**（本次教训的具体化见 lessons-learned.md §144）
2. **重资源操作优先考虑进程隔离**：能放进 `max_tasks_per_child` 有限的 worker 池的操作，就不要留在常驻父进程——worker 死了可以重建，父进程死了服务就没了
3. **外部库的"表面开关"必须验证透传链路**：文档里有选项 ≠ 你用的代码路径会消费它，grep 库源码确认参数流向
4. **兜底 `except Exception` 前必须先放行框架异常**：`except HTTPException: raise` 先行，否则自愈语义（503）会被吞成通用错误（500）
5. **内存类 bug 必须本地构造放大样本复现取证**：凭"可能是 X"猜测修复不如一次 400 图 docx 的 724MB peak RSS 数据有说服力

---

## 相关文档与代码

- `document-converter-sidecar/app.py` — suppression / 进程池 / 自愈 503 / async seam
- `document-converter-sidecar/tests/test_memory_guard.py` — 4 个守卫测试
- [lessons-learned.md §143](./lessons-learned.md) — 容器限制止血条目（本篇的前置事故记录）
- [lessons-learned.md §144](./lessons-learned.md) — 猴补丁副作用陷阱（本次方法论沉淀）
- [decisions.md §6](./decisions.md) — 进程隔离防御设计拍板记录
