# Data Model: 历史案例原文直接下载

## 实体变更

### knowledge_case（变更：+1 列）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `obs_bid_file_key` | `VARCHAR(512)` | NULL | 原版投标文件在 OBS 的对象键，如 `2026年（投标文件）/2026.01.05-中广核办公/项目复盘-中广核.docx`；仅历史切片导入管道写入 |

迁移：`V1205__add_obs_bid_file_key_to_knowledge_case.sql`（列注释说明来源与格式）。

### 切片管道产物（JSONL 记录，变更：+1 字段）

| 字段 | 来源 | 说明 |
|---|---|---|
| `obs_bid_file_key` | `slice_all_v9.py` 已知的 `base + project_dir + docx 文件名` | 与 `project`/`docx_label` 同源，切片时即可确定 |

## 校验规则（纯核心 `ObsObjectKeyPolicy`）

- 非空、不含 `..`、不以 `/` 开头（防止绝对路径/路径穿越入库后被签发）
- 必须以历史资产根前缀 `2026年（投标文件）/` 开头（导入时校验；签发端点只信库内值）
- `filename = key 最后一个 '/' 之后的部分`（URL 解码后）

## 状态与分流

```
案例点击"标书原文"
├── obs_bid_file_key 非空 → 签发端点 → 预签名 URL → 直链下载
├── 否则 sourceProjectId 非空 → 档案预检（有附件？）→ ZIP 下载 / "未归档"提示
└── 否则 → "标书原文暂不可用"
```

## 关系

- `knowledge_case` 1:1 `obs_bid_file_key` → OBS 对象（外部资产，不在库内建模）
- 不新增表、不新增索引（300 条量级，按 id 点查）
