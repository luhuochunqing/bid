<template>
  <div class="filter-bar">
    <div class="filter-grid">
      <div class="filter-item">
        <label class="filter-label">时间维度</label>
        <el-radio-group v-model="filters.timeDimension" class="time-radio-group">
          <el-radio-button value="day">日</el-radio-button>
          <el-radio-button value="week">周</el-radio-button>
          <el-radio-button value="month">月</el-radio-button>
          <el-radio-button value="year">年</el-radio-button>
        </el-radio-group>
      </div>

      <div class="filter-item">
        <label class="filter-label">部门</label>
        <FilterSelect
          v-model="filters.departments"
          :options="departmentOptions"
          :loading="loadingDepartments"
          placeholder="请选择部门"
          @search="handleDepartmentSearch"
          @change="handleDepartmentChange"
        />
      </div>

      <div class="filter-item">
        <label class="filter-label">人员</label>
        <FilterSelect
          v-model="filters.persons"
          :options="personOptions"
          :loading="loadingPersons"
          placeholder="请选择人员"
          @search="handlePersonSearch"
        />
      </div>

      <div class="filter-item">
        <label class="filter-label">区域</label>
        <FilterSelect
          v-model="filters.regions"
          :options="regionOptions"
          :loading="loadingRegions"
          placeholder="请选择区域"
          @search="handleRegionSearch"
        />
      </div>

      <div class="filter-item">
        <label class="filter-label">客户类型</label>
        <FilterSelect
          v-model="filters.customerTypes"
          :options="customerTypeOptions"
          :loading="loadingCustomerTypes"
          placeholder="请选择客户类型"
          @search="handleCustomerTypeSearch"
        />
      </div>

      <div class="filter-item">
        <label class="filter-label">项目类型</label>
        <FilterSelect
          v-model="filters.projectTypes"
          :options="projectTypeOptions"
          :loading="loadingProjectTypes"
          placeholder="请选择项目类型"
          @search="handleProjectTypeSearch"
        />
      </div>

      <div class="filter-item">
        <label class="filter-label">项目状态</label>
        <FilterSelect
          v-model="filters.projectStatuses"
          :options="projectStatusOptions"
          placeholder="请选择项目状态"
          @search="handleProjectStatusSearch"
        />
      </div>

      <div class="filter-item">
        <label class="filter-label">招标主体</label>
        <FilterSelect
          v-model="filters.tenderSubjects"
          :options="tenderSubjectOptions"
          :loading="loadingTenderSubjects"
          placeholder="请选择招标主体"
          @search="handleTenderSubjectSearch"
        />
      </div>

      <div class="filter-item">
        <label class="filter-label">竞品公司</label>
        <FilterSelect
          v-model="filters.competitors"
          :options="competitorOptions"
          :loading="loadingCompetitors"
          placeholder="请选择竞品公司"
          @search="handleCompetitorSearch"
        />
      </div>
    </div>

    <div class="x-axis-section">
      <label class="filter-label">X轴维度（可多选）</label>
      <el-checkbox-group v-model="xAxisDimensions" class="x-axis-checkbox-group">
        <el-checkbox v-for="dim in xAxisOptions" :key="dim.value" :label="dim.value">
          {{ dim.label }}
        </el-checkbox>
      </el-checkbox-group>
    </div>

    <div class="filter-actions">
      <el-button type="primary" :loading="submitting" @click="handleConfirm">确认</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { PROJECT_STATUS_OPTIONS, X_AXIS_OPTIONS } from './filterConstants.js'
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

const xAxisDimensions = ref(['time'])

const xAxisOptions = computed(() => X_AXIS_OPTIONS)

watch(() => filters.value.persons, (val) => {
  if (val.length > 0 && !xAxisDimensions.value.includes('department')) {
    xAxisDimensions.value = [...xAxisDimensions.value, 'department']
  }
})

watch(filters.value, (newFilters) => {
  const autoCheckMap = {
    departments: 'department', regions: 'region',
    customerTypes: 'customerType', projectTypes: 'projectType',
    projectStatuses: 'projectStatus', tenderSubjects: 'tenderSubject',
    competitors: 'competitor'
  }
  const next = [...xAxisDimensions.value]
  Object.entries(autoCheckMap).forEach(([key, dim]) => {
    const vals = newFilters[key]
    if (Array.isArray(vals) && vals.length > 0 && !next.includes(dim)) {
      next.push(dim)
    }
  })
  if (next.length !== xAxisDimensions.value.length ||
      !next.every((d) => xAxisDimensions.value.includes(d))) {
    xAxisDimensions.value = next
  }
}, { deep: true })

const handleDepartmentSearch = (q) => emit('search-department', q)
const handlePersonSearch = (q) => emit('search-person', q)
const handleRegionSearch = (q) => emit('search-region', q)
const handleCustomerTypeSearch = (q) => emit('search-customer-type', q)
const handleProjectTypeSearch = (q) => emit('search-project-type', q)
const handleProjectStatusSearch = (q) => emit('search-project-status', q)
const handleTenderSubjectSearch = (q) => emit('search-tender-subject', q)
const handleCompetitorSearch = (q) => emit('search-competitor', q)

const handleDepartmentChange = (val) => {
  if (val !== filters.value.departments) filters.value.persons = []
  emit('department-change', val)
}

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
  xAxisDimensions.value = ['time']
  emit('reset')
}
</script>

<style scoped>
.filter-bar {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 16px;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.filter-label {
  font-size: 13px;
  font-weight: 600;
  color: #1E293B;
  white-space: nowrap;
}

.time-radio-group { flex-shrink: 0; }

.x-axis-section {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #E2E8F0;
}

.x-axis-section .filter-label {
  display: block;
  margin-bottom: 10px;
}

.x-axis-checkbox-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.filter-actions {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #E2E8F0;
  display: flex;
  gap: 10px;
  justify-content: flex-start;
}

@media (max-width: 1200px) {
  .filter-grid { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .filter-grid { grid-template-columns: 1fr; }
}
</style>