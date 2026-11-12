---
title: 测试环境 OBS 直传与投标文件删除残留
space: engineering
category: pitfalls
tags: [obs, upload, bid-file, cors, clean-up]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/SidecarStreamTextExtractor.java
  - src/views/Project/stages/DraftingStage.vue
  - docs/release/deploy-report-2026-11-13-269th-test.md
backlinks:
  - .wiki/pages/_index.md
created: 2026-11-13
updated: 2026-11-13
health_checked: 2026-11-13
archive: false
---

# 测试环境 OBS 直传与投标文件删除残留

## 结论

- 测试环境上传投标文件必须用域名 `https://winbid-test.ehsy.com`。用 `http://172.16.38.78:8080` 时 OBS 直传的 `HEAD ?apiversion` 请求失败（status 0），`bid_file` 永远停在 `UPLOADING`。
- UI 上「删除」投标文件只解除项目关联，`bid_file` 行与 OBS 对象都还在，需要单独清理。
- sidecar OCR 可以绕过整套评分链路直接验证：`POST /convert-stream` 带 `X-Pdf-Ocr: true`。

## 现象与证据

- 上传后 `bid_file.status=UPLOADING`、`completed_at=NULL`；后端日志只有一次 `/api/files/upload-token` 200，没有任何确认回调。
- 在页面注入 fetch/XHR 钩子捕获到：`https://ehsy-widbid.obs.cn-east-3.myhuaweicloud.com:443/?apiversion` 的 `HEAD` 请求 `status 0`（网络/CORS 层直接失败）。
- 同一操作换到域名后立刻 `COMPLETED`，钩子无报错。CORS_ALLOWED_ORIGINS 里同时列了 IP 与域名，但 OBS 桶的 CORS 是独立配置，后端放行不代表 OBS 放行。
- 删除投标文件后页面行消失，但 `SELECT * FROM bid_file WHERE id=?` 仍能查到，OBS 对象也仍可下载。

## 做法

- 测试环境统一域名访问，不要图方便用 IP。
- 清理残留顺序：UI 删引用 → SQL 删 `score_result` → `score_parse_task` → `bid_file` → 最后删 OBS 对象。
- OBS 删除必须用虚拟主机风格（`bucket.endpoint` + 路径为对象 key）。路径式（`endpoint/bucket/key`）返回 403 `VirtualHostDomainRequired`。
- 服务端用 Python 读 `backend.env` 时必须 `set -a; source ...; set +a`，否则拿到的是 shell 变量，`os.environ` 取不到。
- AK/SK 只在服务器上就地使用，不打印、不落到本地文件。

## 复用的验证手法

打分链路的正文来自 `SidecarStreamTextExtractor`：`POST /convert-stream`，带 `X-Pdf-Ocr: true` 与 `X-Sidecar-Key`。

- 无文本层的扫描 PDF：不带 OCR 头返回 0 字节（复现旧 bug），带头返回正文。
- 透明底图片的 DOCX：带头才识别得出图里的字，不带头只有「【已附图片】」。
- 这比跑完整打分（分钟级、受 AI 时延影响）快得多，适合回归验证。

## 边界

- 以上均为测试环境实测；生产 OBS 桶的 CORS 白名单是独立配置，历史记录见 `docs/lessons/lessons-archive.md` 2026-07-20 的下载 CORS 事故。
- `remote-deploy.sh` 只负责发布，不清理历史 `bid_file` 与 OBS 对象，存量垃圾需要单独治理。
