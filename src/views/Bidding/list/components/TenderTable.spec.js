import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import source from './TenderTable.vue?raw'

const tableStyles = readFileSync(join(process.cwd(), 'src/views/Bidding/list/styles/table.css'), 'utf-8')

describe('TenderTable 行内操作契约', () => {
  it('保留蓝图4.2.9定义的列结构（移除投标负责人列）', () => {
    expect(source).toContain('项目名称')
    expect(source).toContain('来源平台')
    expect(source).toContain('总部所在地')
    expect(source).toContain('招标主体')
    expect(source).toContain('项目类型')
    expect(source).toContain('客户类型')
    expect(source).toContain('报名截止日期')
    expect(source).toContain('开标时间')
    expect(source).toContain('标讯状态')
    expect(source).toContain('项目负责人')
    expect(source).toContain('项目部门')
    // 投标负责人列已移除（按用户要求）
    expect(source).not.toContain('投标负责人')
    expect(source).toContain('优先级')
    expect(source).toContain('创建人')
  })

  it('每个数据字段有 prop 属性', () => {
    const propCols = ['title', 'source', 'region', 'purchaserName', 'projectType', 'customerType',
      'registrationDeadline', 'bidOpeningTime', 'status', 'projectManagerName',
      'department', 'priority', 'creatorName']
    propCols.forEach(prop => {
      expect(source).toContain(`prop="${prop}"`)
    })
    // 确认投标负责人列的 prop 已被移除
    expect(source).not.toContain('prop="biddingPersonName"')
  })

  it('来源平台和标讯状态为独立列', () => {
    expect(source).toContain('prop="source"')
    expect(source).toContain('prop="status"')
    expect(source).toContain('getSourceTagType')
    expect(source).toContain('getTenderStatusTagType')
  })

  it('项目名称列支持点击查看详情', () => {
    expect(source).toContain("emit('view-detail'")
    expect(source).toContain('safeTenderUrl')
  })

  it('紧急截止日期高亮逻辑存在', () => {
    expect(source).toContain('isUrgentDeadline')
    expect(source).toContain('isExpired')
    expect(source).toContain('date-urgent')
    expect(source).toContain('date-expired')
  })

  it('table.css 包含日期紧急高亮和品牌色变量', () => {
    expect(tableStyles).toContain('.date-cell.date-urgent')
    expect(tableStyles).toContain('urgentPulse')
    expect(tableStyles).toContain('color: var(--color-danger)')
    expect(tableStyles).toContain('var(--brand-xiyu-logo)')
  })

  it('scrollbar-always-on 保留', () => {
    expect(source).toContain('scrollbar-always-on')
  })
})
