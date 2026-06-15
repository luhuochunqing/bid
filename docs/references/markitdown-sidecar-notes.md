# markitdown (sidecar) — 内部化参考

> **盲区原因**：`document-converter-sidecar/` 用的 `markitdown` **不在 PyPI**，从工作 venv 拷贝安装；AI 默认会建议 `pip install markitdown`，会失败或装到错误包。

## 依赖声明

来源：`document-converter-sidecar/requirements.txt`。

- 安装方式：**不是** `pip install markitdown`（PyPI 上同名包未必是同一项目）
- 实际安装路径：从工作 venv 拷贝（见 requirements.txt 内注释）
- 配套栈：FastAPI 0.109.2 + uvicorn 0.27.1、`magika`（文件类型识别）、`pypandoc`、`pdfplumber`、`python-multipart`、`beautifulsoup4`、`openpyxl`、`pandas`、`pydub`、`speechrecognition`

## 部署形态

- 模式：**sidecar**（与后端独立部署，HTTP 通信）
- 端口约定（主目录基准）：`127.0.0.1:8000`
- Worktree 映射：Codex worktree 用 `8002`，由 `scripts/dev-services.sh` 自动分配
- Docker：`document-converter-sidecar/Dockerfile`（python:3.11-slim，非 root，装 poppler + tesseract）
- 编排：`docs/deployment/docker-compose.yml` 的 `sidecar` 服务

## AI 写代码须知

- ❌ 不要在文档/脚本里写 `pip install markitdown` 作为安装指引。
- ✅ Python 工具链是 `pip` + `requirements.txt`（本项目**不用** uv/poetry）。
- ✅ 后端调用 sidecar 走 HTTP（见 sidecar 路由），不是直接 import。
- ✅ 改 sidecar 依赖后，同步更新 `requirements.txt` 与 `requirements-dev.txt`。

## 待补充

> 把 markitdown 的实际 API（转换入口、支持的文件类型列表）从 venv 源码摘录到本文件。
