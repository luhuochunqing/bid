<template>
  <el-card class="search-card tender-search-card" shadow="never">
    <el-form :model="modelValue" label-position="top" class="tender-search-form">
      <el-form-item label="关键词" class="search-field search-field--keyword">
        <el-input v-model="modelValue.keyword" placeholder="搜索项目名称、招标主体" clearable class="search-input">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </el-form-item>
      <el-form-item label="总部所在地" class="search-field">
        <el-cascader
          v-model="regionValue"
          :options="chinaRegionOptions"
          :props="{ expandTrigger: 'hover', label: 'name', value: 'name', checkStrictly: true, emitPath: true }"
          placeholder="全部"
          clearable
          filterable
          class="filter-select"
          style="width:100%"
        />
      </el-form-item>
      <el-form-item label="状态" class="search-field">
        <el-select v-model="modelValue.status" placeholder="全部" clearable multiple collapse-tags collapse-tags-tooltip class="filter-select">
          <el-option label="待分配" value="PENDING_ASSIGNMENT" />
          <el-option label="跟踪中" value="TRACKING" />
          <el-option label="已评估" value="EVALUATED" />
          <el-option label="投标中" value="BIDDING" />
          <el-option label="已中标" value="WON" />
          <el-option label="未中标" value="LOST" />
          <el-option label="已放弃" value="ABANDONED" />
        </el-select>
      </el-form-item>
      <el-form-item label="来源平台" class="search-field">
        <el-select v-model="modelValue.source" placeholder="全部" clearable multiple collapse-tags collapse-tags-tooltip class="filter-select">
          <el-option v-for="item in sourcePlatformOptions" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目类型" class="search-field">
        <el-select v-model="modelValue.projectType" placeholder="全部" clearable class="filter-select">
          <el-option v-for="item in projectTypeOptions" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="客户类型" class="search-field">
        <el-select v-model="modelValue.customerType" placeholder="全部" clearable multiple collapse-tags collapse-tags-tooltip class="filter-select">
          <el-option v-for="item in customerTypeOptions" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="优先级" class="search-field">
        <el-select v-model="modelValue.priority" placeholder="全部" clearable multiple collapse-tags collapse-tags-tooltip class="filter-select">
          <el-option v-for="item in priorityOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="报名截止时间" class="search-field--datetime">
        <el-date-picker v-model="registrationDeadlineRange" type="datetimerange" range-separator="至"
          start-placeholder="开始时间" end-placeholder="结束时间" value-format="YYYY-MM-DDTHH:mm:ss" class="filter-datetime-picker" clearable />
      </el-form-item>
      <el-form-item label="开标时间" class="search-field--datetime">
        <el-date-picker v-model="bidOpeningTimeRange" type="datetimerange" range-separator="至"
          start-placeholder="开始时间" end-placeholder="结束时间" value-format="YYYY-MM-DDTHH:mm:ss" class="filter-datetime-picker" clearable />
      </el-form-item>
      <el-form-item label="项目负责人" class="search-field">
        <el-select v-model="modelValue.projectManagerId" placeholder="全部" clearable filterable remote :remote-method="(q) => searchUsers(q, 'pm')" :loading="userLoading.pm" class="filter-select">
          <el-option v-for="u in userOptions.pm" :key="u.id" :label="`${u.name}（${u.employeeId}）`" :value="u.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="创建人" class="search-field">
        <el-select v-model="modelValue.creatorId" placeholder="全部" clearable filterable remote :remote-method="(q) => searchUsers(q, 'cr')" :loading="userLoading.cr" class="filter-select">
          <el-option v-for="u in userOptions.cr" :key="u.id" :label="`${u.name}（${u.employeeId}）`" :value="u.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间" class="search-field--datetime">
        <el-date-picker v-model="createdAtRange" type="datetimerange" range-separator="至"
          start-placeholder="开始时间" end-placeholder="结束时间" value-format="YYYY-MM-DDTHH:mm:ss" class="filter-datetime-picker" clearable />
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
import { RefreshLeft, Search } from '@element-plus/icons-vue'
import { SOURCE_PLATFORM_OPTIONS, PROJECT_TYPE_OPTIONS, CUSTOMER_TYPE_OPTIONS, PRIORITY_OPTIONS } from '../constants.js'
import { chinaRegionOptions } from '@/components/common/chinaRegionData.js'
import { useRegionCascaderValue } from '@/composables/useRegionCascaderValue.js'
import { usersApi } from '@/api/modules/users.js'
import { computed, reactive, ref } from 'vue'

const modelValue = defineModel({ type: Object, required: true })
defineEmits(['search', 'reset'])

const props = defineProps({
  sourcePlatformOptions: { type: Array, default: () => SOURCE_PLATFORM_OPTIONS },
  projectTypeOptions: { type: Array, default: () => PROJECT_TYPE_OPTIONS },
  customerTypeOptions: { type: Array, default: () => CUSTOMER_TYPE_OPTIONS },
  priorityOptions: { type: Array, default: () => PRIORITY_OPTIONS },
})

const userOptions = reactive({ pm: [], cr: [] })
const userLoading = reactive({ pm: false, cr: false })

async function searchUsers(query, scope) {
  if (!query || query.length < 1) { userOptions[scope] = []; return }
  userLoading[scope] = true
  try {
    const result = await usersApi.search(query, 20)
    userOptions[scope] = Array.isArray(result) ? result : []
  } finally {
    userLoading[scope] = false
  }
}

// 搜索过滤场景：emptyValue 传 null（"未筛选"），区别于表单 reset 的 ''
const regionValue = useRegionCascaderValue(
  () => modelValue.value.region,
  (v) => { modelValue.value.region = v },
  { emptyValue: null },
)

const registrationDeadlineRange = computed({
  get: () => {
    const { registrationDeadlineFrom: f, registrationDeadlineTo: t } = modelValue.value
    return (f && t) ? [f, t] : null
  },
  set: (val) => {
    modelValue.value.registrationDeadlineFrom = val?.[0] || null
    modelValue.value.registrationDeadlineTo = val?.[1] || null
  }
})

const bidOpeningTimeRange = computed({
  get: () => {
    const { bidOpeningTimeFrom: f, bidOpeningTimeTo: t } = modelValue.value
    return (f && t) ? [f, t] : null
  },
  set: (val) => {
    modelValue.value.bidOpeningTimeFrom = val?.[0] || null
    modelValue.value.bidOpeningTimeTo = val?.[1] || null
  }
})

const createdAtRange = computed({
  get: () => {
    const { createdAtFrom: f, createdAtTo: t } = modelValue.value
    return (f && t) ? [f, t] : null
  },
  set: (val) => {
    modelValue.value.createdAtFrom = val?.[0] || null
    modelValue.value.createdAtTo = val?.[1] || null
  }
})
</script>

<style scoped>
.tender-search-card { border: 1px solid var(--gray-100, #E8E8E8); border-radius: var(--radius-md, 8px); }
.tender-search-card :deep(.el-card__body) { padding: var(--space-lg, 24px); }
.tender-search-form { 
  display: flex; 
  flex-wrap: wrap; 
  align-items: flex-end; 
  gap: var(--space-md, 16px); 
  min-width: 0; 
}
.tender-search-form :deep(.el-form-item) { margin: 0; }
.tender-search-form :deep(.el-form-item__label) { margin-bottom: var(--space-xs, 4px); color: var(--text-secondary, #666); font-size: var(--font-size-xs, 12px); font-weight: 600; line-height: 1.4; }
/* .search-field / .search-field--keyword / .search-field--date / .search-field--datetime / .search-actions
   已抽取到 src/styles/form-controls.css（全局复用） */
.search-input, .filter-select { width: 100%; --focus-ring-color: transparent; --focus-ring-width: 0; --el-input-hover-border-color: var(--gray-200, #D0D0D0); }
.filter-select { --el-color-primary: var(--gray-200, #D0D0D0); --el-color-primary-light-3: var(--gray-200, #D0D0D0); --el-color-primary-light-5: var(--gray-200, #D0D0D0); --el-color-primary-light-7: var(--gray-100, #E8E8E8); --el-select-input-focus-border-color: var(--gray-200, #D0D0D0); }

/* 键盘可访问性：base 规则移除默认轮廓（不使用 !important，允许 focus-visible 覆盖） */
.tender-search-card :deep(.filter-select),
.tender-search-card :deep(.search-input),
.tender-search-card :deep(.filter-date-picker) { outline: none; }
/* focus-visible：使用 !important 确保键盘焦点轮廓可靠覆盖 base 规则 */
.tender-search-card :deep(.filter-select:focus-visible),
.tender-search-card :deep(.search-input:focus-visible) {
  outline: 2px solid var(--el-color-primary-light-3, #a0cfff) !important;
  box-shadow: 0 0 0 2px var(--el-color-primary-light-3, #a0cfff);
}
.search-actions { flex: 0 0 auto; }
.search-actions :deep(.el-form-item__content) { display: flex; gap: var(--space-sm, 8px); }
.search-submit-button, .search-reset-button { min-width: 88px; height: 40px; border-radius: var(--radius-sm, 4px); box-shadow: none; font-weight: 600; }
.search-submit-button { background: var(--brand-xiyu-logo, #2E7659); border-color: var(--brand-xiyu-logo, #2E7659); }
.search-reset-button { border-color: var(--gray-200, #D0D0D0); color: var(--text-secondary, #666); }
.filter-date-picker, .filter-datetime-picker { width: 100%; --el-date-editor-width: 100%; }
/* 范围选择器高度与 input/select 对齐（实测均渲染 32px），避免 datetimerange/daterange 偏高 */
.tender-search-card :deep(.el-range-editor) {
  height: 32px !important;
  min-height: 32px !important;
}
.tender-search-card :deep(.el-range-editor .el-range-input) {
  line-height: 32px;
  font-size: var(--font-size-sm, 14px);
}
/* 移动端响应式：form-controls.css 定义 .search-field 系列断点，此处补充 TenderSearchCard 特有样式 */
@media (max-width: 768px) {
  .tender-search-card :deep(.el-card__body) { padding: var(--space-md, 16px); }
  .filter-date-picker, .filter-datetime-picker { flex: 1 1 100%; width: 100%; --el-date-editor-width: 100%; }
  .search-actions :deep(.el-form-item__content) { width: 100%; }
  .search-submit-button, .search-reset-button { flex: 1; }
}
</style>
