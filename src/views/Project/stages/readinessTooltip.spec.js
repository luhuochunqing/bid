// Input: readinessTooltip 纯函数（蓝图 4.1.1.2.1 异常处理场景化提示）
// Output: vitest 单测 — 覆盖三类异常场景 + 边界
// Pos: src/views/Project/stages/ - 与 ClosureStage.vue 共享 readinessTooltip.js
// 维护声明: 一旦 CasePrecipitationAppService#getReadiness 的 missingItems 文案调整，
//          必须同步更新本测试期望串；通过 src/views/Project/stages/readinessTooltip.js 关键词维护。

import { describe, expect, it } from 'vitest'
import { readinessToTooltip } from './readinessTooltip.js'

describe('readinessToTooltip — 蓝图 4.1.1.2.1 异常处理场景化提示', () => {
  it('空数组返回空串（不显示 tooltip）', () => {
    expect(readinessToTooltip([])).toBe('')
  })

  it('null / undefined 视为无缺失', () => {
    expect(readinessToTooltip(null)).toBe('')
    expect(readinessToTooltip(undefined)).toBe('')
  })

  it('场景 A：标书文件缺失 → 渲染 "上传标书后即可触发"', () => {
    const html = readinessToTooltip(['缺少标书文件，请先在标书编制阶段上传标书文件'])
    expect(html).toContain('请补齐以下前置条件：')
    expect(html).toContain('• 上传标书后即可触发')
    expect(html).toContain('<br/>')
  })

  it('场景 B：评分项为空 → 渲染 "先在标书编制阶段完成招标文件解析"', () => {
    const html = readinessToTooltip(['缺少评分项，请先在标书编制阶段完成招标文件解析'])
    expect(html).toContain('请补齐以下前置条件：')
    expect(html).toContain('• 先在标书编制阶段完成招标文件解析')
  })

  it('场景 C：项目未结项 → 渲染 "项目结项后才能触发"', () => {
    const html = readinessToTooltip([
      '项目阶段未进入 CLOSED（当前阶段：EVALUATING），需先完成复盘并结项',
    ])
    expect(html).toContain('请补齐以下前置条件：')
    expect(html).toContain('• 项目结项后才能触发')
  })

  it('多缺失项并存 → 多行拼接（用 <br/> 分隔）', () => {
    const html = readinessToTooltip([
      '缺少标书文件，请先在标书编制阶段上传标书文件',
      '缺少评分项，请先在标书编制阶段完成招标文件解析',
      '项目阶段未进入 CLOSED（当前阶段：DRAFTING），需先完成复盘并结项',
    ])
    // 必须同时包含三个场景的提示
    expect(html).toContain('• 上传标书后即可触发')
    expect(html).toContain('• 先在标书编制阶段完成招标文件解析')
    expect(html).toContain('• 项目结项后才能触发')
    // 顺序：与输入一致
    const lines = html.split('<br/>')
    expect(lines[0]).toBe('请补齐以下前置条件：')
    expect(lines[1]).toBe('• 上传标书后即可触发')
    expect(lines[2]).toBe('• 先在标书编制阶段完成招标文件解析')
    expect(lines[3]).toBe('• 项目结项后才能触发')
  })

  it('未识别关键词 → 原样回显（不丢信息）', () => {
    const html = readinessToTooltip(['其他系统异常：数据库连接失败'])
    expect(html).toContain('• 其他系统异常：数据库连接失败')
  })

  it('BID 关键词匹配（贴近后端文案）', () => {
    // 实际后端文案不会发纯英文，但 BID 分类也会触发同一分支
    const html = readinessToTooltip(['缺少 BID 文件，请先上传'])
    expect(html).toContain('• 上传标书后即可触发')
  })
})
