/**
 * ProjectDetailShell 组件测试
 * 验证项目详情页外壳组件能正常加载
 */
import { describe, it, expect } from 'vitest'
import ProjectDetailShell from './ProjectDetailShell.vue'

describe('ProjectDetailShell', () => {
  it('component can be imported', () => {
    // 简化测试：只验证组件文件可以导入
    expect(ProjectDetailShell).toBeDefined()
  })
})
