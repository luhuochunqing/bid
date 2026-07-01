import { describe, expect, it, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '@/stores/user'
import { useCaBorrowEligibility, isCaBorrowableByStatus, isCaReturnableByStatus } from './useCaBorrowEligibility'

vi.mock('@/stores/user', () => ({
  useUserStore: vi.fn()
}))

const baseCa = {
  id: 1,
  borrowStatus: 'IN_STOCK',
  caType: 'ENTITY_CA',
  status: 'ACTIVE',
  custodianId: 'custodian-001'
}

function setup(role, userId) {
  setActivePinia(createPinia())
  vi.mocked(useUserStore).mockReturnValue({
    userRole: role,
    currentUser: { id: userId }
  })
  return useCaBorrowEligibility()
}

describe('纯函数：isCaBorrowableByStatus', () => {
  it('在库 + 实体CA + 有效状态 → 可借用', () => {
    expect(isCaBorrowableByStatus(baseCa)).toBe(true)
  })

  it('已借出状态 → 不可借用', () => {
    expect(isCaBorrowableByStatus({ ...baseCa, borrowStatus: 'BORROWED' })).toBe(false)
  })

  it('电子CA → 不可借用', () => {
    expect(isCaBorrowableByStatus({ ...baseCa, caType: 'ELECTRONIC_CA' })).toBe(false)
  })

  it('已过期 → 不可借用', () => {
    expect(isCaBorrowableByStatus({ ...baseCa, status: 'EXPIRED' })).toBe(false)
  })

  it('已停用 → 不可借用', () => {
    expect(isCaBorrowableByStatus({ ...baseCa, status: 'INACTIVE' })).toBe(false)
  })

  it('null/undefined → 不可借用', () => {
    expect(isCaBorrowableByStatus(null)).toBe(false)
    expect(isCaBorrowableByStatus(undefined)).toBe(false)
  })
})

describe('纯函数：isCaReturnableByStatus', () => {
  it('已借出 → 可归还', () => {
    expect(isCaReturnableByStatus({ ...baseCa, borrowStatus: 'BORROWED' })).toBe(true)
  })

  it('在库 → 不可归还', () => {
    expect(isCaReturnableByStatus(baseCa)).toBe(false)
  })

  it('null/undefined → 不可归还', () => {
    expect(isCaReturnableByStatus(null)).toBe(false)
    expect(isCaReturnableByStatus(undefined)).toBe(false)
  })
})

describe('useCaBorrowEligibility', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('canBorrowByRole (管理员)', () => {
    it('管理员可借用任意 CA', () => {
      const { canBorrowByRole } = setup('admin', 'user-001')
      expect(canBorrowByRole(baseCa)).toBe(true)
    })

    it('投标管理员可借用任意 CA', () => {
      const { canBorrowByRole } = setup('/bidAdmin', 'user-001')
      expect(canBorrowByRole(baseCa)).toBe(true)
    })
  })

  describe('canBorrowByRole (投标专员)', () => {
    it('投标专员不可借用自己保管的 CA', () => {
      const { canBorrowByRole } = setup('bid-Team', 'custodian-001')
      expect(canBorrowByRole(baseCa)).toBe(false)
    })

    it('投标专员可借用他人保管的 CA', () => {
      const { canBorrowByRole } = setup('bid-Team', 'user-002')
      expect(canBorrowByRole(baseCa)).toBe(true)
    })

    it('投标专员可借用无保管员的 CA', () => {
      const { canBorrowByRole } = setup('bid-Team', 'user-002')
      expect(canBorrowByRole({ ...baseCa, custodianId: null })).toBe(true)
    })
  })

  describe('canBorrow (综合判断)', () => {
    it('状态满足 + 角色满足 → 可借用', () => {
      const { canBorrow } = setup('admin', 'user-001')
      expect(canBorrow(baseCa)).toBe(true)
    })

    it('状态不满足 → 不可借用（即使角色满足）', () => {
      const { canBorrow } = setup('admin', 'user-001')
      expect(canBorrow({ ...baseCa, borrowStatus: 'BORROWED' })).toBe(false)
    })

    it('角色不满足 → 不可借用（即使状态满足）', () => {
      const { canBorrow } = setup('bid-Team', 'custodian-001')
      expect(canBorrow(baseCa)).toBe(false)
    })
  })

  describe('canManage', () => {
    it('管理员可管理任意 CA', () => {
      const { canManage } = setup('admin', 'user-001')
      expect(canManage(baseCa)).toBe(true)
    })

    it('投标专员可管理自己保管的 CA', () => {
      const { canManage } = setup('bid-Team', 'custodian-001')
      expect(canManage(baseCa)).toBe(true)
    })

    it('投标专员不可管理他人保管的 CA', () => {
      const { canManage } = setup('bid-Team', 'user-002')
      expect(canManage(baseCa)).toBe(false)
    })
  })

  describe('canReturn', () => {
    it('已借出 + 可管理 → 可归还', () => {
      const { canReturn } = setup('admin', 'user-001')
      expect(canReturn({ ...baseCa, borrowStatus: 'BORROWED' })).toBe(true)
    })

    it('在库状态 → 不可归还', () => {
      const { canReturn } = setup('admin', 'user-001')
      expect(canReturn(baseCa)).toBe(false)
    })

    it('已借出但不可管理 → 不可归还', () => {
      const { canReturn } = setup('bid-Team', 'user-002')
      expect(canReturn({ ...baseCa, borrowStatus: 'BORROWED' })).toBe(false)
    })
  })
})
