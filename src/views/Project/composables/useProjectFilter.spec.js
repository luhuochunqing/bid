import { describe, expect, it, beforeEach } from 'vitest'
import { ref, nextTick } from 'vue'
import { setActivePinia, createPinia } from 'pinia'
import { useProjectFilter } from './useProjectFilter.js'
import { useProjectStore } from '@/stores/project.js'

// 构造一个项目对象，只填筛选相关字段
function makeProject(overrides = {}) {
  return {
    id: 1,
    name: '测试项目',
    projectLeaderId: 100,
    biddingLeaderId: 200, // 主投标负责人 id
    secondaryBiddingLeaderId: null, // 副投标负责人 id
    ...overrides,
  }
}

/**
 * useProjectFilter 是投标项目列表的内存筛选器（前端过滤，不调后端）。
 * 这些测试守住"投标负责人筛选只匹配主负责人、不匹配副负责人"的契约，
 * 防止回归到之前主/副 OR 匹配的语义（PR1574 修复后暴露的设计问题）。
 */
describe('useProjectFilter — 投标负责人筛选', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('筛"主投标负责人"命中主=该用户的项目', async () => {
    const store = useProjectStore()
    store.projects = [
      makeProject({ id: 1, biddingLeaderId: 7246, secondaryBiddingLeaderId: null }),
    ]
    const searchForm = ref({ biddingLeaderId: 7246 })
    const { matchedProjects } = useProjectFilter(searchForm)
    await nextTick()
    expect(matchedProjects.value.map((p) => p.id)).toEqual([1])
  })

  it('筛"陈梦瑶"时，主=张莉娜/副=陈梦瑶的项目不再混入（核心回归用例）', async () => {
    const store = useProjectStore()
    store.projects = [
      // project 136：主=张莉娜(7396)、副=陈梦瑶(7246) — 之前会被错误命中
      makeProject({ id: 136, biddingLeaderId: 7396, secondaryBiddingLeaderId: 7246 }),
      // project 114：主=陈梦瑶(7246) — 应命中
      makeProject({ id: 114, biddingLeaderId: 7246, secondaryBiddingLeaderId: null }),
      // project 146：主=陈梦瑶(7246)、副=张莉娜(7396) — 应命中
      makeProject({ id: 146, biddingLeaderId: 7246, secondaryBiddingLeaderId: 7396 }),
    ]
    const searchForm = ref({ biddingLeaderId: 7246 }) // 筛陈梦瑶
    const { matchedProjects } = useProjectFilter(searchForm)
    await nextTick()
    expect(matchedProjects.value.map((p) => p.id).sort()).toEqual([114, 146])
  })

  it('不传 biddingLeaderId 时不做过滤（全部命中）', async () => {
    const store = useProjectStore()
    store.projects = [
      makeProject({ id: 1, biddingLeaderId: 7246 }),
      makeProject({ id: 2, biddingLeaderId: 7396 }),
    ]
    const searchForm = ref({ biddingLeaderId: null })
    const { matchedProjects } = useProjectFilter(searchForm)
    await nextTick()
    expect(matchedProjects.value.map((p) => p.id).sort()).toEqual([1, 2])
  })

  it('项目负责人筛选仍按 projectLeaderId 匹配', async () => {
    const store = useProjectStore()
    store.projects = [
      makeProject({ id: 1, projectLeaderId: 100 }),
      makeProject({ id: 2, projectLeaderId: 200 }),
    ]
    const searchForm = ref({ projectLeaderId: 100 })
    const { matchedProjects } = useProjectFilter(searchForm)
    await nextTick()
    expect(matchedProjects.value.map((p) => p.id)).toEqual([1])
  })
})
