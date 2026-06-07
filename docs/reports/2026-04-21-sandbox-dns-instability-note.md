# 2026-04-21 Sandbox DNS Instability Note

## 背景

在确认 `feat/non-integration-gap-close` 是否已经与 GitHub 远端同步时，出现了“同一时段内，部分命令可访问 GitHub，部分命令解析失败”的现象。

## 现象

沙箱内多次执行以下命令时，出现 DNS 解析失败：

- `git fetch origin feat/non-integration-gap-close`
- `git ls-remote --heads origin feat/non-integration-gap-close`
- `curl -I https://github.com`

典型报错：

```text
fatal: unable to access 'https://github.com/ericforai/bidding.git/': Could not resolve host: github.com
curl: (6) Could not resolve host: github.com
```

同时，这个故障具有波动性：

- 某些时刻，沙箱内 `curl -I https://github.com` 可以返回 `HTTP/2 200`
- 某些时刻，同样命令又会重新报 `Could not resolve host`

## 已验证结论

1. GitHub 本身不可达并不是根因
   - 在沙箱外执行 `curl -I https://github.com` 返回 `HTTP/2 200`
   - 在沙箱外执行 `git ls-remote --heads origin feat/non-integration-gap-close` 成功

2. 当前问题与沙箱内网络解析有关
   - 沙箱内 `git` 与 `curl` 都曾复现同类 DNS 失败
   - 沙箱外同类命令可正常访问 GitHub

3. 分支同步状态已经确认
   - 远端 `refs/heads/feat/non-integration-gap-close` 为 `36224c9fd2e754fef761c67c8156de5add386c24`
   - 本地 `HEAD` 也为 `36224c9fd2e754fef761c67c8156de5add386c24`

## 影响范围

- 会影响依赖 GitHub 在线校验的命令：
  - `git fetch`
  - `git ls-remote`
  - `git push`
  - 任何直接访问 `github.com` 的网络请求
- 可能导致“代码已推送，但沙箱内无法再次联网确认”的误判

## 建议

1. 当沙箱内再次出现 GitHub 解析失败时，优先区分“沙箱网络问题”与“远端真实失败”
2. 需要最终确认远端状态时，可使用沙箱外联网命令做对照验证
3. 在发布、PR 合并、分支同步这类需要强确认的动作后，记录一次成功的远端 commit SHA，减少重复排查成本

## 本次确认结果

本次最终结论是：

- `feat/non-integration-gap-close` 已成功推送到远端
- 本地与远端分支头一致
- DNS 异常来自当前执行沙箱的网络解析抖动，而不是 GitHub 仓库状态异常
