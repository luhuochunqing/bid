# Utils Directory (工具目录)

> 一旦我所属的文件夹有所变化，请更新我。

## 功能作用

存放通用工具函数，提供跨组件的辅助功能。

## 常见工具类型

| 类型 | 说明 |
|------|------|
| 格式化 | 日期、数字、货币格式化 |
| 验证 | 表单验证、输入校验 |
| 转换 | 数据转换、类型转换 |
| 存储 | localStorage 封装 |
| HTTP | 请求封装 |

## 文件清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `featureFeedback.js` | 工具模块 | 统一处理特性未开放、反馈提示等 UI 反馈逻辑 |
| `firstNonBlank.js` | 工具模块 | 返回首个非空白值，供用户字段归一化和标签格式化复用 |
| `keyboardNavMode.js` | 工具模块 | 标记键盘导航输入模式，用于区分 Tab 焦点和鼠标点击焦点 |
| `notificationHelpers.js` | 工具模块 | 通知图标、类型、payload、@ 提及解析和源实体路由解析；TASK 通知通过 payload.projectId 回到项目任务上下文 |
| `scoreItemOptionDisplay.js` | 工具模块 | 评分项下拉短标题（编号+评分项）、原文 tooltip、按原文关键词过滤 |

## 添加新工具

```javascript
// 格式化工具
export function formatDate(date) {
  // ...
}

// 验证工具
export function validateEmail(email) {
  // ...
}
```
