# Config Directory (配置目录)

> 一旦我所属的文件夹有所变化，请更新我。

## 功能作用

存放应用配置文件，包括 AI 功能的 Prompt 配置。

## 文件清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `ai-prompts.js` | Config | AI 功能配置 |

## AI 配置结构

```javascript
export const aiConfigs = {
  complianceCheck: {
    id: 'complianceCheck',
    name: '合规检查',
    promptTemplate: {...},
    formConfig: {...}
  },
  // ...
}
```

## 添加新 AI 功能

在 `aiConfigs` 中添加新配置：

```javascript
newFeature: {
  id: 'newFeature',
  name: '功能名称',
  icon: '图标',
  category: '类别',
  promptTemplate: {
    role: '角色',
    task: '任务描述',
    outputFormat: '输出格式'
  },
  formConfig: [...]
}
```
