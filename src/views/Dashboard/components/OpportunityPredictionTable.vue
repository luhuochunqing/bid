<template>
  <el-table
    :data="rows"
    :row-class-name="() => 'op-row'"
    size="small"
    style="width: 100%"
    @row-click="handleRowClick"
  >
    <el-table-column label="序号" width="90" align="center" fixed="left">
      <template #default="{ $index }"><span class="op-row-no">{{ startIndex + $index + 1 }}</span></template>
    </el-table-column>
    <el-table-column label="项目名称" width="200" fixed="left" show-overflow-tooltip>
      <template #default="{ row }"><span class="op-proj-name op-trunc-text">{{ row.projectName || '-' }}</span></template>
    </el-table-column>
    <el-table-column label="项目状态" width="125" align="center">
      <template #default="{ row }">
        <el-tag :type="statusTagType(row.resultType)" size="small" effect="light">{{ statusLabel(row.resultType) }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="customer" label="招标主体" width="180" show-overflow-tooltip>
      <template #default="{ row }"><span class="op-trunc-text">{{ row.customer || '-' }}</span></template>
    </el-table-column>
    <el-table-column label="项目负责人" width="100" align="center">
      <template #default="{ row }">{{ row.projectLeaderName || '-' }}</template>
    </el-table-column>
    <el-table-column label="客户类型" width="100" align="center">
      <template #default="{ row }">
        <span class="op-chip" :class="customerTypeChip(row.customerType)">{{ customerTypeLabel(row.customerType) }}</span>
      </template>
    </el-table-column>
    <el-table-column label="项目类型" width="90" align="center">
      <template #default="{ row }">
        <span class="op-chip" :class="projectTypeChip(row.projectType)">{{ projectTypeLabel(row.projectType) }}</span>
      </template>
    </el-table-column>
    <el-table-column label="开标时间" width="110" align="center">
      <template #default="{ row }">
        <DateCell :value="row.bidOpenTime" />
      </template>
    </el-table-column>
    <el-table-column label="项目服务周期（年）" width="130" align="center">
      <template #default="{ row }">{{ formatYears(row.servicePeriodYears) }}</template>
    </el-table-column>
    <el-table-column label="服务周期截止时间" width="120" align="center">
      <template #default="{ row }">
        <DateCell :value="row.servicePeriodEndDate" />
      </template>
    </el-table-column>
    <el-table-column label="预测招标时间" width="120" align="center">
      <template #default="{ row }">
        <DateCell :value="row.predictedBidDate" emphasized>
          <span v-if="row.near" class="op-near-dot"></span>
        </DateCell>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup>
import { h } from 'vue'
import { useRouter } from 'vue-router'
import { navigateToProject } from '@/utils/projectNavigation.js'
import { customerTypeLabel, projectTypeLabel } from '@/views/Project/utils/projectListFormatters.js'
import { getProjectStatusType } from '@/views/Dashboard/workbench-formatters.js'
import { projectStatusLabel } from '@/views/Dashboard/workbench-utils.js'

defineProps({
  rows: { type: Array, default: () => [] },
  // 当前页在过滤后全量结果中的起始下标，用于跨页连续序号
  startIndex: { type: Number, default: 0 },
})

const router = useRouter()

function formatDateParts(value) {
  const normalized = value ? String(value).slice(0, 10) : ''
  return normalized ? { ym: normalized.slice(0, 7), d: normalized.slice(8) } : null
}

// 统一时间单元格：第一行年-月（yyyy-MM），第二行 —日（补上月和日之间的破折号），
// 三个时间列共用，避免各列日期排版不一致；emphasized 用于预测列绿色强调 + 红点插槽
const DateCell = (props, { slots }) => {
  const parts = formatDateParts(props.value)
  if (!parts) return h('span', { class: 'op-date' }, '-')
  return h('span', { class: ['op-date', { 'op-date-em': props.emphasized }] }, [
    h('span', { class: 'op-date-ym' }, parts.ym),
    h('span', { class: 'op-date-d' }, ['—' + parts.d, slots.default ? slots.default() : null]),
  ])
}
DateCell.props = ['value', 'emphasized']

function formatYears(value) {
  if (value === null || value === undefined || value === '') return '-'
  const num = Number(value)
  return Number.isFinite(num) ? String(num) : '-'
}

// 项目状态标签复用 workbench-utils.js 的 8 状态映射（避免重复定义），
// ElTag 配色复用 workbench-formatters.js 的唯一映射（见 Dashboard/README.md）
const statusLabel = (resultType) => projectStatusLabel(resultType) || '-'
const statusTagType = (resultType) => getProjectStatusType(statusLabel(resultType))

// 客户类型轻标签配色（与 V3 设计稿一致）
function customerTypeChip(value) {
  return {
    CENTRAL_SOE: 'op-chip-central',
    LOCAL_SOE: 'op-chip-local',
    PRIVATE: 'op-chip-private',
  }[value] || ''
}

// 项目类型轻标签配色（与 V3 设计稿一致）
function projectTypeChip(value) {
  return {
    INDUSTRIAL: 'op-chip-industrial',
    OFFICE: 'op-chip-office',
    COLLECTIVE: 'op-chip-collective',
  }[value] || ''
}

function handleRowClick(row) {
  const projectId = String(row?.projectId || '')
  if (/^\d+$/.test(projectId)) {
    navigateToProject(router, projectId)
  }
}

</script>

<style scoped>
/* 全局 table.css 给所有 el-table 体单元格设了 white-space:normal + overflow:visible
   （长内容自动换行基线），会覆盖 show-overflow-tooltip 依赖的截断样式：
   项目名称/招标主体列无法出省略号、日期单元格被挤成多行。
   只对需要截断的两列（项目名称/招标主体）恢复单行截断；
   项目状态等其余单元格保持 nowrap 但不加 ellipsis——
   否则状态标签被挤压时会渲染出省略号「…」，看起来像黑点。 */
.el-table :deep(.el-table .op-row) {
  cursor: pointer;
}
.el-table :deep(.el-table__body .cell) {
  white-space: nowrap;
}
.el-table :deep(.el-table__body td:has(.op-trunc-text) .cell) {
  overflow: hidden;
  text-overflow: ellipsis;
}
/* 日期单元格保持两行排版（第一行年-月、第二行 —日），不受上面 nowrap 影响 */
.el-table :deep(.op-date) {
  white-space: normal;
}
.op-row-no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--brand-xiyu-logo-light);
  color: var(--brand-xiyu-logo);
  font-size: 0.78rem;
  font-weight: 600;
}
.op-proj-name { color: var(--text-primary-ui); font-weight: 500; }
.op-chip {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 0.75rem;
  background: var(--gray-50);
  color: var(--text-secondary-ui);
}
.op-chip-central { background: #eef4ff; color: #3a6df0; }
.op-chip-local { background: #fdf6ec; color: #b8821a; }
.op-chip-private { background: #f0f9eb; color: #529b2e; }
.op-chip-industrial { background: #f0f7ff; color: #2e7cd6; }
.op-chip-office { background: #f4f4f5; color: #6b7794; }
.op-chip-collective { background: #fdf6ec; color: #b88230; }
/* DateCell 是函数式组件，内层 span 不继承 scoped 属性，内层样式需 :deep() 命中 */
.op-date {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  line-height: 1.25;
  font-variant-numeric: tabular-nums;
}
.el-table :deep(.op-date-ym) { font-size: 0.8rem; }
.el-table :deep(.op-date-d) { font-size: 0.72rem; color: var(--text-muted); }
.op-date-em { color: var(--brand-xiyu-logo); font-weight: 600; }
.el-table :deep(.op-date-em .op-date-d) { color: var(--brand-xiyu-logo); }
.op-near-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-danger, #f56c6c);
  margin-left: 4px;
  vertical-align: middle;
  animation: op-pulse 1.4s ease-in-out infinite;
}
@keyframes op-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.7); }
}
</style>
