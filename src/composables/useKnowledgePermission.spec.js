import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useKnowledgePermission } from './useKnowledgePermission'

// 可变 mock store，测试用例可动态修改 menuPermissions
const mockUserStore = {
  userRole: 'admin',
  currentUser: { role: 'admin' },
  menuPermissions: ['all'],
}

vi.mock('@/stores/user.js', () => ({
  useUserStore: () => mockUserStore,
}))

describe('useKnowledgePermission', () => {
  beforeEach(() => {
    // 重置为 admin 全权限
    mockUserStore.menuPermissions = ['all']
    mockUserStore.userRole = 'admin'
  })

  it('admin with all permissions can manage all knowledge modules', () => {
    const { canManage, canManagePerformance, canManageWarehouse, canManagePersonnel, canAdminAlert } =
      useKnowledgePermission()
    expect(canManage.value).toBe(true)
    expect(canManagePerformance.value).toBe(true)
    expect(canManageWarehouse.value).toBe(true)
    expect(canManagePersonnel.value).toBe(true)
    expect(canAdminAlert.value).toBe(true)
  })

  it('bid-Team with performance.manage / warehouse.manage / personnel.manage can manage all three modules (CO-438 回归)', () => {
    mockUserStore.menuPermissions = [
      'performance.manage',
      'warehouse.manage',
      'personnel.manage',
    ]
    const { canManagePerformance, canManageWarehouse, canManagePersonnel } =
      useKnowledgePermission()
    expect(canManagePerformance.value).toBe(true)
    expect(canManageWarehouse.value).toBe(true)
    expect(canManagePersonnel.value).toBe(true)
  })

  it('bid-administration without knowledge module permissions cannot manage any of the three modules', () => {
    mockUserStore.menuPermissions = ['certificate.manage', 'qualification.view']
    const { canManagePerformance, canManageWarehouse, canManagePersonnel } =
      useKnowledgePermission()
    expect(canManagePerformance.value).toBe(false)
    expect(canManageWarehouse.value).toBe(false)
    expect(canManagePersonnel.value).toBe(false)
  })

  it('user with only performance.manage can manage performance but not warehouse/personnel', () => {
    mockUserStore.menuPermissions = ['performance.manage']
    const { canManagePerformance, canManageWarehouse, canManagePersonnel } =
      useKnowledgePermission()
    expect(canManagePerformance.value).toBe(true)
    expect(canManageWarehouse.value).toBe(false)
    expect(canManagePersonnel.value).toBe(false)
  })

  it('empty menuPermissions denies all (非管理员角色, 无 fallback)', () => {
    // bid-administration 非管理员，fallback 不放行
    mockUserStore.menuPermissions = []
    mockUserStore.userRole = 'bid-administration'
    const { canManage, canManagePerformance, canManageWarehouse, canManagePersonnel } =
      useKnowledgePermission()
    expect(canManage.value).toBe(false)
    expect(canManagePerformance.value).toBe(false)
    expect(canManageWarehouse.value).toBe(false)
    expect(canManagePersonnel.value).toBe(false)
  })

  // CO-438 Rework 回归保护：menuPermissions 为空时回退到角色白名单
  // 原因：某些登录路径（session restore / OSS 用户 fallback）可能返回空 menuPermissions，
  // 此时若仅按权限点判断会导致 bid-TeamLeader//bidAdmin 等管理员角色也看不到按钮（回归）。
  describe('CO-438 Rework: menuPermissions 为空时回退到角色白名单', () => {
    it('menuPermissions 为空 + roleCode=bid-TeamLeader → canManageWarehouse=true（不回归）', () => {
      mockUserStore.menuPermissions = []
      mockUserStore.userRole = 'bid-TeamLeader'
      const { canManageWarehouse } = useKnowledgePermission()
      expect(canManageWarehouse.value).toBe(true)
    })

    it('menuPermissions 为空 + roleCode=/bidAdmin → canManagePersonnel=true（不回归）', () => {
      mockUserStore.menuPermissions = []
      mockUserStore.userRole = '/bidAdmin'
      const { canManagePersonnel } = useKnowledgePermission()
      expect(canManagePersonnel.value).toBe(true)
    })

    it('menuPermissions 为空 + roleCode=admin → canManagePerformance=true（不回归）', () => {
      mockUserStore.menuPermissions = []
      mockUserStore.userRole = 'admin'
      const { canManagePerformance } = useKnowledgePermission()
      expect(canManagePerformance.value).toBe(true)
    })

    it('menuPermissions 正常 + roleCode=bid-Team → canManagePerformance=true（原始修复保留）', () => {
      mockUserStore.menuPermissions = ['performance.manage']
      mockUserStore.userRole = 'bid-Team'
      const { canManagePerformance } = useKnowledgePermission()
      expect(canManagePerformance.value).toBe(true)
    })
  })
})
