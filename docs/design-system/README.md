# 设计规范 (design-system/)

存放项目的设计系统规范文档。

## 目录职责

本目录存放项目的设计系统规范，包括颜色、字体、间距、组件样式等 UI 设计规范。这些规范是前端开发和 UI 设计的事实依据。

## 文件清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `MASTER.md` | 设计规范 | 设计系统主文件，包含全局规则和组件规范 |

## 设计规范内容

### 颜色系统

| 角色 | 色值 | CSS 变量 |
|------|------|----------|
| Primary | `#0F172A` | `--color-primary` |
| Secondary | `#334155` | `--color-secondary` |
| CTA/Accent | `#0369A1` | `--color-cta` |
| Background | `#F8FAFC` | `--color-background` |
| Text | `#020617` | `--color-text` |

### 字体

- **Heading Font:** Plus Jakarta Sans
- **Body Font:** Plus Jakarta Sans

### 间距规范

| Token | 值 | 用途 |
|-------|-----|------|
| `--space-xs` | `4px` | 紧凑间距 |
| `--space-sm` | `8px` | 图标间距 |
| `--space-md` | `16px` | 标准内边距 |
| `--space-lg` | `24px` | 区域间距 |
| `--space-xl` | `32px` | 大间距 |
| `--space-2xl` | `48px` | 区域外边距 |
| `--space-3xl` | `64px` | Hero 区域 |

### 阴影层级

| 层级 | 值 | 用途 |
|------|-----|------|
| `--shadow-sm` | `0 1px 2px rgba(0,0,0,0.05)` | 微妙提升 |
| `--shadow-md` | `0 4px 6px rgba(0,0,0,0.1)` | 卡片、按钮 |
| `--shadow-lg` | `0 10px 15px rgba(0,0,0,0.1)` | 模态框、下拉 |
| `--shadow-xl` | `0 20px 25px rgba(0,0,0,0.15)` | Hero 图、精选卡片 |

## 使用规则

1. 构建具体页面时，先检查 `design-system/pages/[page-name].md`
2. 若存在页面专属规则，**覆盖** Master 文件中的通用规则
3. 若不存在，按 Master 文件的规则严格遵守

## 禁止事项

- ❌ 使用 Emoji 作为图标（使用 SVG）
- ❌ 缺少 `cursor: pointer`
- ❌ 布局抖动的悬停效果
- ❌ 低对比度文本（最低 4.5:1）
- ❌ 即时状态变化（使用 150-300ms 过渡）
- ❌ 不可见的焦点状态
- ❌ 默认深色模式

## 更新规则

- 设计规范变更需更新 `MASTER.md`
- 新增页面设计规范放在 `pages/` 子目录
- 重大规范变更需通知前端团队
