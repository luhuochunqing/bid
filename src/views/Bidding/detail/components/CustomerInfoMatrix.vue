<template>
  <div class="customer-info-matrix">
    <div class="matrix-header">
      <span class="matrix-title">客户信息矩阵（{{ localData.length }} 个角色 × {{ CUSTOMER_INFO_COLUMNS.length }} 个信息维度）</span>
      <span class="matrix-hint">角色行固定不可增删</span>
    </div>
    <CustomerInfoMatrixTable
      :local-data="localData"
      :editable-columns="editableColumns"
      :disabled="disabled"
      @data-change="onDataChange"
    />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import CustomerInfoMatrixTable from './CustomerInfoMatrixTable.vue'
import { CUSTOMER_INFO_COLUMNS, CUSTOMER_INFO_ROWS } from './customerInfoMatrixConfig.js'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue'])

const editableColumns = computed(() => CUSTOMER_INFO_COLUMNS)

function makeEmptyRow(rowDef) {
  return {
    roleKey: rowDef.roleKey,
    roleLabel: rowDef.roleLabel,
    NAME: '',
    CONTACT_INFO: '',
    POSITION: '',
    XIYU_CONTACT: '',
    CONTACT_METHOD: '',
    INFO_TENDENCY_BASIS: '',
    CONTACTED: null,
    GUIDED_BID: null,
    CAN_GET_KEY_INFO: null,
    CAN_REMOVE_ADVERSE: null,
    CAN_SYNC_EVAL: null,
    TENDENCY: null,
    INFO_CLEAR_WINNER_BID: false,
    INFO_WIN_RATE_IMPACT: null,
  }
}

function mergeData(incoming) {
  const map = new Map()
  if (Array.isArray(incoming)) {
    for (const item of incoming) {
      if (item?.roleKey) map.set(item.roleKey, item)
    }
  }
  const fixedKeys = new Set(CUSTOMER_INFO_ROWS.map(r => r.roleKey))
  const fixedRows = CUSTOMER_INFO_ROWS.map((r) => {
    const existing = map.get(r.roleKey)
    if (existing) {
      return { ...makeEmptyRow(r), ...existing, roleKey: r.roleKey, roleLabel: r.roleLabel }
    }
    return makeEmptyRow(r)
  })
  const extraRows = Array.isArray(incoming)
    ? incoming
      .filter(item => item?.roleKey && !fixedKeys.has(item.roleKey))
      .map(item => {
        const roleLabel = item.roleLabel || item.roleKey
        return { ...makeEmptyRow({ roleKey: item.roleKey, roleLabel }), ...item, roleKey: item.roleKey, roleLabel }
      })
    : []
  return [...fixedRows, ...extraRows]
}

const localData = ref(mergeData(props.modelValue))

watch(
  () => props.modelValue,
  (next) => { localData.value = mergeData(next) }
)

function onDataChange() {
  emit('update:modelValue', localData.value)
}
</script>

<style scoped>
.customer-info-matrix {
  margin-top: 8px;
}

.matrix-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.matrix-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.matrix-hint {
  font-size: 12px;
  color: #909399;
}
</style>
