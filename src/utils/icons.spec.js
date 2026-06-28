import { describe, it, expect } from 'vitest'
import { icons, basicIcons, statusIcons, aiIcons, navIcons, userIcons, fileIcons, messageIcons, timeIcons } from './icons.js'

describe('icons', () => {
  it('导出非空图标对象', () => {
    expect(icons).toBeDefined()
    expect(Object.keys(icons).length).toBeGreaterThan(0)
  })

  it('基础图标包含常用图标', () => {
    expect(basicIcons.Plus).toBeDefined()
    expect(basicIcons.Search).toBeDefined()
  })

  it('状态图标包含 Warning', () => {
    expect(statusIcons.Warning).toBeDefined()
  })

  it('AI图标包含 Timer 和 Clock', () => {
    expect(aiIcons.Timer).toBeDefined()
    expect(aiIcons.Clock).toBeDefined()
  })

  it('不包含已移除的无效图标（Success, Shield, Thumb）', () => {
    expect(icons.Success).toBeUndefined()
    expect(icons.Shield).toBeUndefined()
    expect(icons.Thumb).toBeUndefined()
  })
})
