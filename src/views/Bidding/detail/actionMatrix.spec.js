import { describe, it, expect } from 'vitest'
import {
  getHeaderActions,
  getBottomActions,
  shouldShowLogsTab,
} from './actionMatrix'

// ---------------------------------------------------------------------------
// Domain constants matching the production status enum / role names
// ---------------------------------------------------------------------------
const PENDING = 'PENDING_ASSIGNMENT'
const TRACKING = 'TRACKING'
const EVALUATED = 'EVALUATED'
const BIDDING = 'BIDDING'
const WON = 'WON'
const LOST = 'LOST'
const ABANDONED = 'ABANDONED'

// ---------------------------------------------------------------------------
// Expected action shapes (see spec in actionMatrix.js)
// ---------------------------------------------------------------------------
const ACTIONS = Object.freeze({
  ASSIGN: { key: 'assign', label: '分配', type: 'primary', icon: null },
  TRANSFER: { key: 'transfer', label: '转派', type: 'warning', icon: null },
  DELETE: { key: 'delete', label: '删除', type: 'danger', icon: null },
  VIEW_PROJECT: {
    key: 'viewProject',
    label: '查看项目',
    type: 'primary',
    icon: null,
  },
  EDIT: { key: 'edit', label: '编辑', type: 'primary', icon: 'edit' },
  EDIT_BASIC: {
    key: 'editBasic',
    label: '编辑',
    type: 'primary',
    icon: 'edit',
  },
  EDIT_EVALUATION: {
    key: 'editEvaluation',
    label: '编辑评估表',
    type: 'primary',
    icon: 'edit',
  },
  SAVE: { key: 'save', label: '保存', type: 'primary', icon: null },
  CANCEL: { key: 'cancel', label: '取消', type: 'default', icon: null },
  BID: { key: 'bid', label: '立即投标', type: 'success', icon: null },
  ABANDON: {
    key: 'abandon',
    label: '放弃投标',
    type: 'danger',
    icon: null,
  },
  VIEW_ANNOUNCEMENT: {
    key: 'viewAnnouncement',
    label: '查看官网公告',
    type: 'default',
    icon: null,
  },
})

/** Assert every property on every action in the array matches expectation. */
function expectActions(actual, expectedList) {
  expect(actual).toHaveLength(expectedList.length)
  expectedList.forEach((exp, i) => {
    expect(actual[i].key).toBe(exp.key)
    expect(actual[i].label).toBe(exp.label)
    expect(actual[i].type).toBe(exp.type)
    expect(actual[i].icon).toBe(exp.icon)
  })
}

// ===========================================================================
// getHeaderActions
// ===========================================================================
describe('getHeaderActions', () => {
  describe('PENDING_ASSIGNMENT', () => {
    it('admin sees assign + delete', () => {
      expectActions(getHeaderActions(PENDING, 'admin'), [
        ACTIONS.ASSIGN,
        ACTIONS.DELETE,
      ])
    })

    it('bid_admin sees assign + delete', () => {
      expectActions(getHeaderActions(PENDING, '/bidAdmin'), [
        ACTIONS.ASSIGN,
        ACTIONS.DELETE,
      ])
    })

    it('bid_lead sees assign only', () => {
      expectActions(getHeaderActions(PENDING, 'bid-TeamLeader'), [
        ACTIONS.ASSIGN,
      ])
    })

    it('sales sees nothing', () => {
      expect(getHeaderActions(PENDING, 'bid-projectLeader')).toEqual([])
    })

    it('bid_specialist sees nothing', () => {
      expect(getHeaderActions(PENDING, 'bid-Team')).toEqual([])
    })
  })

  describe('TRACKING', () => {
    it('bid_admin sees transfer + delete', () => {
      expectActions(getHeaderActions(TRACKING, '/bidAdmin'), [
        ACTIONS.TRANSFER,
        ACTIONS.DELETE,
      ])
    })

    it('bid_lead sees transfer only', () => {
      expectActions(getHeaderActions(TRACKING, 'bid-TeamLeader'), [
        ACTIONS.TRANSFER,
      ])
    })


    it('sales sees nothing', () => {
      expect(getHeaderActions(TRACKING, 'bid-projectLeader')).toEqual([])
    })

    it('bid_specialist sees nothing', () => {
      expect(getHeaderActions(TRACKING, 'bid-Team')).toEqual([])
    })
  })

  describe('EVALUATED', () => {
    it('bid_admin sees transfer only (delete not allowed per §4.2.8)', () => {
      expectActions(getHeaderActions(EVALUATED, '/bidAdmin'), [
        ACTIONS.TRANSFER,
      ])
    })

    it('bid_lead sees transfer only', () => {
      expectActions(getHeaderActions(EVALUATED, 'bid-TeamLeader'), [
        ACTIONS.TRANSFER,
      ])
    })

    it('sales sees nothing (delete not allowed per §4.2.8)', () => {
      expect(getHeaderActions(EVALUATED, 'bid-projectLeader')).toEqual([])
    })

    it('bid_specialist sees nothing', () => {
      expect(getHeaderActions(EVALUATED, 'bid-Team')).toEqual([])
    })
  })

  describe('BIDDING', () => {
    it('bid_admin sees viewProject', () => {
      expectActions(getHeaderActions(BIDDING, '/bidAdmin'), [
        ACTIONS.VIEW_PROJECT,
      ])
    })

    it('sales sees viewProject', () => {
      expectActions(getHeaderActions(BIDDING, 'bid-projectLeader'), [
        ACTIONS.VIEW_PROJECT,
      ])
    })

    it('bid_specialist sees viewProject', () => {
      expectActions(getHeaderActions(BIDDING, 'bid-Team'), [
        ACTIONS.VIEW_PROJECT,
      ])
    })
  })

  describe('WON', () => {
    it('bid_admin sees viewProject', () => {
      expectActions(getHeaderActions(WON, '/bidAdmin'), [
        ACTIONS.VIEW_PROJECT,
      ])
    })
  })

  describe('LOST', () => {
    it('bid_admin sees viewProject', () => {
      expectActions(getHeaderActions(LOST, '/bidAdmin'), [
        ACTIONS.VIEW_PROJECT,
      ])
    })
  })

  describe('ABANDONED', () => {
    it('bid_admin sees nothing', () => {
      expect(getHeaderActions(ABANDONED, '/bidAdmin')).toEqual([])
    })

    it('sales sees nothing', () => {
      expect(getHeaderActions(ABANDONED, 'bid-projectLeader')).toEqual([])
    })

    it('bid_specialist sees nothing', () => {
      expect(getHeaderActions(ABANDONED, 'bid-Team')).toEqual([])
    })
  })

  describe('edge cases', () => {
    it('returns empty array for unknown role', () => {
      expect(getHeaderActions(PENDING, 'auditor')).toEqual([])
    })

    it('returns empty array for unknown status', () => {
      expect(getHeaderActions('UNKNOWN_STATUS', '/bidAdmin')).toEqual([])
    })
  })


  describe('PENDING_ASSIGNMENT with creator context', () => {
    it('sales as creator sees edit + delete', () => {
      expectActions(getHeaderActions(PENDING, 'bid-projectLeader', false, 1, 1), [
        ACTIONS.EDIT,
        ACTIONS.DELETE,
      ])
    })

    it('bid_specialist as creator sees edit + delete', () => {
      expectActions(getHeaderActions(PENDING, 'bid-Team', false, 1, 1), [
        ACTIONS.EDIT,
        ACTIONS.DELETE,
      ])
    })

    it('sales as non-creator sees nothing', () => {
      expect(getHeaderActions(PENDING, 'bid-projectLeader', false, 1, 2)).toEqual([])
    })

    it('bid_specialist as non-creator sees nothing', () => {
      expect(getHeaderActions(PENDING, 'bid-Team', false, 1, 2)).toEqual([])
    })

    it('admin sees assign + delete regardless of creator', () => {
      expectActions(getHeaderActions(PENDING, 'admin', false, 1, 2), [
        ACTIONS.ASSIGN,
        ACTIONS.DELETE,
      ])
    })

    it('bid_admin sees assign + delete regardless of creator', () => {
      expectActions(getHeaderActions(PENDING, '/bidAdmin', false, 1, 2), [
        ACTIONS.ASSIGN,
        ACTIONS.DELETE,
      ])
    })

    it('sales as creator sees nothing in TRACKING status', () => {
      expect(getHeaderActions(TRACKING, 'bid-projectLeader', false, 1, 1)).toEqual([])
    })

    it('bid_specialist as creator sees nothing in TRACKING status', () => {
      expect(getHeaderActions(TRACKING, 'bid-Team', false, 1, 1)).toEqual([])
    })

    it('backward compatible: no creatorId passed defaults to no buttons', () => {
      expect(getHeaderActions(PENDING, 'bid-projectLeader')).toEqual([])
      expect(getHeaderActions(PENDING, 'bid-Team')).toEqual([])
    })
  })

  describe('viewAnnouncement button', () => {
    it('includes viewAnnouncement when hasOriginalUrl is true', () => {
      expectActions(getHeaderActions(ABANDONED, 'bid-projectLeader', true), [
        ACTIONS.VIEW_ANNOUNCEMENT,
      ])
    })

    it('omits viewAnnouncement when hasOriginalUrl is false', () => {
      expect(getHeaderActions(ABANDONED, 'bid-projectLeader', false)).toEqual([])
    })

    it('omits viewAnnouncement by default', () => {
      expect(getHeaderActions(ABANDONED, 'bid-projectLeader')).toEqual([])
    })

    it('appends viewAnnouncement after matrix actions', () => {
      const actions = getHeaderActions(PENDING, 'admin', true)
      expect(actions).toHaveLength(3)
      expect(actions[2].key).toBe('viewAnnouncement')
    })
  })
})

// ===========================================================================
// getBottomActions
// ===========================================================================
describe('getBottomActions', () => {
  describe('PENDING_ASSIGNMENT', () => {
    it('admin sees edit', () => {
      expectActions(getBottomActions(PENDING, 'admin'), [
        ACTIONS.EDIT,
      ])
    })

    it('bid_admin sees edit', () => {
      expectActions(getBottomActions(PENDING, '/bidAdmin'), [
        ACTIONS.EDIT,
      ])
    })

    it('bid_lead sees edit', () => {
      expectActions(getBottomActions(PENDING, 'bid-TeamLeader'), [
        ACTIONS.EDIT,
      ])
    })

    it('sales sees nothing', () => {
      expect(getBottomActions(PENDING, 'bid-projectLeader')).toEqual([])
    })

    it('bid_specialist sees nothing', () => {
      expect(getBottomActions(PENDING, 'bid-Team')).toEqual([])
    })
  })

  describe('TRACKING', () => {
    it('admin_lead sees nextStep in basic info tab', () => {
      expectActions(getBottomActions(TRACKING, '/bidAdmin'), [
        { key: 'nextStep', label: '下一步', type: 'primary', icon: null },
      ])
    })

    it('admin sees nextStep in basic info tab', () => {
      expectActions(getBottomActions(TRACKING, 'admin', false, false), [
        { key: 'nextStep', label: '下一步', type: 'primary', icon: null },
      ])
    })

    it('admin sees prevStep and submit in evaluation tab (not submitted)', () => {
      expectActions(getBottomActions(TRACKING, 'admin', false, true, false), [
        { key: 'prevStep', label: '上一步', type: 'default', icon: null },
        { key: 'submit', label: '提交', type: 'primary', icon: null },
      ])
    })

    it('admin sees only prevStep in evaluation tab (already submitted)', () => {
      expectActions(getBottomActions(TRACKING, 'admin', false, true, true), [
        { key: 'prevStep', label: '上一步', type: 'default', icon: null },
      ])
    })

    it('bidAdmin sees nextStep in basic info tab', () => {
      expectActions(getBottomActions(TRACKING, '/bidAdmin', false, false), [
        { key: 'nextStep', label: '下一步', type: 'primary', icon: null },
      ])
    })

    it('bidAdmin sees prevStep and submit in evaluation tab (not submitted)', () => {
      expectActions(getBottomActions(TRACKING, '/bidAdmin', false, true, false), [
        { key: 'prevStep', label: '上一步', type: 'default', icon: null },
        { key: 'submit', label: '提交', type: 'primary', icon: null },
      ])
    })

    it('bidAdmin sees only prevStep in evaluation tab (already submitted)', () => {
      expectActions(getBottomActions(TRACKING, '/bidAdmin', false, true, true), [
        { key: 'prevStep', label: '上一步', type: 'default', icon: null },
      ])
    })

    it('manager sees nextStep in basic info tab', () => {
      expectActions(getBottomActions(TRACKING, 'manager', false, false), [
        { key: 'nextStep', label: '下一步', type: 'primary', icon: null },
      ])
    })

    it('manager sees prevStep and submit in evaluation tab (not submitted)', () => {
      expectActions(getBottomActions(TRACKING, 'manager', false, true, false), [
        { key: 'prevStep', label: '上一步', type: 'default', icon: null },
        { key: 'submit', label: '提交', type: 'primary', icon: null },
      ])
    })

    it('manager sees only prevStep in evaluation tab (already submitted)', () => {
      expectActions(getBottomActions(TRACKING, 'manager', false, true, true), [
        { key: 'prevStep', label: '上一步', type: 'default', icon: null },
      ])
    })

    it('bid_lead sees editBasic, editEvaluation, save, and cancel (editing mode buttons)', () => {
      expectActions(getBottomActions(TRACKING, 'bid-TeamLeader'), [
        ACTIONS.EDIT_BASIC,
        ACTIONS.EDIT_EVALUATION,
        ACTIONS.SAVE,
        ACTIONS.CANCEL,
      ])
    })

    it('sales sees nextStep', () => {
      expectActions(getBottomActions(TRACKING, 'bid-projectLeader'), [
        { key: 'nextStep', label: '下一步', type: 'primary', icon: null },
      ])
    })

    it('bid_specialist sees nothing', () => {
      expect(getBottomActions(TRACKING, 'bid-Team')).toEqual([])
    })
  })

  describe('EVALUATED', () => {
    it('bid_admin sees bid + abandon', () => {
      expectActions(getBottomActions(EVALUATED, '/bidAdmin'), [
        ACTIONS.BID,
        ACTIONS.ABANDON,
      ])
    })

    it('bid_lead sees bid + abandon', () => {
      expectActions(getBottomActions(EVALUATED, 'bid-TeamLeader'), [
        ACTIONS.BID,
        ACTIONS.ABANDON,
      ])
    })

    it('sales sees nothing', () => {
      expect(getBottomActions(EVALUATED, 'bid-projectLeader')).toEqual([])
    })

    it('bid_specialist sees nothing', () => {
      expect(getBottomActions(EVALUATED, 'bid-Team')).toEqual([])
    })
  })

  describe('BIDDING', () => {
    it('bid_admin sees nothing', () => {
      expect(getBottomActions(BIDDING, '/bidAdmin')).toEqual([])
    })
  })

  describe('WON', () => {
    it('bid_admin sees nothing', () => {
      expect(getBottomActions(WON, '/bidAdmin')).toEqual([])
    })
  })

  describe('LOST', () => {
    it('bid_admin sees nothing', () => {
      expect(getBottomActions(LOST, '/bidAdmin')).toEqual([])
    })
  })

  describe('ABANDONED', () => {
    it('bid_admin sees nothing', () => {
      expect(getBottomActions(ABANDONED, '/bidAdmin')).toEqual([])
    })
  })

  describe('edge cases', () => {
    it('returns empty array for unknown role', () => {
      expect(getBottomActions(PENDING, 'auditor')).toEqual([])
    })

    it('returns empty array for unknown status', () => {
      expect(getBottomActions('UNKNOWN_STATUS', '/bidAdmin')).toEqual([])
    })
  })
})

// ===========================================================================
// shouldShowLogsTab
// ===========================================================================
describe('shouldShowLogsTab', () => {
  it('returns true for all source types', () => {
    expect(shouldShowLogsTab('MANUAL_SINGLE')).toBe(true)
    expect(shouldShowLogsTab('人工录入')).toBe(true)
    expect(shouldShowLogsTab('EXTERNAL_PLATFORM')).toBe(true)
    expect(shouldShowLogsTab('第三方平台')).toBe(true)
    expect(shouldShowLogsTab('CRM_OPPORTUNITY')).toBe(true)
    expect(shouldShowLogsTab('CRM创建')).toBe(true)
    expect(shouldShowLogsTab('BULK_IMPORT')).toBe(true)
    expect(shouldShowLogsTab('批量导入')).toBe(true)
    expect(shouldShowLogsTab(null)).toBe(true)
    expect(shouldShowLogsTab(undefined)).toBe(true)
  })
})
