<template>
  <el-select
    v-model="selectedId"
    :filterable="mode === 'search'"
    :remote="mode === 'search'"
    :remote-method="handleRemoteSearch"
    :loading="loading"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    :multiple="multiple"
    @change="handleChange"
  >
    <el-option
      v-for="user in mergedOptions"
      :key="user.id"
      :label="formatLabel(user)"
      :value="getOptionValue(user)"
    />
    <template v-if="mergedOptions.length === 0" #empty>
      无匹配用户
    </template>
  </el-select>
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
  if (props.mode === 'candidates') {
    loadCandidates()
  }
})
</script>