// Input: bid result normalizer fixtures and expected frontend mappings
// Output: vitest coverage for bid result API normalization
// Pos: src/api/modules/ - Bid result API tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, expect, it } from 'vitest'

import {
  normalizeCompetitorReport,
  normalizeDetail,
  normalizeOverview,
  normalizeReminder
} from './bidResults.normalizers.js'

describe('bidResults normalizers', () => {
  it('maps overview fields from backend names', () => {
    expect(normalizeOverview({
      pendingFetchCount: 3,
      pendingReminderCount: 5,
      competitorCount: 7
    })).toEqual({
      lastSyncTime: '',
      pendingCount: 3,
      uploadPending: 5,
      competitorCount: 7
    })
  })

  it('maps reminder owner and type fields', () => {
    expect(normalizeReminder({
      id: 1,
      ownerName: '张三',
      reminderType: 'NOTICE',
      status: 'UPLOADED'
    })).toMatchObject({
      id: 1,
      owner: '张三',
      type: 'notice',
      status: 'uploaded'
    })
  })

  it('unwraps detail payload and keeps attachments and competitors', () => {
    const detail = normalizeDetail({
      fetchResult: {
        id: 9,
        projectId: 18,
        projectName: '西域项目',
        result: 'WON',
        amount: 1200,
        contractStartDate: '2026-01-01',
        contractEndDate: '2026-12-31',
        contractDurationMonths: 12,
        remark: '备注',
        skuCount: 66
      },
      reminder: { id: 1, reminderType: 'NOTICE' },
      noticeAttachment: { documentId: 11, name: 'notice.pdf' },
      competitorWins: [{ competitorName: '竞品A' }]
    })

    expect(detail).toMatchObject({
      id: 9,
      projectId: 18,
      projectName: '西域项目',
      result: 'won',
      reminders: [{ id: 1, type: 'notice' }],
      attachments: {
        noticeDocument: { id: 11, name: 'notice.pdf' }
      },
      competitors: [{ company: '竞品A' }]
    })
  })

  it('maps competitor payment terms field', () => {
    expect(normalizeCompetitorReport({
      company: '竞品B',
      paymentTerms: '月结30天',
      projectCount: 2
    })).toMatchObject({
      company: '竞品B',
      payment: '月结30天',
      projectCount: 2
    })
  })
})
