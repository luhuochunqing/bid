<template>
  <el-card class="search-card project-search-card" shadow="never">
    <el-form :model="searchForm" class="project-search-form" label-position="top">
      <el-form-item label="关键词" class="search-field search-field--keyword">
        <el-input v-model="searchForm.name" placeholder="搜索项目名称、招标主体" clearable class="search-input">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </el-form-item>
      <el-form-item label="来源平台" class="search-field">
        <el-select v-model="searchForm.sourceModule" placeholder="全部" clearable class="filter-select">
          <el-option v-for="opt in sourceOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目状态" class="search-field">
        <el-select v-model="searchForm.bidStatus" placeholder="全部" clearable multiple collapse-tags collapse-tags-tooltip class="filter-select">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目阶段" class="search-field">
        <el-select v-model="searchForm.stage" placeholder="全部" clearable multiple collapse-tags collapse-tags-tooltip class="filter-select">
          <el-option v-for="opt in stageOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="总部所在地" class="search-field">
        <el-cascader
          v-model="regionValue"
          :options="chinaRegionOptions"
          :props="{ expandTrigger: 'hover', label: 'name', value: 'name', checkStrictly: false, emitPath: true }"
          placeholder="全部"
          clearable
          filterable
          class="filter-select"
          style="width:100%"
        />
      </el-form-item>
      <el-form-item label="项目类型" class="search-field">
        <el-select v-model="searchForm.projectType" placeholder="全部" clearable multiple collapse-tags collapse-tags-tooltip class="filter-select">
          <el-option v-for="opt in projectTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="客户类型" class="search-field">
        <el-select v-model="searchForm.customerType" placeholder="全部" clearable multiple collapse-tags collapse-tags-tooltip class="filter-select">
          <el-option v-for="opt in customerTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="优先级" class="search-field">
        <el-select v-model="searchForm.priority" placeholder="全部" clearable multiple collapse-tags collapse-tags-tooltip class="filter-select">
          <el-option v-for="opt in priorityOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="投标月份" class="search-field">
        <el-select v-model="searchForm.bidMonth" placeholder="全部" clearable class="filter-select">
          <el-option v-for="opt in bidMonthOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间" class="search-field--datetime">
        <el-date-picker v-model="searchForm.createTimeRange" type="daterange" range-separator="至"
          start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" class="filter-date-picker" clearable />
      </el-form-item>
      <el-form-item label="开标时间" class="search-field--datetime">
        <el-date-picker v-model="searchForm.bidOpenTimeRange" type="daterange" range-separator="至"
          start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" class="filter-date-picker" clearable />
      </el-form-item>
      <el-form-item label="项目负责人" class="search-field">
        <el-select v-model="searchForm.projectLeaderName" placeholder="全部" clearable filterable remote :remote-method="(q) => searchUsers(q, 'pm')" :loading="userLoading.pm" class="filter-select">
          <el-option v-for="u in userOptions.pm" :key="u.id" :label="u.name" :value="u.name" />
        </el-select>
      </el-form-item>
      <el-form-item label="投标负责人" class="search-field">
        <el-select v-model="searchForm.biddingLeaderName" placeholder="全部" clearable filterable remote :remote-method="(q) => searchUsers(q, 'bp')" :loading="userLoading.bp" class="filter-select">
          <el-option v-for="u in userOptions.bp" :key="u.id" :label="u.name" :value="u.name" />
        </el-select>
      </el-form-item>
      <el-form-item label="投标平台" class="search-field">
        <el-input v-model="searchForm.biddingPlatform" placeholder="请输入" clearable class="search-input" />
      </el-form-item>
      <el-form-item class="search-actions">
        <el-button type="primary" class="search-submit-button" @click="$emit('search')">
          <el-icon><Search /></el-icon>搜索
        </el-button>
        <el-button class="search-reset-button" @click="$emit('reset')">
          <el-icon><RefreshLeft /></el-icon>重置
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>
<script setup>
import { computed } from 'vue'
import { Search, RefreshLeft } from '@element-plus/icons-vue'

const props = defineProps({
  searchForm: { type: Object, required: true },
  sourceOptions: { type: Array, default: () => [] },
  statusOptions: { type: Array, default: () => [] },
  stageOptions: { type: Array, default: () => [] },
  priorityOptions: { type: Array, default: () => [] },
  projectTypeOptions: { type: Array, default: () => [] },
  customerTypeOptions: { type: Array, default: () => [] },
  bidMonthOptions: { type: Array, default: () => [] },
  chinaRegionOptions: { type: Array, default: () => [] },
  userOptions: { type: Object, default: () => ({ pm: [], bp: [] }) },
  userLoading: { type: Object, default: () => ({ pm: false, bp: false }) },
  searchUsers: { type: Function, default: () => {} },
})

defineEmits(['search', 'reset'])

const regionValue = computed({
  get: () => {
    const v = props.searchForm.region
    if (!v) return null
    for (const province of props.chinaRegionOptions) {
      if (province.name === v) return [v]
      if (province.children) {
        for (const city of province.children) {
          if (city.name === v) return [province.name, city.name]
          if (v.startsWith(province.name) && v.endsWith(city.name)) return [province.name, city.name]
        }
      }
    }
    return v
  },
  set: (val) => {
    if (!val) { props.searchForm.region = ''; return }
    if (Array.isArray(val)) { props.searchForm.region = val.join('') }
    else { props.searchForm.region = val }
  }
})
</script>
<style scoped>
.project-search-card { border: 1px solid var(--gray-100, #E8E8E8); border-radius: var(--radius-md, 8px); }
.project-search-card :deep(.el-card__body) { padding: var(--space-lg, 24px); }
.project-search-form { display: flex; flex-wrap: wrap; align-items: flex-end; gap: var(--space-md, 16px) var(--space-lg, 24px); }
.project-search-form :deep(.el-form-item) { margin: 0; }
.project-search-form :deep(.el-form-item__label) { margin-bottom: var(--space-xs, 4px); color: var(--text-secondary, #666); font-size: var(--font-size-xs, 12px); font-weight: 600; line-height: 1.4; }
.search-input, .filter-select { width: 100%; --focus-ring-color: transparent; --focus-ring-width: 0; --el-input-hover-border-color: var(--gray-200, #D0D0D0); }
.filter-select { --el-color-primary: var(--gray-200, #D0D0D0); --el-color-primary-light-3: var(--gray-200, #D0D0D0); --el-color-primary-light-5: var(--gray-200, #D0D0D0); --el-color-primary-light-7: var(--gray-100, #E8E8E8); --el-select-input-focus-border-color: var(--gray-200, #D0D0D0); }
.search-actions { flex: 0 0 auto; }
.search-actions :deep(.el-form-item__content) { display: flex; gap: var(--space-sm, 8px); }
.search-submit-button, .search-reset-button { min-width: 88px; height: 40px; border-radius: var(--radius-sm, 4px); box-shadow: none; font-weight: 600; }
.search-submit-button { background: var(--brand-xiyu-logo, #2E7659); border-color: var(--brand-xiyu-logo, #2E7659); }
.search-reset-button { border-color: var(--gray-200, #D0D0D0); color: var(--text-secondary, #666); }
.filter-date-picker { width: 100%; --el-date-editor-width: 100%; }
.search-field--datetime { width: 280px; flex: 0 0 280px; }
@media (max-width: 768px) {
  .project-search-card :deep(.el-card__body) { padding: var(--space-md, 16px); }
  .filter-date-picker { flex: 1 1 100%; width: 100%; --el-date-editor-width: 100%; }
  .search-actions :deep(.el-form-item__content) { width: 100%; }
  .search-submit-button, .search-reset-button { flex: 1; }
}
</style>
