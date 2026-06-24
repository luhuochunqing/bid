<template>
  <el-select
    v-model="selectedId"
    :filterable="mode === 'search'"
    :remote="mode === 'search'"
    :remote-method="handleRemoteSearch"
    :loading="loading"
    :placeholder="placeholder"
    :disabled="disabled"
    @change="handleChange"
  >
    <el-option
      v-for="user in mergedOptions"
      :key="user.id"
      :label="formatLabel(user)"
      :value="user.id"
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
  modelValue: { type: [Number, String], default: null },
  mode: { type: String, default: 'search' },
  context: { type: String, default: '' },
  deptCode: { type: String, default: '' },
  roleCode: { type: String, default: '' },
  placeholder: { type: String, default: '请选择用户' },
  disabled: { type: Boolean, default: false },
  initialOptions: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:modelValue', 'select'])

const { options, loading, search, loadCandidates, formatLabel } = useUserPicker({
  mode: props.mode,
  context: props.context,
  deptCode: props.deptCode,
  roleCode: props.roleCode,
})

const selectedId = ref(props.modelValue)

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
  if (val !== selectedId.value) {
    selectedId.value = val
  }
})

function handleRemoteSearch(query) {
  if (props.mode === 'search') {
    search(query)
  }
}

function handleChange(value) {
  // Use loose equality so both numeric and string ids from stubbed selects work.
  const selected = mergedOptions.value.find((user) => user.id == value)
  emit('update:modelValue', value)
  if (selected) {
    emit('select', selected)
  }
}

onMounted(() => {
  if (props.mode === 'candidates') {
    loadCandidates()
  }
})
</script>
