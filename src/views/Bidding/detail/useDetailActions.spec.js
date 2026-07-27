import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

const deleteApi = vi.fn()

vi.mock('@/api', () => ({
  tendersApi: { delete: deleteApi, participate: vi.fn(), abandon: vi.fn() },
}))

const elMessage = {
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
}

const elMessageBox = {
  confirm: vi.fn(),
  prompt: vi.fn(),
}

vi.mock('element-plus', () => ({
  ElMessage: elMessage,
  ElMessageBox: elMessageBox,
}))

const { useDetailActions } = await import('./useDetailActions.js')

// ---------------------------------------------------------------------------
// Harness
// ---------------------------------------------------------------------------

function createHarness(tenderRef, roleRef, loadDetailFn, handlers = {}, evaluationTabActive = false, evaluationSubmitted = false, currentUserId, creatorId) {
  return defineComponent({
    template: '<div />',
    setup() {
      return useDetailActions(tenderRef, roleRef, loadDetailFn, handlers, evaluationTabActive, evaluationSubmitted, currentUserId, creatorId)
    },
  })
}

// ===========================================================================
// Tests
// ===========================================================================

describe('useDetailActions', () => {
  let tenderRef
  let roleRef
  let loadDetailFn

  beforeEach(() => {
    vi.clearAllMocks()
    tenderRef = ref(null)
    roleRef = ref(null)
    loadDetailFn = vi.fn()
  })

  // ---------------------------------------------------------------------------
  // Empty / null state
  // ---------------------------------------------------------------------------

  it('tender 或 role 为 null 时 headerActions / bottomActions 返回空数组', () => {
    const wrapper = mount(createHarness(tenderRef, roleRef, loadDetailFn))
    expect(wrapper.vm.headerActions).toEqual([])
    expect(wrapper.vm.bottomActions).toEqual([])
  })

  // ---------------------------------------------------------------------------
  // headerActions computed
  // ---------------------------------------------------------------------------

  it('headerActions computed 返回正确的按钮列表 (admin + originalUrl)', async () => {
    tenderRef.value = { status: 'PENDING_ASSIGNMENT', originalUrl: 'https://example.com' }
    roleRef.value = 'admin'
    const wrapper = mount(createHarness(tenderRef, roleRef, loadDetailFn))
    await flushPromises()
    expect(wrapper.vm.headerActions).toHaveLength(3)
    expect(wrapper.vm.headerActions[0].key).toBe('assign')
    expect(wrapper.vm.headerActions[1].key).toBe('delete')
    expect(wrapper.vm.headerActions[2].key).toBe('viewAnnouncement')
  })

  it('sales on PENDING_ASSIGNMENT sees only viewAnnouncement (no assign/delete)', async () => {
    tenderRef.value = { status: 'PENDING_ASSIGNMENT', originalUrl: 'https://example.com' }
    roleRef.value = 'bid-projectLeader'
    const wrapper = mount(createHarness(tenderRef, roleRef, loadDetailFn))
    await flushPromises()
    expect(wrapper.vm.headerActions).toHaveLength(1)
    expect(wrapper.vm.headerActions[0].key).toBe('viewAnnouncement')
  })

  // ---------------------------------------------------------------------------
  // bottomActions computed
  // ---------------------------------------------------------------------------

  it('bottomActions computed 返回正确的按钮列表 (bid_admin on PENDING)', async () => {
    tenderRef.value = { status: 'PENDING_ASSIGNMENT' }
    roleRef.value = '/bidAdmin'
    const wrapper = mount(createHarness(tenderRef, roleRef, loadDetailFn))
    await flushPromises()
    expect(wrapper.vm.bottomActions).toHaveLength(1)
    expect(wrapper.vm.bottomActions[0].key).toBe('edit')
  })

  // ---------------------------------------------------------------------------
  // handleAction — dispatches ALL actions via injected handlers
  // ---------------------------------------------------------------------------

  it('handleAction(\'viewAnnouncement\') calls injected viewAnnouncement handler', () => {
    const viewAnnouncement = vi.fn()
    const wrapper = mount(createHarness(tenderRef, roleRef, loadDetailFn, { viewAnnouncement }))
    wrapper.vm.handleAction('viewAnnouncement')
    expect(viewAnnouncement).toHaveBeenCalledOnce()
  })

  it('handleAction(\'bid\') calls injected bid handler', () => {
    const bid = vi.fn()
    const wrapper = mount(createHarness(tenderRef, roleRef, loadDetailFn, { bid }))
    wrapper.vm.handleAction('bid')
    expect(bid).toHaveBeenCalledOnce()
  })

  it('handleAction(\'assign\') calls injected assign handler', () => {
    const assign = vi.fn()
    const wrapper = mount(createHarness(tenderRef, roleRef, loadDetailFn, { assign }))
    wrapper.vm.handleAction('assign')
    expect(assign).toHaveBeenCalledOnce()
  })

  it('handleAction(\'transfer\') calls injected transfer handler', () => {
    const transfer = vi.fn()
    const wrapper = mount(createHarness(tenderRef, roleRef, loadDetailFn, { transfer }))
    wrapper.vm.handleAction('transfer')
    expect(transfer).toHaveBeenCalledOnce()
  })

  it('handleAction(\'edit\') / \'editBasic\' calls injected edit handler', () => {
    const edit = vi.fn()
    const wrapper = mount(createHarness(tenderRef, roleRef, loadDetailFn, { edit }))
    wrapper.vm.handleAction('edit')
    expect(edit).toHaveBeenCalledOnce()
    wrapper.vm.handleAction('editBasic')
    expect(edit).toHaveBeenCalledTimes(2)
  })

  it('handleAction(\'editEvaluation\') calls injected editEvaluation handler', () => {
    const editEvaluation = vi.fn()
    const wrapper = mount(createHarness(tenderRef, roleRef, loadDetailFn, { editEvaluation }))
    wrapper.vm.handleAction('editEvaluation')
    expect(editEvaluation).toHaveBeenCalledOnce()
  })

  it('handleAction(\'save\') calls injected save handler', () => {
    const save = vi.fn()
    const wrapper = mount(createHarness(tenderRef, roleRef, loadDetailFn, { save }))
    wrapper.vm.handleAction('save')
    expect(save).toHaveBeenCalledOnce()
  })

  it('handleAction(\'cancel\') calls injected cancel handler', () => {
    const cancel = vi.fn()
    const wrapper = mount(createHarness(tenderRef, roleRef, loadDetailFn, { cancel }))
    wrapper.vm.handleAction('cancel')
    expect(cancel).toHaveBeenCalledOnce()
  })

  it('handleAction(\'viewProject\') calls injected viewProject handler', () => {
    const viewProject = vi.fn()
    const wrapper = mount(createHarness(tenderRef, roleRef, loadDetailFn, { viewProject }))
    wrapper.vm.handleAction('viewProject')
    expect(viewProject).toHaveBeenCalledOnce()
  })

  it('handleAction 对未注入 handler 的操作静默忽略', () => {
    const wrapper = mount(createHarness(tenderRef, roleRef, loadDetailFn))
    expect(() => wrapper.vm.handleAction('assign')).not.toThrow()
    expect(() => wrapper.vm.handleAction('nonexistent')).not.toThrow()
  })

  // ---------------------------------------------------------------------------
  // handleAction('delete') – confirm dialog then API call + afterDelete
  // ---------------------------------------------------------------------------

  it('handleAction(\'delete\') 显示确认弹窗后调用删除 API 并触发 afterDelete', async () => {
    elMessageBox.confirm.mockResolvedValue()
    deleteApi.mockResolvedValue({ success: true })
    tenderRef.value = { id: '9001', status: 'PENDING_ASSIGNMENT' }
    roleRef.value = 'admin'
    const afterDelete = vi.fn()
    const wrapper = mount(createHarness(tenderRef, roleRef, loadDetailFn, { afterDelete }))

    wrapper.vm.handleAction('delete')
    await flushPromises()

    expect(elMessageBox.confirm).toHaveBeenCalled()
    expect(deleteApi).toHaveBeenCalledWith('9001')
    expect(elMessage.success).toHaveBeenCalledWith('删除成功')
    expect(afterDelete).toHaveBeenCalled()
  })

  // ---------------------------------------------------------------------------
  // BUI-1.1 回归：currentUserId / creatorId Ref 解包（PR !2201 修复点）
  // ---------------------------------------------------------------------------

  describe('BUI-1.1 currentUserId/creatorId Ref 解包', () => {
    /**
     * !2201 修复前：currentUserId / creatorId 可能是父组件 computed Ref，
     * 纯核心 actionMatrix 内的 === 比较命中 Ref 对象引用而非 .value，
     * 导致 sales 创建的标讯自己访问时永远显示 "无按钮"。
     *
     * 修复：useDetailActions 新增 resolveIdValue 解包 Ref 再传给 actionMatrix。
     */

    it('sales 持有 Ref 形式的 currentUserId 与 creatorId（同值）时能看到 edit + delete', () => {
      tenderRef.value = { status: 'PENDING_ASSIGNMENT', originalUrl: 'https://example.com' }
      roleRef.value = 'bid-projectLeader'
      const currentUserIdRef = ref(42)
      const creatorIdRef = ref(42)
      const wrapper = mount(
        createHarness(tenderRef, roleRef, loadDetailFn, {}, false, false, currentUserIdRef, creatorIdRef)
      )
      // 头部应出现 edit + delete（销售/专员为创建人时）
      const keys = wrapper.vm.headerActions.map((a) => a.key)
      expect(keys).toContain('edit')
      expect(keys).toContain('delete')
    })

    it('sales 持有 Ref 形式的 currentUserId（≠ creatorId）时不应看到 edit/delete', () => {
      tenderRef.value = { status: 'PENDING_ASSIGNMENT', originalUrl: 'https://example.com' }
      roleRef.value = 'bid-projectLeader'
      const currentUserIdRef = ref(42)
      const creatorIdRef = ref(99)  // 不同值
      const wrapper = mount(
        createHarness(tenderRef, roleRef, loadDetailFn, {}, false, false, currentUserIdRef, creatorIdRef)
      )
      // 非创建人不能看到 edit/delete；但 originalUrl=true 仍会追加 viewAnnouncement
      const keys = wrapper.vm.headerActions.map((a) => a.key)
      expect(keys).not.toContain('edit')
      expect(keys).not.toContain('delete')
    })

    it('bid_specialist 持有 Ref 形式的 currentUserId/creatorId（同值）时能看到 edit + delete', () => {
      tenderRef.value = { status: 'PENDING_ASSIGNMENT' }
      roleRef.value = 'bid-Team'
      const currentUserIdRef = ref(7)
      const creatorIdRef = ref(7)
      const wrapper = mount(
        createHarness(tenderRef, roleRef, loadDetailFn, {}, false, false, currentUserIdRef, creatorIdRef)
      )
      const keys = wrapper.vm.headerActions.map((a) => a.key)
      expect(keys).toContain('edit')
      expect(keys).toContain('delete')
    })

    it('混合：currentUserId 是 Ref、creatorId 是原始值（同值）时解包正确', () => {
      // 模拟：父组件只把 currentUserId 暴露为 Ref，creatorId 是后端返回的原始数字
      tenderRef.value = { status: 'PENDING_ASSIGNMENT' }
      roleRef.value = 'bid-projectLeader'
      const currentUserIdRef = ref(100)
      const wrapper = mount(
        createHarness(tenderRef, roleRef, loadDetailFn, {}, false, false, currentUserIdRef, 100)
      )
      const keys = wrapper.vm.headerActions.map((a) => a.key)
      expect(keys).toContain('edit')
      expect(keys).toContain('delete')
    })

    it('currentUserId/creatorId 都为 null 时返回空按钮（不抛异常）', () => {
      tenderRef.value = { status: 'PENDING_ASSIGNMENT' }
      roleRef.value = 'bid-projectLeader'
      const wrapper = mount(
        createHarness(tenderRef, roleRef, loadDetailFn, {}, false, false, null, null)
      )
      expect(wrapper.vm.headerActions).toEqual([])
    })

    it('currentUserId 是 Ref(0) / creatorId 是 Ref(0) 时（同值零）能正确识别创建人', () => {
      // 边界：0 是合法的 userId，需要正确解包
      tenderRef.value = { status: 'PENDING_ASSIGNMENT' }
      roleRef.value = 'bid-projectLeader'
      const currentUserIdRef = ref(0)
      const creatorIdRef = ref(0)
      const wrapper = mount(
        createHarness(tenderRef, roleRef, loadDetailFn, {}, false, false, currentUserIdRef, creatorIdRef)
      )
      const keys = wrapper.vm.headerActions.map((a) => a.key)
      expect(keys).toContain('edit')
      expect(keys).toContain('delete')
    })
  })
})
