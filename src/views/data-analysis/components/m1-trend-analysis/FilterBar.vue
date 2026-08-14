<template>
  <div class="m8-filter-bar">
    <div class="m8-filter-item">
      <input type="checkbox" class="m9-xaxis-cb" :checked="xAxisDimensions.includes('time')" @change="handleXAxisChange('time', $event.target.checked)" />
      <label class="m8-filter-label">时间维度</label>
      <el-select
        ref="timeDimensionSelectRef"
        v-model="filters.timeDimension"
        class="time-dimension-select"
        size="small"
        placeholder="请选择"
        @change="handleTimeDimensionChange"
      >
        <el-option label="日" value="day" />
        <el-option label="周" value="week" />
        <el-option label="月" value="month" />
        <el-option label="年" value="year" />
      </el-select>
    </div>

    <div class="m8-filter-item">
      <input type="checkbox" class="m9-xaxis-cb" :checked="xAxisDimensions.includes('dept')" @change="handleXAxisChange('dept', $event.target.checked)" />
      <label class="m8-filter-label">部门</label>
      <FilterSelect v-model="filters.departments" :options="departmentOptions" :loading="loadingDepartments" placeholder="请选择" @search="handleDepartmentSearch" @change="handleFieldChange('dept')" />
    </div>

    <div class="m8-filter-item">
      <input type="checkbox" class="m9-xaxis-cb" :checked="xAxisDimensions.includes('person')" @change="handleXAxisChange('person', $event.target.checked)" />
      <label class="m8-filter-label">人员</label>
      <FilterSelect v-model="filters.persons" :options="personOptions" :loading="loadingPersons" placeholder="请选择" @search="handlePersonSearch" @change="handleFieldChange('person')" />
    </div>

    <div class="m8-filter-item">
      <input type="checkbox" class="m9-xaxis-cb" :checked="xAxisDimensions.includes('region')" @change="handleXAxisChange('region', $event.target.checked)" />
      <label class="m8-filter-label">区域</label>
      <FilterSelect v-model="filters.regions" :options="regionOptions" placeholder="请选择" @search="handleRegionSearch" @change="handleFieldChange('region')" />
    </div>

    <div class="m8-filter-item">
      <input type="checkbox" class="m9-xaxis-cb" :checked="xAxisDimensions.includes('customerType')" @change="handleXAxisChange('customerType', $event.target.checked)" />
      <label class="m8-filter-label">客户类型</label>
      <FilterSelect v-model="filters.customerTypes" :options="customerTypeOptions" placeholder="请选择" @search="handleCustomerTypeSearch" @change="handleFieldChange('customerType')" />
    </div>

    <div class="m8-filter-item">
      <input type="checkbox" class="m9-xaxis-cb" :checked="xAxisDimensions.includes('projectType')" @change="handleXAxisChange('projectType', $event.target.checked)" />
      <label class="m8-filter-label">项目类型</label>
      <FilterSelect v-model="filters.projectTypes" :options="projectTypeOptions" placeholder="请选择" @search="handleProjectTypeSearch" @change="handleFieldChange('projectType')" />
    </div>

    <div class="m8-filter-item">
      <input type="checkbox" class="m9-xaxis-cb" :checked="xAxisDimensions.includes('projectStatus')" @change="handleXAxisChange('projectStatus', $event.target.checked)" />
      <label class="m8-filter-label">项目状态</label>
      <FilterSelect v-model="filters.projectStatuses" :options="projectStatusOptions" placeholder="请选择" @search="handleProjectStatusSearch" @change="handleFieldChange('projectStatus')" />
    </div>

    <div class="m8-filter-item">
      <input type="checkbox" class="m9-xaxis-cb" :checked="xAxisDimensions.includes('tenderEntity')" @change="handleXAxisChange('tenderEntity', $event.target.checked)" />
      <label class="m8-filter-label">招标主体</label>
      <FilterSelect v-model="filters.tenderSubjects" :options="tenderSubjectOptions" :loading="loadingTenderSubjects" placeholder="请选择" @search="handleTenderSubjectSearch" @change="handleFieldChange('tenderEntity')" />
    </div>

    <div class="m8-filter-item">
      <input type="checkbox" class="m9-xaxis-cb" :checked="xAxisDimensions.includes('competitor')" @change="handleXAxisChange('competitor', $event.target.checked)" />
      <label class="m8-filter-label">竞品公司</label>
      <FilterSelect v-model="filters.competitors" :options="competitorOptions" placeholder="请选择" @search="handleCompetitorSearch" @change="handleFieldChange('competitor')" />
    </div>

    <div class="m9-filter-actions">
      <button class="confirm-btn" :disabled="submitting" @click="handleConfirm">确认</button>
      <button class="confirm-btn reset" @click="handleReset">重置</button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import {
  PROJECT_STATUS_OPTIONS,
  CUSTOMER_TYPE_OPTIONS,
  PROJECT_TYPE_OPTIONS,
  COMPETITOR_OPTIONS,
  REGION_OPTIONS
} from './filterConstants.js'
import FilterSelect from './FilterSelect.vue'

const props = defineProps({
  departmentOptions: { type: Array, default: () => [] },
  personOptions: { type: Array, default: () => [] },
  tenderSubjectOptions: { type: Array, default: () => [] },
  loadingDepartments: Boolean,
  loadingPersons: Boolean,
  loadingTenderSubjects: Boolean
})

const emit = defineEmits([
  'confirm', 'reset',
  'search-department', 'search-person', 'search-region',
  'search-customer-type', 'search-project-type', 'search-project-status',
  'search-tender-subject', 'search-competitor',
  'department-change'
])

const timeDimensionSelectRef = ref(null)
const projectStatusOptions = ref(PROJECT_STATUS_OPTIONS)
const customerTypeOptions = ref(CUSTOMER_TYPE_OPTIONS)
const projectTypeOptions = ref(PROJECT_TYPE_OPTIONS)
const competitorOptions = ref(COMPETITOR_OPTIONS)
const regionOptions = ref(REGION_OPTIONS)
const submitting = ref(false)

const filters = ref({
  timeDimension: 'month',
  departments: [], persons: [], regions: [],
  customerTypes: [], projectTypes: [], projectStatuses: [],
  tenderSubjects: [], competitors: []
})

const xAxisDimensions = ref([])

// X 轴维度 key → 筛选状态字段映射（time 不映射到筛选值）
const AXIS_TO_FILTER = {
  time: null,
  dept: 'departments', person: 'persons', region: 'regions',
  customerType: 'customerTypes', projectType: 'projectTypes',
  projectStatus: 'projectStatuses', tenderEntity: 'tenderSubjects',
  competitor: 'competitors'
}
const NON_DEPT_PERSON = ['time', 'region', 'customerType', 'projectType', 'projectStatus', 'tenderEntity', 'competitor']

// 时间维度单选后自动关闭下拉框
// 用 setTimeout 确保 Element Plus 内部 change 处理完成后再触发 blur 关闭 popper
const handleTimeDimensionChange = () => {
  setTimeout(() => {
    timeDimensionSelectRef.value?.blur?.()
  }, 50)
}

// PRD 6.3 X 轴互斥逻辑
// 复选框变化时执行互斥逻辑（清空其他字段），但不立即刷新图表
// 图表刷新由"确认"按钮触发
const handleXAxisChange = (field, checked) => {
  if (checked) {
    if (field === 'dept' || field === 'person') {
      xAxisDimensions.value = xAxisDimensions.value.filter(f => f === 'dept' || f === 'person')
      if (!xAxisDimensions.value.includes(field)) xAxisDimensions.value.push(field)
      if (field === 'person' && !xAxisDimensions.value.includes('dept')) {
        xAxisDimensions.value.push('dept')
      }
      NON_DEPT_PERSON.forEach(key => { if (AXIS_TO_FILTER[key]) filters.value[AXIS_TO_FILTER[key]] = [] })
    } else {
      xAxisDimensions.value = [field]
      Object.entries(AXIS_TO_FILTER).forEach(([k, fk]) => {
        if (fk && k !== field) filters.value[fk] = []
      })
    }
  } else {
    if (field === 'dept') {
      xAxisDimensions.value = xAxisDimensions.value.filter(f => f !== 'person')
    }
    xAxisDimensions.value = xAxisDimensions.value.filter(f => f !== field)
    if (field === 'dept') emit('department-change', filters.value.departments)
  }
}

// PRD 6.3 选值自动勾选：用户选值时自动勾选复选框并触发互斥
const handleFieldChange = (dim) => {
  const filterKey = AXIS_TO_FILTER[dim]
  const vals = filters.value[filterKey]
  // PRD 6.4 部门-人员联动：部门选值变化时先清空人员选值（确保 X 轴切换时 persons 已清空）
  if (dim === 'dept') {
    filters.value.persons = []
  }
  if (Array.isArray(vals) && vals.length > 0 && !xAxisDimensions.value.includes(dim)) {
    handleXAxisChange(dim, true)
  }
  if (dim === 'dept') {
    emit('department-change', filters.value.departments)
  }
}

const handleDepartmentSearch = (q) => emit('search-department', q)
const handlePersonSearch = (q) => emit('search-person', q)
const handleRegionSearch = (q) => emit('search-region', q)
const handleCustomerTypeSearch = (q) => emit('search-customer-type', q)
const handleProjectTypeSearch = (q) => emit('search-project-type', q)
const handleProjectStatusSearch = (q) => emit('search-project-status', q)
const handleTenderSubjectSearch = (q) => emit('search-tender-subject', q)
const handleCompetitorSearch = (q) => emit('search-competitor', q)

const handleConfirm = async () => {
  submitting.value = true
  try {
    emit('confirm', { filters: { ...filters.value }, xAxisDimensions: [...xAxisDimensions.value] })
  } finally {
    submitting.value = false
  }
}

const handleReset = () => {
  filters.value = {
    timeDimension: 'month', departments: [], persons: [], regions: [],
    customerTypes: [], projectTypes: [], projectStatuses: [],
    tenderSubjects: [], competitors: []
  }
  xAxisDimensions.value = []
  emit('reset')
}
</script>

<style scoped>
.m8-filter-bar {
  display: flex;
  gap: 10px 14px;
  align-items: center;
  flex-wrap: wrap;
  padding: 14px 16px;
  background: #FAFBFC;
  border-radius: 6px;
  margin-bottom: 16px;
  border: 1px solid var(--status-neutral-bg);
}

.m8-filter-item {
  display: flex;
  align-items: center;
  gap: 5px;
  position: relative;
}

.m8-filter-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--login-brand-bg-mid);
  white-space: nowrap;
}

.m9-xaxis-cb {
  width: 14px;
  height: 14px;
  cursor: pointer;
  accent-color: var(--brand-xiyu-logo);
  margin: 0;
}

.time-radio-group {
  flex-shrink: 0;
}

.m8-filter-item :deep(.filter-select) { width: 140px; flex-shrink: 0; }
.m8-filter-item :deep(.el-select .el-select__wrapper) { min-height: 28px; }
.m8-filter-item :deep(.el-select .el-select__selection-wrapper) { overflow: hidden; }
.m8-filter-item :deep(.el-select__tags-text) {
  max-width: 90px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.time-dimension-select { width: 90px; flex-shrink: 0; }

.m9-filter-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
  margin-left: auto;
}

.confirm-btn {
  padding: 5px 14px;
  background: var(--brand-xiyu-logo);
  color: var(--bg-white);
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.confirm-btn:hover {
  background: var(--brand-xiyu-logo-hover);
}

.confirm-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.confirm-btn.reset {
  background: var(--bg-white);
  color: var(--text-badge);
  border: 1px solid var(--status-neutral-bg);
}

.confirm-btn.reset:hover {
  background: var(--bg-subtle);
}
</style>
