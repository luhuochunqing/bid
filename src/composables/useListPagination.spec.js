// Input: src/composables/useListPagination.js — 纯前端列表分页 composable
// Output: coverage for pagination slice, total, reset behavior, size change
// Pos: src/composables/ — composable unit tests

import { describe, it, expect } from 'vitest'
import { ref, computed, nextTick } from 'vue'
import { useListPagination } from './useListPagination'

describe('useListPagination', () => {
  it('returns total count from source ref', () => {
    const source = ref([1, 2, 3, 4, 5])
    const { totalCount, pagination } = useListPagination(source)
    expect(totalCount.value).toBe(5)
    expect(pagination.value.page).toBe(1)
    expect(pagination.value.pageSize).toBe(10)
  })

  it('slices source by current page and page size', () => {
    const source = ref(Array.from({ length: 25 }, (_, i) => i))
    const { pagedData, pagination } = useListPagination(source)
    expect(pagedData.value).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9])

    pagination.value.page = 2
    expect(pagedData.value).toEqual([10, 11, 12, 13, 14, 15, 16, 17, 18, 19])

    pagination.value.pageSize = 5
    pagination.value.page = 1
    expect(pagedData.value).toEqual([0, 1, 2, 3, 4])
  })

  it('resets to first page when source length changes', async () => {
    const source = ref([1, 2, 3])
    const { pagedData, pagination } = useListPagination(source, { defaultPageSize: 2 })
    pagination.value.page = 2
    expect(pagedData.value).toEqual([3])

    // 模拟重新加载后数据条数变化
    source.value = [10, 20, 30, 40, 50]
    await nextTick()
    expect(pagination.value.page).toBe(1)
    expect(pagedData.value).toEqual([10, 20])
  })

  it('handleSizeChange resets to first page', () => {
    const source = ref([1, 2, 3, 4, 5])
    const { handleSizeChange, pagination } = useListPagination(source, { defaultPageSize: 2 })
    pagination.value.page = 3
    handleSizeChange()
    expect(pagination.value.page).toBe(1)
  })

  it('resetPage sets page to 1', () => {
    const source = ref([1, 2, 3])
    const { resetPage, pagination } = useListPagination(source)
    pagination.value.page = 5
    resetPage()
    expect(pagination.value.page).toBe(1)
  })

  it('accepts custom page sizes and default page size', () => {
    const source = ref([1, 2, 3])
    const { pagination, pageSizes } = useListPagination(source, {
      defaultPageSize: 20,
      pageSizes: [5, 20, 50]
    })
    expect(pagination.value.pageSize).toBe(20)
    expect(pageSizes).toEqual([5, 20, 50])
  })

  it('works with ComputedRef source', () => {
    const base = ref([1, 2, 3, 4, 5, 6])
    const even = computed(() => base.value.filter(n => n % 2 === 0))
    const { pagedData, totalCount } = useListPagination(even, { defaultPageSize: 2 })
    expect(totalCount.value).toBe(3)
    expect(pagedData.value).toEqual([2, 4])
  })

  it('returns empty pagedData when source is empty', () => {
    const source = ref([])
    const { pagedData, totalCount } = useListPagination(source)
    expect(totalCount.value).toBe(0)
    expect(pagedData.value).toEqual([])
  })

  it('does not crash when page exceeds available pages', () => {
    const source = ref([1, 2, 3])
    const { pagedData, pagination } = useListPagination(source, { defaultPageSize: 2 })
    // 仅 2 页（每页 2 条），手动跳到第 5 页：slice 会返回空数组（不抛错）
    pagination.value.page = 5
    expect(pagedData.value).toEqual([])
  })
})
