import { describe, expect, it } from 'vitest'
import {
  buildProjectBaselineActivities,
  formatProjectActivityTime,
  resolveProjectActivityUser,
} from './useProjectDetailActivities.js'

describe('project detail activities', () => {
  it('does not seed the legacy fixed mock timeline', () => {
    const activities = buildProjectBaselineActivities({
      id: 108,
      createdAt: '2026-04-27T17:38:12',
      managerName: '小王',
    })

    expect(activities).toEqual([{
      id: 'project-created-108',
      user: '小王',
      action: '创建了项目',
      time: '2026-04-27 17:38',
    }])
    expect(activities.map((item) => item.time)).not.toContain('2025-02-20 10:00')
  })

  it('uses the current user only as a fallback when project creator fields are absent', () => {
    expect(resolveProjectActivityUser({ ownerName: '负责人' }, '当前用户')).toBe('负责人')
    expect(resolveProjectActivityUser({}, '当前用户')).toBe('当前用户')
  })

  it('keeps unknown date values visible instead of inventing a time', () => {
    expect(formatProjectActivityTime('待确认')).toBe('待确认')
    expect(formatProjectActivityTime('')).toBe('')
  })

  it('returns no baseline activity when project is missing', () => {
    expect(buildProjectBaselineActivities(null, '小王')).toEqual([])
    expect(buildProjectBaselineActivities({}, '小王')).toEqual([])
  })
})
