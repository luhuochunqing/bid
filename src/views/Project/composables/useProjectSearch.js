import { ref, computed } from 'vue'
import { chinaRegionOptions } from '@/components/common/chinaRegionData.js'

export const statusOptions = [
  { value: 'PENDING_INITIATION', label: '待立项' },
  { value: 'INITIATED', label: '已立项' },
  { value: 'BIDDING', label: '投标中' },
  { value: 'EVALUATING', label: '评标中' },
  { value: 'WON', label: '已中标' },
  { value: 'LOST', label: '未中标' },
  { value: 'FAILED', label: '已流标' },
  { value: 'ABANDONED', label: '弃标' },
]

export const projectTypeOptions = [
  { value: 'OFFICE', label: '办公' },
  { value: 'COMPREHENSIVE', label: '综合' },
  { value: 'COLLECTIVE', label: '集采' },
  { value: 'INDUSTRIAL', label: '工业品' },
  { value: 'OTHER', label: '其他' },
]

export const customerTypeOptions = [
  { value: 'GOVERNMENT', label: '政府机关/事业单位/高校' },
  { value: 'CENTRAL_SOE', label: '央企' },
  { value: 'LOCAL_SOE', label: '地方国企' },
  { value: 'PRIVATE', label: '民企' },
  { value: 'FOREIGN', label: '港澳台及外企' },
  { value: 'OTHER', label: '其他' },
]

export const priorityOptions = [
  { value: 'S', label: 'S级' },
  { value: 'A', label: 'A级' },
  { value: 'B', label: 'B级' },
  { value: 'C', label: 'C级' },
]

export const sourceOptions = [
  { value: '人工录入', label: '人工录入' },
  { value: 'CRM创建', label: 'CRM创建' },
  { value: '第三方平台', label: '第三方平台' },
  { value: '批量导入', label: '批量导入' },
]

export const stageOptions = [
  { value: 'INITIATED', label: '项目立项' },
  { value: 'DRAFTING', label: '标书制作' },
  { value: 'EVALUATING', label: '评标中' },
  { value: 'RESULT_PENDING', label: '结果确认' },
  { value: 'RETROSPECTIVE', label: '项目复盘' },
  { value: 'CLOSED', label: '项目结项' },
]

export function generateBidMonthOptions() {
  const now = new Date()
  const year = now.getFullYear()
  const options = []
  for (let y = year - 1; y <= year + 1; y++) {
    for (let m = 1; m <= 12; m++) {
      const val = `${y}-${String(m).padStart(2, '0')}`
      options.push({ value: val, label: `${y}年${m}月` })
    }
  }
  return options
}

export function useProjectSearch() {
  const searchForm = ref({
    name: '',
    ownerUnit: '',
    projectType: '',
    customerType: '',
    priority: '',
    sourceModule: '',
    bidStatus: '',
    stage: '',
    projectLeaderId: null,
    biddingLeaderId: null,
    leaderDepartment: '',
    region: '',
    biddingPlatform: '',
    bidMonth: '',
    bidOpenTimeRange: null,
    createTimeRange: null,
    shortlistedCountMin: null,
    shortlistedCountMax: null,
    revenueMin: null,
    revenueMax: null,
  })

  const isFiltered = computed(() => {
    const f = searchForm.value
    return !!(
      f.name || f.ownerUnit || f.projectType || f.customerType || f.priority ||
      f.sourceModule || f.bidStatus || f.stage || f.projectLeaderId ||
      f.biddingLeaderId || f.leaderDepartment || f.region || f.biddingPlatform ||
      f.bidMonth ||
      (f.bidOpenTimeRange && f.bidOpenTimeRange.length === 2) ||
      (f.createTimeRange && f.createTimeRange.length === 2) ||
      f.shortlistedCountMin != null || f.shortlistedCountMax != null ||
      f.revenueMin != null || f.revenueMax != null
    )
  })

  const handleSearch = () => true

  const handleReset = () => {
    searchForm.value = {
      name: '',
      ownerUnit: '',
      projectType: '',
      customerType: '',
      priority: '',
      sourceModule: '',
      bidStatus: '',
      stage: '',
      projectLeaderId: null,
      biddingLeaderId: null,
      leaderDepartment: '',
      region: '',
      biddingPlatform: '',
      bidMonth: '',
      bidOpenTimeRange: null,
      createTimeRange: null,
      shortlistedCountMin: null,
      shortlistedCountMax: null,
      revenueMin: null,
      revenueMax: null,
    }
  }

  return {
    searchForm,
    isFiltered,
    statusOptions,
    projectTypeOptions,
    customerTypeOptions,
    priorityOptions,
    sourceOptions,
    stageOptions,
    generateBidMonthOptions,
    handleSearch,
    handleReset,
    chinaRegionOptions,
  }
}
