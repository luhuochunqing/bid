# wangEditor — 内部化参考

> **盲区原因**：`@wangeditor/editor` + `@wangeditor/editor-for-vue` 是中文生态富文本编辑器，西方训练语料覆盖弱，AI 容易写出过期 v4 API 或与 Vue 3 不兼容的用法。

## 依赖声明

来源：`package.json`。

- `@wangeditor/editor`
- `@wangeditor/editor-for-vue`（Vue 3 适配版本）

## 关键约定

- 版本线：本项目用的是 **Vue 3 适配版**（`editor-for-vue` 的 `^5` 线对应 Vue 3），不要写 Vue 2 的 `<editor>` 全局组件用法。
- 与 Element Plus 共存：wangEditor 实例挂载时注意 z-index 与 Element Plus 弹层冲突，通常需在菜单配置里调 `zIndex`。
- 内容回写：富文本内容以 HTML 字符串存储；落库前必须经过 `dompurify`（本项目已装）做净化，防止 XSS。

## AI 写代码须知

- ✅ 参考本项目现有富文本用法（搜索 `@wangeditor/editor` 的 import 点）。
- ✅ 输出 HTML 入库/落 CSP 前，必走 `dompurify.sanitize()`。
- ❌ 不要直接 `v-html` 渲染未净化的用户输入。
- ❌ 不要用 v4 的 `wangEditor.createMenu` 注册法，v5 用模块化 `registerMenu`。

## 待补充

> 把项目里实际用到的自定义菜单/插件配置摘录到本文件，方便后续 AI 复用。
