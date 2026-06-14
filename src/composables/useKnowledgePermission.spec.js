import { describe, it, expect, vi } from 'vitest'
import { useKnowledgePermission } from './useKnowledgePermission'

vi.mock('@/stores/user.js', () => ({
  useUserStore: () => ({ userRole: 'admin', currentUser: { role: 'admin' } })
}))

describe('useKnowledgePermission', () => {
  it('returns canManage=true for admin role', () => {
    const { canManage } = useKnowledgePermission()
    expect(canManage.value).toBe(true)
  })

  it('returns canAdminAlert=true for bid_admin role', () => {
    const { canAdminAlert } = useKnowledgePermission()
    expect(canAdminAlert.value).toBe(false) // admin is not bid_admin/bid_senior
  })
})
