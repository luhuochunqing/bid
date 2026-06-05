# Styles Directory (样式目录)

> 一旦我所属的文件夹有所变化，请更新我。

## 功能作用

存放全局样式文件和 CSS 变量，定义设计系统。

## 文件清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `variables.css` | Style | CSS 变量定义 |
| `common.css` | Style | 通用工具样式与基础无障碍规则 |
| `form-controls.css` | Style | 全局输入框/选择框尺寸与静态边框基线 |
| `micro-interactions.css` | Style | 非表单控件的微交互与状态样式 |
| `accessibility.css` | Style | 跳转、可读性与辅助访问样式 |

## CSS 变量分类

### 颜色系统
- `--color-primary`: 主色
- `--color-success`: 成功色
- `--color-warning`: 警告色
- `--color-danger`: 危险色
- `--color-info`: 信息色

### 间距系统
- `--spacing-xs`: 4px
- `--spacing-sm`: 8px
- `--spacing-md`: 16px
- `--spacing-lg`: 24px
- `--spacing-xl`: 32px

### 圆角和阴影
- `--border-radius-sm`: 2px
- `--border-radius-md`: 4px
- `--border-radius-lg`: 8px
- `--shadow-sm`: 小阴影
- `--shadow-md`: 中阴影
- `--shadow-lg`: 大阴影

## 表单控件基线

- 默认输入框、选择框、日期框高度统一为 `40px`。
- 搜索/筛选类内联表单控件桌面宽度统一为 `168px`，移动端占满容器。
- 输入、选择、文本域在 hover / focus / active 时不增加蓝色描边、光圈或阴影；校验错误态仍保留红色边框。
- 主操作按钮、搜索按钮和分页当前页使用 `--brand-xiyu-logo` 品牌绿，主按钮文字固定为白色；并排按钮中的删除、警告、状态区分色保留各自语义。
