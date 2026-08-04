// 业绩附件类型常量（与后端 PerformanceAttachmentTypeLabels 保持一致）
export const PERF_ATTACHMENT_TYPES = [
  { value: 'CONTRACT_AGREEMENT', label: '合同协议', required: true },
  { value: 'MALL_SCREENSHOT', label: '商城截图' },
  { value: 'SOE_DIRECTORY', label: '央企名录' },
  { value: 'RELATIONSHIP_PROOF', label: '关系证明' },
  { value: 'CATEGORY_PAGE', label: '品类页' },
  { value: 'BID_NOTICE', label: '中标通知书' },
  { value: 'OTHER', label: '其他附件' },
]

export const PERF_ALL_VALUES = PERF_ATTACHMENT_TYPES.map(t => t.value)