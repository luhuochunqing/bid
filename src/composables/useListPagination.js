import { ref, computed, watch } from 'vue'

/**
 * 纯前端列表分页 composable。
 *
 * 用法：
 *   const filtered = computed(() => ...)         // 过滤后的列表
 *   const { pagination, pagedData, totalCount, ... } = useListPagination(filtered)
 *
 * @param {import('vue').Ref<Array> | import('vue').ComputedRef<Array>} sourceRef - 数据源（通常是过滤后的列表）
 * @param {Object} [options]
 * @param {number} [options.defaultPageSize=10] - 默认每页条数
 * @param {number[]} [options.pageSizes=[10, 20, 50, 100]] - 可选每页条数（绑定到 el-pagination :page-sizes）
 */
export function useListPagination(sourceRef, options = {}) {
  const defaultPageSize = options.defaultPageSize ?? 10
  const pageSizes = options.pageSizes ?? [10, 20, 50, 100]

  const pagination = ref({ page: 1, pageSize: defaultPageSize })

  const totalCount = computed(() => sourceRef.value.length)

  const pagedData = computed(() => {
    const start = (pagination.value.page - 1) * pagination.value.pageSize
    return sourceRef.value.slice(start, start + pagination.value.pageSize)
  })

  // 数据源长度变化时重置到第一页，避免越界停留在空页
  watch(() => sourceRef.value.length, () => {
    pagination.value.page = 1
  })

  const handleSizeChange = () => {
    pagination.value.page = 1
  }

  const resetPage = () => {
    pagination.value.page = 1
  }

  return {
    pagination,
    pageSizes,
    totalCount,
    pagedData,
    handleSizeChange,
    resetPage,
  }
}
