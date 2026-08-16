# Research: 评分解析与打分的花费守卫

## 1. 自动门闩放在哪

**Decision**: 抽出 `AutoParseGate`（无任务且无评分项才允许自动新建）。`TenderDocumentStoredListener` 与抽屉自动路径共用；`POST /parse` 手动「重新解析」不走此门闩。后端事件路径必须执行门闩，不能只靠前端。

**Rationale**: 043 只收了抽屉。保存事件仍会 `triggerParseFromEvent`，只拦进行中，已成功/失败仍会新建。换招标文件已澄清不自动。

**Alternatives considered**:
- 只改监听器 if 判断：会和抽屉条件再分叉。
- 所有 POST /parse 都上门闩：手点「重新解析」会失效。

## 2. 熔断怎么记

**Decision**: 任务表增加 `trigger_source`（`AUTO` / `MANUAL`）。熔断看该项目 30 分钟内 `AUTO`+`FAILED` 的次数；≥2 则拒绝新的 AUTO 解析/打分。一次 `MANUAL` 且成功则立即解除。已在跑的任务不取消。

**Rationale**: 不新建计费表。失败次数从已有任务行能算出来。

**Alternatives considered**:
- Redis 计数：多一个依赖，重启丢计数。
- 所有失败都熔断：手点试错会被误伤。

## 3. 「文件没变」比什么

**Decision**: 对投标文件**字节**做 SHA-256，记在最近一次 **COMPLETED** 打分任务上。同时记评分项清单指纹（各项 id+weight+detail 的稳定哈希）。两者都与上次成功打分相同才跳过。手点也跳过。本版无强制重打。

**Rationale**: 比文件名/URL 稳；清单变了（又解析过标准）即使投标字节相同也必须重评（spec 边角）。

**Alternatives considered**:
- 只比抽出的正文：抽取器一变哈希就变，误重打。
- 失败任务也当可沿用：违反「失败不产生可沿用结果」。

## 4. 脏章节怎么切、怎么对应项

**Decision**: 按标题行切开投标正文（Markdown `#` / 中文章节标题），每章对正文做 SHA-256。与上次成功打分记下的章节指纹对比得到脏章。评分项算相关若：上次 `quote`/`evidence` 含该章标题，或项的维度/名称与章标题有字面重叠。不确定则重评该项。切不出章或对不上 → 全量并说明。

**Rationale**: 不引入新的文档结构服务；宁可多打一项不可漏打。

**Alternatives considered**:
- 句子级 diff：规格已排除。
- 只按项的 location 字段：现网 location 经常空。

## 5. 手动范围

**Decision**: 打分触发增加可选 `scope=ALL|UNSATISFIED|ITEMS` + `itemIds`。不传则 ALL（旧前端兼容）。范围只在「需要评估」时生效；文件未变仍整表跳过。

**Rationale**: 澄清已定哈希优先于手点。

**Alternatives considered**:
- 选了范围就忽略哈希：与「手点也跳过」矛盾。

## 6. 不做的

**Decision**: 无项目额度、无 Token 账本、无超额确认。花费控制 = 门闩 + 熔断 + 跳过 + 增量 + 范围。
