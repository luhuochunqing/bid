import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'

vi.mock('./list/components/BiddingPageHeader.vue', () => ({
  default: { name: 'BiddingPageHeader', template: '<div class="bidding-page-header-stub" />' },
}))

vi.mock('./list/components/TenderTable.vue', () => ({
  default: { name: 'TenderTable', template: '<div class="tender-table-stub" />' },
}))

vi.mock('./list/useTenderListPage.js', () => ({
  useTenderListPage: () => ({
    searchForm: ref({ keyword: '', region: '', industry: '', status: '', source: '' }),
    viewMode: ref('all'),
    isMobile: ref(false),
    currentPage: ref(1),
    pageSize: ref(10),
    filteredTenders: ref([]),
    filteredRecommendTenders: ref([]),
    displayTenders: ref([]),
    statusCounts: ref({ all: 0, pending: 0, tracking: 0, bidded: 0, abandoned: 0 }),
    canManageTenders: ref(true),
    canCreateTender: ref(true),
    canDeleteTenders: ref(true),
    canSyncExternalSource: ref(true),
    customerOpportunityCenterEnabled: ref(true),
    showTenderAiEntry: ref(true),
    showParsingDialog: ref(false),
    parseProgress: ref(0),
    selection: {
      tableRef: ref(null),
      selectedTenders: ref([]),
      selectAllChecked: ref(false),
      isIndeterminate: ref(false),
      handleSelectAll: vi.fn(),
      handleSelectionChange: vi.fn(),
      handleClearSelection: vi.fn(),
    },
    sourceConfig: {
      fetchingTenders: ref(false),
      sourceConfig: ref({ platforms: [] }),
      lastSyncTime: ref('暂未同步'),
      showSourceConfig: ref(false),
      savingConfig: ref(false),
      testingConnection: ref(false),
      fetchResult: ref({ visible: false }),
      syncExternalTenders: vi.fn(),
      saveSourceConfig: vi.fn(),
      testConnection: vi.fn(),
    },
    manualCreate: {
      showManualAdd: ref(false),
      manualFormRef: ref(null),
      manualForm: ref({}),
      savingManual: ref(false),
      parsingManualDocument: ref(false),
      resetManualForm: vi.fn(),
      handleFileChange: vi.fn(),
      handlePastedTextParse: vi.fn(),
      saveManualTender: vi.fn(),
    },
    bulkImport: {
      showBulkImport: ref(false),
      templateDownloading: ref(false),
      importing: ref(false),
      importResult: ref(null),
      selectedFile: ref(null),
      openBulkImport: vi.fn(),
      closeDialog: vi.fn(),
      resetImport: vi.fn(),
      downloadImportTemplate: vi.fn(),
      handleFileChange: vi.fn(),
      submitBulkImport: vi.fn(),
    },
    marketInsight: {
      showMarketInsight: ref(false),
      activeInsightTab: ref('industry'),
      loadingTrendData: ref(false),
      industryTrends: ref([]),
      potentialOpportunities: ref([]),
      industryInsight: ref(''),
      forecastTips: ref([]),
      refreshTrendData: vi.fn(),
    },
    batchActions: {
      handleBatchClaim: vi.fn(),
      handleBatchFollow: vi.fn(),
      handleSingleClaim: vi.fn(),
      handleUpdateStatus: vi.fn(),
      handleDeleteTender: vi.fn(),
    },
    distribution: {
      showRecordDialog: ref(false),
      showDistributeDialog: ref(false),
      showAssignDialog: ref(false),
      candidates: ref([]),
      distributionPreview: ref([]),
      distributeForm: ref({}),
      activeTender: ref({ id: null, title: '' }),
      distributeLoading: ref(false),
      assignLoading: ref(false),
      loadingCandidates: ref(false),
      distributeRecords: ref([]),
      openDistributeDialog: vi.fn(),
      openSingleDistribute: vi.fn(),
      openAssignDialog: vi.fn(),
      resetDistributeForm: vi.fn(),
      resetAssignForm: vi.fn(),
      handleDistribute: vi.fn(),
      handleAssign: vi.fn(),
    },
    handleSearch: vi.fn(),
    handleReset: vi.fn(),
    handleExport: vi.fn(),
    handleViewDetail: vi.fn(),
    handleParticipate: vi.fn(),
    handleViewAllRecommend: vi.fn(),
    handleOpenCustomerOpportunityCenter: vi.fn(),
    openManualAdd: vi.fn(),
    openSourceConfig: vi.fn(),
    handleAIAnalysis: vi.fn(),
  }),
}))

describe('List.vue (标讯中心)', () => {
  it('renders the composed bidding list page shell', async () => {
    const List = (await import('./List.vue')).default
    const wrapper = mount(List, {
      global: {
        stubs: {
          AiParsingDialog: true,
          AiRecommendSection: true,
          AssignDialog: true,
          BulkImportDialog: true,
          DistributeDialog: true,
          FetchResultDialog: true,
          ManualTenderDialog: true,
          MarketInsightDialog: true,
          RecordsDialog: true,
          SourceConfigDialog: true,
          SourceStatusCard: true,
          TenderBatchActionBar: true,
          TenderMobileCards: true,
          TenderSearchCard: true,
          ElButton: { template: '<button><slot /></button>' },
          ElCard: { template: '<div><slot name="header" /><slot /></div>' },
          ElIcon: { template: '<span><slot /></span>' },
          ElPagination: { template: '<nav />' },
          ElRadioButton: { template: '<button><slot /></button>' },
          ElRadioGroup: { template: '<div><slot /></div>' },
          'el-button': { template: '<button><slot /></button>' },
          'el-card': { template: '<div><slot name="header" /><slot /></div>' },
          'el-icon': { template: '<span><slot /></span>' },
          'el-pagination': { template: '<nav />' },
          'el-radio-button': { template: '<button><slot /></button>' },
          'el-radio-group': { template: '<div><slot /></div>' },
        },
      },
    })
    wrapper.vm.$forceUpdate()
    await nextTick()

    expect(wrapper.find('.bidding-page-header-stub').exists()).toBe(true)
    expect(
      wrapper.find('.tender-table-stub').exists() || wrapper.find('tender-mobile-cards-stub').exists(),
    ).toBe(true)
  }, 30000)
})
