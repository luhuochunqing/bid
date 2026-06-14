// Input: ProjectFileUpload.vue 组件
// Output: H13 cookie 契约烟囱测试
// Pos: src/components/common/ — 与组件同目录
//
// H13 根治 (2026-06-14): access token 迁移到 HttpOnly cookie 后,
// 所有打到 /api/** 认证端点的 <el-upload> 必须声明 :with-credentials="true",
// 否则浏览器不会自动携带 access_token cookie → 上传被 401 拒绝。
// 该组件即声明了该属性 (见模板根 <el-upload :with-credentials="true">)。
// 此处以动态 import 校验组件在 with-credentials 改造后仍可编译导出,
// 运行时上传链路由 E2E 上传 flow 覆盖。

import { describe, it, expect } from 'vitest'

describe('ProjectFileUpload (H13 cookie 契约)', () => {
  it('with-credentials 改造后组件仍可编译并导出 Vue 组件', async () => {
    const mod = await import('./ProjectFileUpload.vue')
    expect(mod.default).toBeTruthy()
  })
})
