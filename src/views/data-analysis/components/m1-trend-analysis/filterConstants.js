export const PROJECT_STATUS_OPTIONS = [
  { label: '待立项', value: 'PENDING_INITIATION' },
  { label: '已立项', value: 'INITIATED' },
  { label: '投标中', value: 'BIDDING' },
  { label: '评标中', value: 'EVALUATING' },
  { label: '已中标', value: 'WON' },
  { label: '未中标', value: 'LOST' },
  { label: '已流标', value: 'FAILED' },
  { label: '已放弃', value: 'ABANDONED' }
]

export const X_AXIS_OPTIONS = [
  { label: '时间', value: 'time' },
  { label: '部门', value: 'department' },
  { label: '区域', value: 'region' },
  { label: '客户类型', value: 'customerType' },
  { label: '项目类型', value: 'projectType' },
  { label: '项目状态', value: 'projectStatus' },
  { label: '招标主体', value: 'tenderSubject' },
  { label: '竞品公司', value: 'competitor' }
]