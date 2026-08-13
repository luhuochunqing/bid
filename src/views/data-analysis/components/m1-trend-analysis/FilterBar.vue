<template>
  <div class="m8-filter-bar">
    <div class="m8-filter-item">
      <label class="m8-filter-label">时间维度</label>
      <el-radio-group v-model="filters.timeDimension" class="time-radio-group" size="small">
        <el-radio-button value="day">日</el-radio-button>
        <el-radio-button value="week">周</el-radio-button>
        <el-radio-button value="month">月</el-radio-button>
        <el-radio-button value="year">年</el-radio-button>
      </el-radio-group>
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
      <FilterSelect v-model="filters.regions" :options="regionOptions" :loading="loadingRegions" placeholder="请选择" @search="handleRegionSearch" @change="handleFieldChange('region')" />
    </div>

    <div class="m8-filter-item">
      <input type="checkbox" class="m9-xaxis-cb" :checked="xAxisDimensions.includes('customerType')" @change="handleXAxisChange('customerType', $event.target.checked)" />
      <label class="m8-filter-label">客户类型</label>
      <FilterSelect v-model="filters.customerTypes" :options="customerTypeOptions" :loading="loadingCustomerTypes" placeholder="请选择" @search="handleCustomerTypeSearch" @change="handleFieldChange('customerType')" />
    </div>

    <div class="m8-filter-item">
      <input type="checkbox" class="m9-xaxis-cb" :checked="xAxisDimensions.includes('projectType')" @change="handleXAxisChange('projectType', $event.target.checked)" />
      <label class="m8-filter-label">项目类型</label>
      <FilterSelect v-model="filters.projectTypes" :options="projectTypeOptions" :loading="loadingProjectTypes" placeholder="请选择" @search="handleProjectTypeSearch" @change="handleFieldChange('projectType')" />
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
      <FilterSelect v-model="filters.competitors" :options="competitorOptions" :loading="loadingCompetitors" placeholder="请选择" @search="handleCompetitorSearch" @change="handleFieldChange('competitor')" />
    </div>

    <div class="m9-filter-actions">
      <button class="confirm-btn" :disabled="submitting" @click="handleConfirm">确认</button>
      <button class="confirm-btn reset" @click="handleReset">重置</button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { PROJECT_STATUS_OPTIONS } from './filterConstants.js'
import FilterSelect from './FilterSelect.vue'

const props = defineProps({
  departmentOptions: { type: Array, default: () => [] },
  personOptions: { type: Array, default: () => [] },
  regionOptions: { type: Array, default: () => [] },
  customerTypeOptions: { type: Array, default: () => [] },
  projectTypeOptions: { type: Array, default: () => [] },
  tenderSubjectOptions: { type: Array, default: () => [] },
  competitorOptions: { type: Array, default: () => [] },
  loadingDepartments: Boolean,
  loadingPersons: Boolean,
  loadingRegions: Boolean,
  loadingCustomerTypes: Boolean,
  loadingProjectTypes: Boolean,
  loadingTenderSubjects: Boolean,
  loadingCompetitors: Boolean
})

const emit = defineEmits([
  'confirm', 'reset',
  'search-department', 'search-person', 'search-region',
  'search-customer-type', 'search-project-type', 'search-project-status',
  'search-tender-subject', 'search-competitor',
  'department-change'
])

const projectStatusOptions = ref(PROJECT_STATUS_OPTIONS)
const submitting = ref(false)

const filters = ref({
  timeDimension: 'month',
  departments: [], persons: [], regions: [],
  customerTypes: [], projectTypes: [], projectStatuses: [],
  tenderSubjects: [], competitors: []
})

const xAxisDimensions = ref([])

// X 轴维度 key → 筛选状态字段映射
const AXIS_TO_FILTER = {
  dept: 'departments', person: 'persons', region: 'regions',
  customerType: 'customerTypes', projectType: 'projectTypes',
  projectStatus: 'projectStatuses', tenderEntity: 'tenderSubjects',
  competitor: 'competitors'
}
const NON_DEPT_PERSON = ['region', 'customerType', 'projectType', 'projectStatus', 'tenderEntity', 'competitor']

// PRD 6.3 X 轴互斥逻辑
const handleXAxisChange = (field, checked) => {
  if (checked) {
    if (field === 'dept' || field === 'person') {
      xAxisDimensions.value = xAxisDimensions.value.filter(f => f === 'dept' || f === 'person')
      if (!xAxisDimensions.value.includes(field)) xAxisDimensions.value.push(field)
      if (field === 'person' && !xAxisDimensions.value.includes('dept')) {
        xAxisDimensions.value.push('dept')
      }
      NON_DEPT_PERSON.forEach(key => { filters.value[AXIS_TO_FILTER[key]] = [] })
    } else {
      xAxisDimensions.value = [field]
      Object.values(AXIS_TO_FILTER).forEach(fk => {
        if (fk !== AXIS_TO_FILTER[field]) filters.value[fk] = []
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
  if (Array.isArray(vals) && vals.length > 0 && !xAxisDimensions.value.includes(dim)) {
    handleXAxisChange(dim, true)
  }
  // PRD 6.4 部门-人员联动：部门选值变化时通知父组件刷新人员下拉
  if (dim === 'dept') {
    filters.value.persons = []
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

.m8-filter-item :deep(.filter-select) {
  width: 140px;
}

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
