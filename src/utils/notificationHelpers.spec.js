// Input: notificationHelpers.js
// Output: unit tests for payload parsing + diff + mention helpers
// Pos: src/utils/ - Utility test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { describe, it, expect } from 'vitest'
import {
  parseNotificationPayload,
  extractChanges,
  hasChangeDiff,
  parseMentionContent,
  resolveNotificationRoute,
  formatNotificationTime,
  getNotificationTypeLabel
} from './notificationHelpers.js'

describe('parseNotificationPayload', () => {
  it('returns empty object for null/undefined', () => {
    expect(parseNotificationPayload(null)).toEqual({})
    expect(parseNotificationPayload(undefined)).toEqual({})
    expect(parseNotificationPayload('')).toEqual({})
  })

  it('parses valid JSON string', () => {
    expect(parseNotificationPayload('{"changes":[{"field":"a"}]}')).toEqual({
      changes: [{ field: 'a' }]
    })
  })

  it('returns empty object for invalid JSON', () => {
    expect(parseNotificationPayload('{broken')).toEqual({})
  })

  it('returns the object directly if already parsed', () => {
    expect(parseNotificationPayload({ changes: [] })).toEqual({ changes: [] })
  })
})

describe('extractChanges', () => {
  it('returns empty list when no payload', () => {
    expect(extractChanges({})).toEqual([])
    expect(extractChanges(null)).toEqual([])
  })

  it('extracts changes from JSON payload', () => {
    const n = { payloadJson: '{"changes":[{"field":"title","before":"a","after":"b"}]}' }
    expect(extractChanges(n)).toHaveLength(1)
  })

  it('returns empty list when payload has no changes', () => {
    expect(extractChanges({ payloadJson: '{}' })).toEqual([])
  })
})

describe('hasChangeDiff', () => {
  it('returns true when changes present', () => {
    expect(hasChangeDiff({ payloadJson: '{"changes":[{"field":"x"}]}' })).toBe(true)
  })

  it('returns false when no changes', () => {
    expect(hasChangeDiff({})).toBe(false)
    expect(hasChangeDiff({ payloadJson: '{"changes":[]}' })).toBe(false)
  })
})

describe('parseMentionContent', () => {
  it('returns empty for null/blank', () => {
    expect(parseMentionContent(null)).toEqual({ plainText: '', mentionedUserIds: [] })
    expect(parseMentionContent('')).toEqual({ plainText: '', mentionedUserIds: [] })
  })

  it('extracts single mention', () => {
    const result = parseMentionContent('@[Alice](7) hello')
    expect(result.mentionedUserIds).toEqual([7])
    expect(result.plainText).toBe('@Alice hello')
  })

  it('deduplicates mentions', () => {
    const result = parseMentionContent('@[Alice](7) and @[Alice](7)')
    expect(result.mentionedUserIds).toEqual([7])
  })

  it('extracts multiple distinct mentions', () => {
    const result = parseMentionContent('@[A](1) @[B](2) @[C](3)')
    expect(result.mentionedUserIds).toEqual([1, 2, 3])
  })

  it('ignores invalid tokens', () => {
    expect(parseMentionContent('plain @text').mentionedUserIds).toEqual([])
  })
})

describe('resolveNotificationRoute', () => {
  it('returns null for missing fields', () => {
    expect(resolveNotificationRoute({})).toBeNull()
    expect(resolveNotificationRoute({ sourceEntityType: 'PROJECT' })).toBeNull()
  })

  it('resolves valid project route', () => {
    expect(resolveNotificationRoute({ sourceEntityType: 'PROJECT', sourceEntityId: 42 }))
      .toBe('/project/42')
  })

  it('resolves task route through payload projectId', () => {
    expect(resolveNotificationRoute({
      sourceEntityType: 'TASK',
      sourceEntityId: 99,
      payloadJson: '{"projectId":42}'
    })).toBe('/project/42?taskId=99')
  })

  it('rejects non-numeric or negative ids', () => {
    expect(resolveNotificationRoute({ sourceEntityType: 'PROJECT', sourceEntityId: '../admin' }))
      .toBeNull()
    expect(resolveNotificationRoute({ sourceEntityType: 'PROJECT', sourceEntityId: -1 }))
      .toBeNull()
  })
})

describe('formatNotificationTime', () => {
  it('returns empty for null', () => {
    expect(formatNotificationTime(null)).toBe('')
  })

  it('returns 刚刚 for recent', () => {
    const now = new Date().toISOString()
    expect(formatNotificationTime(now)).toBe('刚刚')
  })
})

describe('getNotificationTypeLabel', () => {
  it('maps known types', () => {
    expect(getNotificationTypeLabel('MENTION')).toBe('提及')
    expect(getNotificationTypeLabel('SYSTEM')).toBe('系统')
  })

  it('returns type when unknown', () => {
    expect(getNotificationTypeLabel('FOO')).toBe('FOO')
  })
})
