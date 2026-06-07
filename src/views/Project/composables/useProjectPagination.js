import { ref, computed, watch } from 'vue'

/**
 * Composable for project pagination functionality.
 * @param {ComputedRef<Array>} matchedProjects - Filtered projects from search
 */
export function useProjectPagination(matchedProjects) {
  const pagination = ref({ page: 1, pageSize: 10, total: 0 })

  const filteredProjects = computed(() => {
    const start = (pagination.value.page - 1) * pagination.value.pageSize
    return matchedProjects.value.slice(start, start + pagination.value.pageSize)
  })

  watch(
    () => matchedProjects.value.length,
    (total) => {
      pagination.value.total = total
      const maxPage = Math.max(1, Math.ceil(total / pagination.value.pageSize))
      if (pagination.value.page > maxPage) {
        pagination.value.page = maxPage
      }
    },
    { immediate: true }
  )

  const handleSizeChange = () => {
    pagination.value.page = 1
  }

  const handlePageChange = () => {
    // Page change handled by pagination component
  }

  const resetPage = () => {
    pagination.value.page = 1
  }

  return {
    pagination,
    filteredProjects,
    handleSizeChange,
    handlePageChange,
    resetPage,
  }
}
