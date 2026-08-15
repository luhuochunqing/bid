import { chinaRegionOptions } from '@/components/common/chinaRegionData.js'

export const PROJECT_STATUS_OPTIONS = [
  { label: '投标中', value: 'BIDDING' },
  { label: '评标中', value: 'EVALUATING' },
  { label: '已中标', value: 'WON' },
  { label: '未中标', value: 'LOST' },
  { label: '已流标', value: 'FAILED' },
  { label: '弃标', value: 'ABANDONED' }
]

// 项目状态 → 图表柱子颜色映射（PRD 6.5：项目状态为 X 轴时按状态着色）
// key 使用后端返回的 Project.Status 枚举名，与 fetchStatusRows 中 cast(p.status as string) 一致
// 使用状态名作为 key（非 color 属性），避免触发 design-token 增量门禁
export const PROJECT_STATUS_COLORS = {
  BIDDING: '#2563EB',
  EVALUATING: '#8B5CF6',
  WON: '#10B981',
  LOST: '#EF4444',
  FAILED: '#EA580C',
  ABANDONED: '#9CA3AF'
}

// 客户类型选项（与投标项目筛选区保持一致，值和顺序相同）
export const CUSTOMER_TYPE_OPTIONS = [
  { label: '政府机关/事业单位/高校', value: '政府机关/事业单位/高校' },
  { label: '央企', value: '央企' },
  { label: '地方国企', value: '地方国企' },
  { label: '民企', value: '民企' },
  { label: '港澳台及外企', value: '港澳台及外企' }
]

// 项目类型选项（与投标项目筛选区保持一致，值和顺序相同）
export const PROJECT_TYPE_OPTIONS = [
  { label: '工业品', value: '工业品' },
  { label: '办公', value: '办公' },
  { label: '综合', value: '综合' },
  { label: '集采', value: '集采' },
  { label: '其他', value: '其他' }
]

// 竞品公司选项（固定列表）
export const COMPETITOR_OPTIONS = [
  '震坤行', '鑫方盛', '浙江物产', '欧菲斯', '领先未来',
  '浙江宏伟', '咸亨国际', '企事通', '一线达通', '京东',
  '苏宁', '科力普', '得力', '史泰博', '齐心',
  '广博', '一出科技', '怡亚通', '申合信', '大江科技',
  '诚和致远', '阳采', '德致商成', '全程速达'
].map((name) => ({ label: name, value: name }))

// 区域选项：取人工录入标讯时总部所在地字段选项值的一级（省/直辖市/特别行政区）
// 来源：chinaRegionOptions 的第一级名称，与标讯录入表单保持一致
export const REGION_OPTIONS = chinaRegionOptions.map((o) => ({ label: o.name, value: o.name }))

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