// Input: template-library remote API helper and local filter helpers
// Output: collection/query state for template workspace with backend-owned official filtering
// Pos: src/views/Knowledge/components/template/ - template page composable layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  buildFilterSummaryItems,
  buildRemoteTemplateFilters,
  createTemplateFilters,
  filterTemplateCollection,
  hasActiveLocalFilters,
  hasActiveOfficialFilters,
  paginateTemplates
} from './templateLibraryHelpers.js'
import { fetchTemplateList } from './templateLibraryRemote.js'

export function useTemplateLibraryCollection() {
  const activeCategory = ref('all')
  const filters = reactive(createTemplateFilters())
  const pagination = reactive({ page: 1, pageSize: 10 })
  const templates = ref([])
  const loading = ref(false)
  const featurePlaceholder = ref(null)

  const allTags = computed(() => Array.from(new Set(templates.value.flatMap((item) => item.tags || []))).sort())
  const filteredTemplates = computed(() => filterTemplateCollection(templates.value, filters))
  const pagedTemplates = computed(() => paginateTemplates(filteredTemplates.value, pagination.page, pagination.pageSize))
  const hasOfficialFilters = computed(() => hasActiveOfficialFilters(activeCategory.value, filters))
  const hasLocalFilters = computed(() => hasActiveLocalFilters(filters))
  const filterSummaryItems = computed(() => buildFilterSummaryItems(activeCategory.value, filters))

  const workspaceEmptyState = computed(() => {
    if (loading.value || featurePlaceholder.value) return null
    if (templates.value.length === 0 && !hasOfficialFilters.value && !hasLocalFilters.value) {
      return {
        type: 'initial',
        title: '模板资产工作台已就绪',
        description: '先通过产品类型、行业、文档类型建立模板资产视图，再补充模板内容。',
        hint: '当前库内还没有真实模板，点击“新建模板”开始沉淀第一份标准模板。'
      }
    }
    if (filteredTemplates.value.length === 0) {
      return {
        type: 'filtered',
        title: '没有匹配当前条件的模板',
        description: '已按当前筛选条件完成检索，但没有找到可用模板。',
        hint: filterSummaryItems.value.length > 0
          ? '建议清空部分条件，或切换到更宽的产品类型 / 行业 / 文档类型范围后重试。'
          : '可以调整标签筛选或排序方式，再次查看结果。'
      }
    }
    return null
  })

  watch(filteredTemplates, (items) => {
    const maxPage = Math.max(1, Math.ceil(items.length / pagination.pageSize))
    if (pagination.page > maxPage) pagination.page = maxPage
  })

  function buildQuery() {
    return buildRemoteTemplateFilters(activeCategory.value, filters)
  }

  async function loadTemplates(query = buildQuery()) {
    loading.value = true
    try {
      const result = await fetchTemplateList(query)
      templates.value = result.templates
      featurePlaceholder.value = result.featurePlaceholder
      if (result.errorMessage) ElMessage.error(result.errorMessage)
    } finally {
      loading.value = false
    }
  }

  async function handleSearch() {
    pagination.page = 1
    await loadTemplates()
  }

  async function handleReset() {
    activeCategory.value = 'all'
    Object.assign(filters, createTemplateFilters())
    pagination.page = 1
    await loadTemplates(buildRemoteTemplateFilters('all', filters))
  }

  async function handleCategoryChange() {
    pagination.page = 1
    await loadTemplates()
  }

  return {
    activeCategory,
    filters,
    pagination,
    templates,
    loading,
    featurePlaceholder,
    allTags,
    filteredTemplates,
    pagedTemplates,
    filterSummaryItems,
    workspaceEmptyState,
    loadTemplates,
    handleSearch,
    handleReset,
    handleCategoryChange
  }
}
