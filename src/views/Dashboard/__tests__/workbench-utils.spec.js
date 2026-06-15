import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  normalizeProjectForWorkbench,
  normalizeCalendarEvent,
  normalizeAlertForTodo,
  extractCustomersFromProjects,
} from '@/views/Dashboard/workbench-utils.js'

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-04-22T09:00:00+08:00'))
})

afterEach(() => {
  vi.useRealTimers()
})

// ---------------------------------------------------------------------------
// 1. normalizeProjectForWorkbench
// ---------------------------------------------------------------------------
describe('normalizeProjectForWorkbench', () => {
  const FAR_FUTURE = '2099-12-31'

  it('maps a normal BIDDING project correctly', () => {
    const result = normalizeProjectForWorkbench({
      id: 1,
      name: '某某投标项目',
      status: 'BIDDING',
      managerId: 5,
      managerName: '张三',
      startDate: '2026-04-01',
      endDate: FAR_FUTURE,
      customerManager: '李四',
      teamMembers: [1, 2, 3],
    })
    expect(result.id).toBe(1)
    expect(result.name).toBe('某某投标项目')
    expect(result.status).toBe('投标中')
    expect(result.progress).toBe(95)
    expect(result.deadline).toBe(FAR_FUTURE)
    expect(result.manager).toBe('张三')
    expect(result.priority).toBe('low')
  })

  it('maps status INITIATED → "已立项" / progress 10', () => {
    const r = normalizeProjectForWorkbench({ status: 'INITIATED', endDate: FAR_FUTURE })
    expect(r.status).toBe('已立项')
    expect(r.progress).toBe(10)
  })

  it('maps status EVALUATING → "评标中" / progress 75', () => {
    const r = normalizeProjectForWorkbench({ status: 'EVALUATING', endDate: FAR_FUTURE })
    expect(r.status).toBe('评标中')
    expect(r.progress).toBe(75)
  })

  it('maps status WON → "已中标" / progress 100', () => {
    const r = normalizeProjectForWorkbench({ status: 'WON', endDate: FAR_FUTURE })
    expect(r.status).toBe('已中标')
    expect(r.progress).toBe(100)
  })

  it('maps status BIDDING → "投标中" / progress 95', () => {
    const r = normalizeProjectForWorkbench({ status: 'BIDDING', endDate: FAR_FUTURE })
    expect(r.status).toBe('投标中')
    expect(r.progress).toBe(95)
  })

  it('maps status LOST → "未中标" / progress 100', () => {
    const r = normalizeProjectForWorkbench({ status: 'LOST', endDate: FAR_FUTURE })
    expect(r.status).toBe('未中标')
    expect(r.progress).toBe(100)
  })

  it('derives priority "high" when deadline ≤ 7 days away', () => {
    const soon = new Date()
    soon.setDate(soon.getDate() + 3)
    const r = normalizeProjectForWorkbench({ status: 'BIDDING', endDate: soon.toISOString().slice(0, 10) })
    expect(r.priority).toBe('high')
  })

  it('derives priority "medium" when deadline ≤ 30 days away', () => {
    const mid = new Date()
    mid.setDate(mid.getDate() + 20)
    const r = normalizeProjectForWorkbench({ status: 'BIDDING', endDate: mid.toISOString().slice(0, 10) })
    expect(r.priority).toBe('medium')
  })

  it('derives priority "low" when deadline > 30 days away', () => {
    const r = normalizeProjectForWorkbench({ status: 'BIDDING', endDate: FAR_FUTURE })
    expect(r.priority).toBe('low')
  })

  it('handles missing managerName gracefully', () => {
    const r = normalizeProjectForWorkbench({ status: 'BIDDING', endDate: FAR_FUTURE })
    expect(r.manager).toBe('')
  })

  it('handles missing endDate gracefully (no crash)', () => {
    const r = normalizeProjectForWorkbench({ status: 'BIDDING' })
    expect(r.deadline).toBeUndefined()
  })

  it('returns default object for null input', () => {
    const r = normalizeProjectForWorkbench(null)
    expect(r).toEqual({
      id: undefined,
      name: '',
      status: '',
      progress: 0,
      deadline: undefined,
      manager: '',
      priority: 'low',
    })
  })
})

// ---------------------------------------------------------------------------
// 2. normalizeCalendarEvent
// ---------------------------------------------------------------------------
describe('normalizeCalendarEvent', () => {
  const baseEvent = {
    id: 1,
    eventDate: '2026-05-15',
    eventType: 'DEADLINE',
    title: '某某项目截标',
    description: '描述内容',
    projectId: 3,
    isUrgent: true,
  }

  it('maps a normal DEADLINE event correctly', () => {
    const r = normalizeCalendarEvent(baseEvent)
    expect(r.id).toBe(1)
    expect(r.projectId).toBe(3)
    expect(r.date).toBe('2026-05-15')
    expect(r.eventType).toBe('DEADLINE')
    expect(r.type).toBe('deadline')
    expect(r.title).toBe('某某项目截标')
    expect(r.project).toBe('某某项目截标')
    expect(r.urgent).toBe(true)
    expect(r.description).toBe('描述内容')
  })

  it('maps SUBMISSION → "bid"', () => {
    expect(normalizeCalendarEvent({ ...baseEvent, eventType: 'SUBMISSION' }).type).toBe('bid')
  })

  it('maps REVIEW → "review"', () => {
    expect(normalizeCalendarEvent({ ...baseEvent, eventType: 'REVIEW' }).type).toBe('review')
  })

  it('maps MEETING → "review"', () => {
    expect(normalizeCalendarEvent({ ...baseEvent, eventType: 'MEETING' }).type).toBe('review')
  })

  it('maps MILESTONE → "milestone"', () => {
    expect(normalizeCalendarEvent({ ...baseEvent, eventType: 'MILESTONE' }).type).toBe('milestone')
  })

  it('maps REMINDER → "reminder"', () => {
    expect(normalizeCalendarEvent({ ...baseEvent, eventType: 'REMINDER' }).type).toBe('reminder')
  })

  it('sets urgent to false when isUrgent is missing', () => {
    const r = normalizeCalendarEvent({ ...baseEvent, isUrgent: undefined })
    expect(r.urgent).toBe(false)
  })

  it('handles missing title gracefully', () => {
    const r = normalizeCalendarEvent({ ...baseEvent, title: undefined })
    expect(r.title).toBe('')
    expect(r.project).toBe('')
  })

  it('returns default object for null input', () => {
    const r = normalizeCalendarEvent(null)
    expect(r).toEqual({
      id: undefined,
      projectId: null,
      date: undefined,
      eventType: 'REMINDER',
      type: 'reminder',
      title: '',
      project: '',
      urgent: false,
      description: '',
    })
  })
})

// ---------------------------------------------------------------------------
// 3. normalizeAlertForTodo
// ---------------------------------------------------------------------------
describe('normalizeAlertForTodo', () => {
  const baseAlert = {
    id: 10,
    level: 'HIGH',
    message: '项目A保证金即将到期',
    relatedId: 'Project:5',
    resolved: false,
    createdAt: '2026-04-15T10:00:00',
  }

  it('maps a normal HIGH alert correctly', () => {
    const r = normalizeAlertForTodo(baseAlert)
    expect(r.id).toBe('alert-10')
    expect(r.title).toBe('项目A保证金即将到期')
    expect(r.priority).toBe('high')
    expect(r.type).toBe('warning')
    expect(r.done).toBe(false)
    expect(r.deadline).toBe('2026-04-15')
    expect(r.sourceType).toBe('alert')
  })

  it('maps level CRITICAL → "urgent"', () => {
    expect(normalizeAlertForTodo({ ...baseAlert, level: 'CRITICAL' }).priority).toBe('urgent')
  })

  it('maps level MEDIUM → "medium"', () => {
    expect(normalizeAlertForTodo({ ...baseAlert, level: 'MEDIUM' }).priority).toBe('medium')
  })

  it('maps level LOW → "low"', () => {
    expect(normalizeAlertForTodo({ ...baseAlert, level: 'LOW' }).priority).toBe('low')
  })

  it('prefers severity field when backend returns dto payload', () => {
    expect(normalizeAlertForTodo({ ...baseAlert, level: undefined, severity: 'CRITICAL' }).priority).toBe('urgent')
  })

  it('sets done to true when resolved is true', () => {
    expect(normalizeAlertForTodo({ ...baseAlert, resolved: true }).done).toBe(true)
  })

  it('sets done to true when dto status is RESOLVED', () => {
    expect(normalizeAlertForTodo({ ...baseAlert, resolved: false, status: 'RESOLVED' }).done).toBe(true)
  })

  it('extracts date portion from ISO createdAt', () => {
    const r = normalizeAlertForTodo({ ...baseAlert, createdAt: '2026-12-31T23:59:59' })
    expect(r.deadline).toBe('2026-12-31')
  })

  it('handles missing message gracefully', () => {
    const r = normalizeAlertForTodo({ ...baseAlert, message: undefined })
    expect(r.title).toBe('')
  })

  it('handles missing createdAt gracefully', () => {
    const r = normalizeAlertForTodo({ ...baseAlert, createdAt: undefined })
    expect(r.deadline).toBeUndefined()
  })

  it('returns default object for null input', () => {
    const r = normalizeAlertForTodo(null)
    expect(r).toEqual({
      id: 'alert-undefined',
      title: '',
      priority: 'low',
      type: 'warning',
      done: false,
      deadline: undefined,
      sourceType: 'alert',
    })
  })
})

// ---------------------------------------------------------------------------
// 4. extractCustomersFromProjects
// ---------------------------------------------------------------------------
describe('extractCustomersFromProjects', () => {
  it('groups projects by customerManagerId and counts them', () => {
    const projects = [
      { id: 1, customerManager: '李四', customerManagerId: 101, name: '项目A', status: '投标中' },
      { id: 2, customerManager: '李四', customerManagerId: 101, name: '项目B', status: '投标中' },
      { id: 3, customerManager: '王五', customerManagerId: 102, name: '项目C', status: '已立项' },
    ]
    const result = extractCustomersFromProjects(projects)
    expect(result).toHaveLength(2)
    const lisi = result.find(c => c.id === 101)
    expect(lisi.name).toBe('李四')
    expect(lisi.projectCount).toBe(2)
    expect(lisi.company).toBe('')
  })

  it('sets status "跟进中"/"warning" when any project is BIDDING', () => {
    const projects = [
      { id: 1, customerManager: '李四', customerManagerId: 101, name: '项目A', status: '投标中' },
      { id: 2, customerManager: '李四', customerManagerId: 101, name: '项目B', status: '已中标' },
    ]
    const r = extractCustomersFromProjects(projects)
    const lisi = r.find(c => c.id === 101)
    expect(lisi.status).toBe('跟进中')
    expect(lisi.statusType).toBe('warning')
  })

  it('sets status "跟进中"/"warning" when any project is EVALUATING', () => {
    const projects = [
      { id: 1, customerManager: '张三', customerManagerId: 201, name: '项目A', status: '评标中' },
    ]
    const r = extractCustomersFromProjects(projects)
    expect(r[0].status).toBe('跟进中')
    expect(r[0].statusType).toBe('warning')
  })

  it('sets status "已完成"/"success" when ALL projects are terminal', () => {
    const projects = [
      { id: 1, customerManager: '李四', customerManagerId: 101, name: '项目A', status: '已中标' },
      { id: 2, customerManager: '李四', customerManagerId: 101, name: '项目B', status: '未中标' },
    ]
    const r = extractCustomersFromProjects(projects)
    expect(r[0].status).toBe('已完成')
    expect(r[0].statusType).toBe('success')
  })

  it('sets status "新客户"/"info" for other statuses', () => {
    const projects = [
      { id: 1, customerManager: '王五', customerManagerId: 102, name: '项目C', status: '已立项' },
    ]
    const r = extractCustomersFromProjects(projects)
    expect(r[0].status).toBe('新客户')
    expect(r[0].statusType).toBe('info')
  })

  it('returns empty array for empty input', () => {
    expect(extractCustomersFromProjects([])).toEqual([])
  })

  it('returns empty array for null input', () => {
    expect(extractCustomersFromProjects(null)).toEqual([])
  })

  it('skips projects without customerManager field', () => {
    const projects = [
      { id: 1, name: '项目A', status: '投标中' }, // no customerManager
      { id: 2, customerManager: '李四', customerManagerId: 101, name: '项目B', status: '投标中' },
    ]
    const r = extractCustomersFromProjects(projects)
    expect(r).toHaveLength(1)
    expect(r[0].id).toBe(101)
  })
})
