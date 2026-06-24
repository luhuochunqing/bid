import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { flushPromises } from '@vue/test-utils'

vi.mock('@/api/modules/users.js', () => ({
  usersApi: {
    search: vi.fn(),
    getAssignableCandidates: vi.fn(),
  },
}))

import { usersApi } from '@/api/modules/users.js'
import { useUserPicker } from '@/composables/useUserPicker.js'

const mockUser = {
  id: 1,
  name: '张三',
  employeeNumber: '20260509',
  roleCode: 'bid_admin',
  roleName: '投标管理员',
  deptCode: 'BID',
  deptName: '投标管理部',
}

describe('useUserPicker', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('mode=search calls usersApi.search and returns options', async () => {
    usersApi.search.mockResolvedValue([mockUser])
    const { options, search } = useUserPicker({ mode: 'search' })

    search('张')
    vi.advanceTimersByTime(300)
    await flushPromises()

    // P1.3: search 现在接收 signal 参数用于竞态保护
    expect(usersApi.search).toHaveBeenCalledWith('张', 10, expect.objectContaining({ signal: expect.any(AbortSignal) }))
    expect(options.value).toHaveLength(1)
    expect(options.value[0]).toMatchObject({ id: 1, name: '张三' })
  })

  it('mode=candidates calls usersApi.getAssignableCandidates and returns options', async () => {
    usersApi.getAssignableCandidates.mockResolvedValue([mockUser])
    const { options, loadCandidates } = useUserPicker({
      mode: 'candidates',
      context: 'task',
    })

    await loadCandidates()

    // P1.3: getAssignableCandidates 现在接收 signal 参数
    expect(usersApi.getAssignableCandidates).toHaveBeenCalledWith({
      context: 'task',
      deptCode: undefined,
      roleCode: undefined,
    }, expect.objectContaining({ signal: expect.any(AbortSignal) }))
    expect(options.value).toHaveLength(1)
    expect(options.value[0]).toMatchObject({ id: 1, name: '张三' })
  })

  it('debounces search by 300ms', async () => {
    usersApi.search.mockResolvedValue([])
    const { search } = useUserPicker({ mode: 'search' })

    search('张')
    expect(usersApi.search).not.toHaveBeenCalled()

    vi.advanceTimersByTime(299)
    expect(usersApi.search).not.toHaveBeenCalled()

    vi.advanceTimersByTime(1)
    expect(usersApi.search).toHaveBeenCalledWith('张', 10, expect.objectContaining({ signal: expect.any(AbortSignal) }))

    await flushPromises()
  })

  it('sets loading to true during request, then false when done', async () => {
    let resolveSearch
    usersApi.search.mockReturnValue(
      new Promise((resolve) => {
        resolveSearch = resolve
      })
    )

    const { loading, search } = useUserPicker({ mode: 'search' })

    search('张')
    vi.advanceTimersByTime(300)

    expect(loading.value).toBe(true)

    resolveSearch([mockUser])
    await flushPromises()

    expect(loading.value).toBe(false)
  })

  it('returns empty array when search yields no results', async () => {
    usersApi.search.mockResolvedValue([])
    const { options, search } = useUserPicker({ mode: 'search' })

    search('不存在的用户')
    vi.advanceTimersByTime(300)
    await flushPromises()

    expect(options.value).toEqual([])
  })

  it('formatLabel returns "姓名（部门·角色）" format', () => {
    const { formatLabel } = useUserPicker({ mode: 'search' })

    expect(formatLabel(mockUser)).toBe('张三（投标管理部·投标管理员）')
  })

  it('formatLabel omits empty dept or role parts', () => {
    const { formatLabel } = useUserPicker({ mode: 'search' })

    expect(formatLabel({ name: '李四', roleName: '销售' })).toBe('李四（销售）')
    expect(formatLabel({ name: '王五', deptName: '技术部' })).toBe('王五（技术部）')
    expect(formatLabel({ name: '赵六' })).toBe('赵六')
  })
})
