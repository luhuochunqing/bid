import { describe, expect, it } from 'vitest'
import marginSource from './MarginManagement.vue?raw'

// 项目负责人列显示格式统一为 "姓名 (工号)"，工号缺失时仅显示姓名
// MarginDTO 后端同步由后端 agent 处理；前端先用 row.projectLeaderEmployeeNumber，
// undefined 时回退到仅显示姓名（不会报错）
describe('MarginManagement.vue 项目负责人工号显示', () => {
  it('项目负责人列显示"姓名 (工号)"格式', () => {
    // 应引入 formatUserWithNameAndNumber 通用工具
    expect(marginSource).toContain('formatUserWithNameAndNumber')
    expect(marginSource).toContain('@/utils/userDisplay')
  })

  it('项目负责人列使用 row.projectLeaderName 和 row.projectLeaderEmployeeNumber', () => {
    const leaderColMatch = marginSource.match(/label="项目负责人"[^]*?<\/el-table-column>/)
    expect(leaderColMatch).not.toBeNull()
    expect(leaderColMatch[0]).toContain('row.projectLeaderName')
    expect(leaderColMatch[0]).toContain('row.projectLeaderEmployeeNumber')
  })
})
