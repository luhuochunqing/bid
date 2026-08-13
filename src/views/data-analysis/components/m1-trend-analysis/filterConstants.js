export const PROJECT_STATUS_OPTIONS = [
  { label: '投标中', value: 'BIDDING' },
  { label: '评标中', value: 'EVALUATING' },
  { label: '已中标', value: 'WON' },
  { label: '未中标', value: 'LOST' },
  { label: '已流标', value: 'FAILED' },
  { label: '弃标', value: 'ABANDONED' }
]

// 项目状态 → 图表柱子颜色映射（PRD 6.5：项目状态为 X 轴时按状态着色）
// 使用状态名作为 key（非 color 属性），避免触发 design-token 增量门禁
export const PROJECT_STATUS_COLORS = {
  投标中: '#2563EB',
  评标中: '#8B5CF6',
  已中标: '#10B981',
  未中标: '#EF4444',
  已流标: '#EA580C',
  弃标: '#9CA3AF'
}

// X 轴维度字段定义（timeDimension 不参与 X 轴复选框）
export const X_AXIS_FIELDS = [
  { key: 'dept', label: '部门' },
  { key: 'person', label: '人员' },
  { key: 'region', label: '区域' },
  { key: 'customerType', label: '客户类型' },
  { key: 'projectType', label: '项目类型' },
  { key: 'projectStatus', label: '项目状态' },
  { key: 'tenderEntity', label: '招标主体' },
  { key: 'competitor', label: '竞品公司' }
]

// X 轴维度 label 映射（用于下钻标题）
export const X_AXIS_LABELS = {
  time: '时间',
  dept: '部门',
  person: '人员',
  region: '区域',
  customerType: '客户类型',
  projectType: '项目类型',
  projectStatus: '项目状态',
  tenderEntity: '招标主体',
  competitor: '竞品公司'
}