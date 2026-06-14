// Input: ProjectDetailWorkflowDialog.vue 组件
// Output: H13 cookie 契约烟囱测试
// Pos: src/components/project/detail/ — 与组件同目录
//
// H13 根治 (2026-06-14): access token 迁移到 HttpOnly cookie 后,
// 标书编制流程弹窗内的多个 <el-upload> (初稿/用印文件) 均声明了
// :with-credentials="true" 以走 HttpOnly cookie, 不再依赖 Authorization header。
// 该组件依赖大量 detail prop 注入 (userStore/forms/methods), 完整 mount 成本高,
// 此处以动态 import 校验改造后组件仍可编译导出, 运行时链路由 E2E workflow flow 覆盖。

import { describe, it, expect } from 'vitest'

describe('ProjectDetailWorkflowDialog (H13 cookie 契约)', () => {
  it('with-credentials 改造后组件仍可编译并导出 Vue 组件', async () => {
    const mod = await import('./ProjectDetailWorkflowDialog.vue')
    expect(mod.default).toBeTruthy()
  })
})
