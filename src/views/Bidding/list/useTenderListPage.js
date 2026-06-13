// Input: Vue router, stores, API modules, and browser viewport
// Output: composed state/actions for the bidding list page shell
// Pos: src/views/Bidding/list/ - Bidding list page composition root

import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useBiddingStore } from '@/stores/bidding'
import { useUserStore } from '@/stores/user'
import { tendersApi } from '@/api/modules/tenders'
import { batchTendersApi } from '@/api/modules/tenders/batch.js'
import { ExportType } from '@/api'
import { useExport } from '@/composables/useExport'
import { DEFAULT_SEARCH_FORM } from './constants.js'
import { buildPermissionFlags, isAdminRole, normalizeTenderForExport, resolveUserRole } from './helpers.js'
import {
  matchesTenderStatus,
  TENDER_STATUSES,
} from '../bidding-utils-status.js'
import { useManualTenderCreate } from './useManualTenderCreate.js'
import { useTenderBulkImport } from './useTenderBulkImport.js'
import { useMarketInsight } from './useMarketInsight.js'
import { useTenderBatchActions } from './useTenderBatchActions.js'
import { useTenderDistribution } from './useTenderDistribution.js'
import { useTenderSelection } from './useTenderSelection.js'
import { useTenderSourceConfig } from './useTenderSourceConfig.js'

export function useTenderListPage() {
  const router = useRouter()
  const route = useRoute()
  const biddingStore = useBiddingStore()
  const userStore = useUserStore()
  const searchForm = ref({
    ...DEFAULT_SEARCH_FORM,
    keyword: (route.query.keyword) || DEFAULT_SEARCH_FORM.keyword,
    region: (route.query.region) || DEFAULT_SEARCH_FORM.region,
    status: (route.query.status) || DEFAULT_SEARCH_FORM.status,
    customerType: (route.query.customerType) || DEFAULT_SEARCH_FORM.customerType,
    priority: (route.query.priority) || DEFAULT_SEARCH_FORM.priority,
  })
  const viewMode = ref(route.query.statusTab || 'all')
  const isMobile = ref(false)
  const currentPage = ref(Number(route.query.page) || 1)
  const pageSize = ref(Number(route.query.pageSize) || 20)
  const followedTenders = ref([])
  const showParsingDialog = ref(false)
  const parseProgress = ref(0)
  const parsingTenderId = ref(null)
  let parseTimer = null

  const userRole = computed(() => resolveUserRole(userStore))
  const isAdmin = computed(() => isAdminRole(userRole.value))
  const permissions = computed(() => buildPermissionFlags(userStore.menuPermissions))
  const canManageTenders = computed(() => permissions.value.canManageTenders)
  const canCreateTender = computed(() => permissions.value.canCreateTender)
  const canBulkImport = computed(() => permissions.value.canCreateTender && userRole.value !== 'sales')
  const canDeleteTenders = computed(() => permissions.value.canDeleteTenders)
  const canSyncExternalSource = computed(() => permissions.value.canSyncExternalSource)
  const customerOpportunityCenterEnabled = computed(() => isAdmin.value)
  const showTenderAiEntry = computed(() => true)
  const currentUserId = computed(() => userStore.currentUser?.id)
  const tenders = computed(() => biddingStore.tenders || [])
  const loading = computed(() => biddingStore.loading)

  const filteredTenders = computed(() => {
    if (viewMode.value === 'all') return [...tenders.value]
    return tenders.value.filter((tender) => matchesTenderStatus(tender.status, viewMode.value))
  })
  const filteredRecommendTenders = computed(() => filteredTenders.value.filter((tender) => Number(tender.aiScore || 0) >= 85).slice(0, 3))
  const displayTenders = computed(() => {
    const start = (currentPage.value - 1) * pageSize.value
    return filteredTenders.value.slice(start, start + pageSize.value)
  })

  const statusCounts = computed(() => ({
    all: tenders.value.length,
    pendingAssignment: tenders.value.filter((tender) => matchesTenderStatus(tender.status, TENDER_STATUSES.PENDING_ASSIGNMENT)).length,
    tracking: tenders.value.filter((tender) => matchesTenderStatus(tender.status, TENDER_STATUSES.TRACKING)).length,
    evaluated: tenders.value.filter((tender) => matchesTenderStatus(tender.status, TENDER_STATUSES.EVALUATED)).length,
    bidding: tenders.value.filter((tender) => matchesTenderStatus(tender.status, TENDER_STATUSES.BIDDING)).length,
    won: tenders.value.filter((tender) => matchesTenderStatus(tender.status, TENDER_STATUSES.WON)).length,
    lost: tenders.value.filter((tender) => matchesTenderStatus(tender.status, TENDER_STATUSES.LOST)).length,
    abandoned: tenders.value.filter((tender) => matchesTenderStatus(tender.status, TENDER_STATUSES.ABANDONED)).length,
  }))

  const refreshTenderList = async () => {
    await biddingStore.getTenders({
      keyword: searchForm.value.keyword || undefined,
      region: searchForm.value.region || undefined,
      status: searchForm.value.status || undefined,
      source: searchForm.value.source || undefined,
      projectType: searchForm.value.projectType || undefined,
      projectManagerId: searchForm.value.projectManagerId || undefined,
      creatorId: searchForm.value.creatorId || undefined,
      customerType: searchForm.value.customerType || undefined,
      priority: searchForm.value.priority || undefined,
      registrationDeadlineFrom: searchForm.value.registrationDeadlineFrom || undefined,
      registrationDeadlineTo: searchForm.value.registrationDeadlineTo || undefined,
      bidOpeningTimeFrom: searchForm.value.bidOpeningTimeFrom || undefined,
      bidOpeningTimeTo: searchForm.value.bidOpeningTimeTo || undefined,
      createdAtFrom: searchForm.value.createdAtFrom || undefined,
      createdAtTo: searchForm.value.createdAtTo || undefined,
    })
  }

  const selection = useTenderSelection({ displayTenders })
  const sourceConfig = useTenderSourceConfig({
    refreshTenderList,
    searchForm,
    canSyncExternalSource,
  })
  const manualCreate = useManualTenderCreate({ tendersApi, refreshTenderList, canCreateTender })
  const bulkImport = useTenderBulkImport({ tendersApi, refreshTenderList, canCreateTender })
  const marketInsight = useMarketInsight()
  const batchActions = useTenderBatchActions({
    batchTendersApi,
    tendersApi,
    selectedTenders: selection.selectedTenders,
    followedTenders,
    clearSelection: selection.handleClearSelection,
    refreshTenderList,
    canManageTenders,
    canDeleteTenders,
    router,
  })
  const distribution = useTenderDistribution({
    batchTendersApi,
    selectedTenders: selection.selectedTenders,
    selectSingleTender: selection.selectSingleTender,
    clearSelection: selection.handleClearSelection,
    refreshTenderList,
    showBatchOperationFeedback: batchActions.showBatchOperationFeedback,
    canManageTenders,
  })

  const syncUrlState = () => {
    const query = {
      keyword: searchForm.value.keyword || undefined,
      region: searchForm.value.region || undefined,
      status: searchForm.value.status || undefined,
      customerType: searchForm.value.customerType || undefined,
      priority: searchForm.value.priority || undefined,
      statusTab: viewMode.value !== 'all' ? viewMode.value : undefined,
      page: currentPage.value > 1 ? String(currentPage.value) : undefined,
      pageSize: pageSize.value !== 20 ? String(pageSize.value) : undefined,
    }
    router.replace({ query })
  }

  const checkMobile = () => {
    isMobile.value = typeof window !== 'undefined' && window.innerWidth < 768
  }

  const handleSearch = async () => {
    currentPage.value = 1
    syncUrlState()
    await refreshTenderList()
  }

  const handleReset = async () => {
    searchForm.value = { ...DEFAULT_SEARCH_FORM }
    viewMode.value = 'all'
    currentPage.value = 1
    syncUrlState()
    await refreshTenderList()
  }

  // Debounced watcher: sync filter changes to URL after user stops typing
  let syncTimer = null
  watch(
    [searchForm, viewMode],
    () => {
      clearTimeout(syncTimer)
      syncTimer = setTimeout(syncUrlState, 500)
    },
    { deep: true },
  )

  watch([currentPage, pageSize], ([_newPage, newSize], [_oldPage, oldSize]) => {
    if (oldSize !== undefined && newSize !== oldSize) {
      currentPage.value = 1
    }
    syncUrlState()
  })

  const handleExport = () => {
    const { exportExcel } = useExport()
    // 将filteredTenders数据映射为导出格式
    const exportRows = filteredTenders.value.map((tender, idx) => normalizeTenderForExport(tender, idx + 1))
    exportExcel(ExportType.TENDERS, exportRows, '标讯列表导出成功')
  }

  const handleViewDetail = (id) => router.push(`/bidding/${id}`)

  const handleParticipate = (id) => {
    router.push({ path: '/project/create', query: { tenderId: id } })
  }

  const handleEvaluate = (id) => {
    // Redirect to score analysis page with tenderId
    router.push({ path: '/analytics/score-analysis', query: { tenderId: id, action: 'create' } })
  }

  const handleViewAllRecommend = () => {
    searchForm.value = { ...DEFAULT_SEARCH_FORM }
    viewMode.value = 'all'
  }

  const handleOpenCustomerOpportunityCenter = () => {
    router.push('/bidding/customer-opportunities')
  }

  const openManualAdd = () => {
    if (!canCreateTender.value) return ElMessage.error('当前账号无权人工录入标讯')
    manualCreate.showManualAdd.value = true
  }

  const openSourceConfig = () => {
    if (!canSyncExternalSource.value) return ElMessage.error('当前账号无权配置标讯源')
    sourceConfig.showSourceConfig.value = true
  }

  const handleAIAnalysis = (id) => {
    if (!showTenderAiEntry.value) return
    parsingTenderId.value = id
    parseProgress.value = 0
    showParsingDialog.value = true
    clearInterval(parseTimer)
    parseTimer = setInterval(() => {
      parseProgress.value = Math.min(100, parseProgress.value + 20)
      if (parseProgress.value >= 100) {
        clearInterval(parseTimer)
        showParsingDialog.value = false
        router.push(`/bidding/ai-analysis/${id}`)
      }
    }, 250)
  }

  onMounted(async () => {
    checkMobile()
    window.addEventListener('resize', checkMobile)
    await refreshTenderList()
    if (canSyncExternalSource.value) {
      sourceConfig.loadSavedConfig()
    }
  })

  onUnmounted(() => {
    window.removeEventListener('resize', checkMobile)
    clearInterval(parseTimer)
  })

  return {
    searchForm,
    viewMode,
    isMobile,
    loading,
    currentPage,
    pageSize,
    filteredTenders,
    filteredRecommendTenders,
    displayTenders,
    statusCounts,
    userRole,
    isAdmin,
    canManageTenders,
    canCreateTender,
    canBulkImport,
    canDeleteTenders,
    canSyncExternalSource,
    customerOpportunityCenterEnabled,
    currentUserId,
    showTenderAiEntry,
    showParsingDialog,
    parseProgress,
    selection,
    sourceConfig,
    manualCreate,
    bulkImport,
    marketInsight,
    batchActions,
    distribution,
    handleSearch,
    handleReset,
    handleExport,
    handleViewDetail,
    handleParticipate,
    handleEvaluate,
    handleViewAllRecommend,
    handleOpenCustomerOpportunityCenter,
    openManualAdd,
    openSourceConfig,
    handleAIAnalysis,
  }
}
