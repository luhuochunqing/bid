<template>
  <el-select-v2
    v-model="selectedId"
    :options="selectOptions"
    :filterable="true"
    :remote="mode === 'search'"
    :remote-method="handleRemoteSearch"
    :loading="loading"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    :multiple="multiple"
    value-key="value"
    @change="handleChange"
  >
    <template v-if="selectOptions.length === 0" #empty>
      无匹配用户
    </template>
  </el-select-v2>
</template>

<script setup>
import { ref, watch, onMounted, computed } from 'vue'
import { useUserPicker } from '@/composables/useUserPicker.js'

const props = defineProps({
  modelValue: { type: [Number, String, Array], default: null },
  mode: { type: String, default: 'search' },
  context: { type: String, default: '' },
  deptCode: { type: String, default: '' },
  roleCode: { type: String, default: '' },
  placeholder: { type: String, default: '请选择用户' },
  disabled: { type: Boolean, default: false },
  valueField: { type: String, default: 'id' },
  initialOptions: { type: Array, default: () => [] },
  clearable: { type: Boolean, default: true },
  multiple: { type: Boolean, default: false },
  // 要从下拉中排除的用户 id 列表（如审核人场景排除项目负责人/团队成员）。
  // 过滤在合并后选项上做，避免后端搜索接口为此新增参数。
  excludeIds: { type: Array, default: () => [] },
  // 可选：按 roleCode 在前端进一步过滤候选（如辅助人员仅展示 bid-Team 角色）。
  // 支持单个字符串或字符串数组；放在前端是因为 usersApi.search 不支持 roleCode 过滤。
  roleFilter: { type: [String, Array], default: '' },
  // 是否在挂载时自动加载候选人。外部已加载好候选列表时设为 false，避免重复请求。
  loadOnMount: { type: Boolean, default: true },
  // 是否在下拉选项标签后追加部门/组织信息（如分发场景需要展示部门）。
  showDepartment: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'select'])

const { options, loading, search, loadCandidates, formatLabel } = useUserPicker({
  mode: props.mode,
  context: props.context,
  deptCode: props.deptCode,
  roleCode: props.roleCode,
})

const selectedId = ref(props.multiple ? (props.modelValue || []) : props.modelValue)

// Merge initial options with searched options, deduped by id, so callers
// can preload candidates (e.g. from a dedicated list endpoint) while still
// benefiting from UserPicker's remote search.
const mergedOptions = computed(() => {
  const byId = new Map()
  for (const u of props.initialOptions) {
    if (u?.id != null) byId.set(u.id, u)
  }
  for (const u of options.value) {
    if (u?.id != null) byId.set(u.id, u)
  }
  return Array.from(byId.values())
})

// Convert to el-select-v2 options format { value, label }
// el-select-v2 is data-driven and correctly handles remote search results
// (el-select + slot el-option has a bug where optionsArray is not recomputed
//  after remote results arrive while the dropdown is open)
//
// 搜索态只展示搜索结果，不混入 initialOptions：
// el-select-v2 在 remote 模式下 isValidOption 对所有选项返回 true（见
// useSelect.mjs isRemoteMethodValid），导致预加载的"固定人员"始终和搜索结果
// 一起展示，搜索命中被淹没。仅当无搜索结果时回落到 mergedOptions（含
// initialOptions），保证未搜索/关闭态下已选值的标签仍能渲染。
const selectOptions = computed(() => {
  const searching = props.mode === 'search' && options.value.length > 0
  const source = searching ? options.value : mergedOptions.value
  return source
    .filter((user) => !isExcluded(user))
    .filter((user) => matchesRoleFilter(user))
    .map((user) => ({
      value: getOptionValue(user),
      label: buildOptionLabel(user),
    }))
})

function isExcluded(user) {
  if (!props.excludeIds || props.excludeIds.length === 0) return false
  const uid = getOptionValue(user)
  // 宽松比较，兼容 number/string id
  return props.excludeIds.some((id) => id == uid)
}

function matchesRoleFilter(user) {
  if (!props.roleFilter) return true
  const code = String(user?.roleCode || user?.role || '').trim()
  if (Array.isArray(props.roleFilter)) {
    return props.roleFilter.some((filter) => String(filter).trim() === code)
  }
  return code === String(props.roleFilter).trim()
}

watch(() => props.modelValue, (val) => {
  if (props.multiple) {
    // For multiple: deep compare arrays to avoid infinite loops
    const current = selectedId.value || []
    const next = val || []
    if (current.length !== next.length || !current.every((v, i) => v == next[i])) {
      selectedId.value = next
    }
  } else if (val !== selectedId.value) {
    selectedId.value = val
  }
})

function handleRemoteSearch(query) {
  if (props.mode === 'search') {
    search(query)
  }
}

function buildOptionLabel(user) {
  const base = formatLabel(user)
  if (!props.showDepartment) return base
  const dept = user?.deptName || user?.departmentName || user?.dept || user?.orgName
  if (!dept) return base
  return `${base} · ${dept}`
}

function getOptionValue(user) {
  if (!user) return null
  return user[props.valueField] ?? user.id
}

function handleChange(value) {
  // Use loose equality so both numeric and string ids from stubbed selects work.
  emit('update:modelValue', value)
  if (props.multiple) {
    // Multi-select: emit array of selected user objects
    const values = Array.isArray(value) ? value : []
    const selectedUsers = values
      .map((v) => mergedOptions.value.find((user) => getOptionValue(user) == v))
      .filter(Boolean)
    if (selectedUsers.length > 0) {
      emit('select', selectedUsers)
    }
  } else {
    // Single-select: emit one user object
    const selected = mergedOptions.value.find((user) => getOptionValue(user) == value)
    if (selected) {
      emit('select', selected)
    }
  }
}

onMounted(() => {
  if (props.mode === 'candidates' && props.loadOnMount) {
    loadCandidates()
  }
})
</script>