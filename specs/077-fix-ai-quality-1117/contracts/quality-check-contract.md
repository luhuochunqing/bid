# Quality Check Behavioral Contract

| 输入事实 | 必须输出 | 禁止输出 |
|---|---|---|
| `2026 年 09 月 21 日` | 日期存在 | 日期缺失 |
| 纸质模板词 + 明确电子投标/数字证书 | 电子/需 CA | 首次命中纸质词即短路 |
| 保证金声明存在、图片不可读 | MANUAL_REVIEW | 确定缺失或确定已附 |
| ISO9001/14001/45001 组合 | 三个原子结果 | 单证书代表全部满足 |
| 仓库数量门槛 | OBJECTIVE | SUBJECTIVE |
| 服务/应急方案评审 | SUBJECTIVE | OBJECTIVE |
| 候选未激活 | 继续读取 active | 默默切换 candidate |
