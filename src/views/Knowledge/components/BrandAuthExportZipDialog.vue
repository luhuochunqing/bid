<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="val => $emit('update:visible', val)"
    title="导出 ZIP 配置"
    width="480px"
    :close-on-click-modal="false"
  >
    <div class="export-zip-dialog-body">
      <div class="export-hint">
        <el-icon><InfoFilled /></el-icon>
        <span>将导出 {{ totalCount }} 条授权的台账和附件</span>
      </div>

      <div class="select-all-row">
        <el-checkbox
          :model-value="selectAll"
          :indeterminate="indeterminate"
          @change="handleSelectAllChange"
        >
          全选
        </el-checkbox>
      </div>

      <el-checkbox-group v-model="checkedTypes" class="type-checkbox-group">
        <div v-for="item in ATTACHMENT_TYPES" :key="item.value" class="type-row">
          <el-checkbox :value="item.value">
            {{ item.label }}
          </el-checkbox>
        </div>
      </el-checkbox-group>
    </div>

    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button
        type="primary"
        :disabled="!canConfirm"
        @click="handleConfirm"
      >
        确认导出
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  totalCount: { type: Number, default: 0 },
  authorizationType: { type: String, default: 'MANUFACTURER' }
})

const emit = defineEmits(['update:visible', 'confirm'])

const MFG_TYPES = [
  { value: 'AUTH_DOC', label: '原厂授权附件' },
  { value: 'SUPPLEMENTARY', label: '补充材料附件' }
]
const AGENT_TYPES = [
  { value: 'AGENT_AUTH_1', label: '代理商授权1附件' },
  { value: 'AGENT_AUTH_2', label: '代理商授权2附件' },
  { value: 'SUPPLEMENTARY', label: '补充材料附件' }
]

const ATTACHMENT_TYPES = computed(() =>
  props.authorizationType === 'AGENT' ? AGENT_TYPES : MFG_TYPES
)

const ALL_VALUES = computed(() => ATTACHMENT_TYPES.value.map(t => t.value))

const checkedTypes = ref([...ALL_VALUES.value])

const selectAll = computed(() => checkedTypes.value.length === ALL_VALUES.value.length)

const indeterminate = computed(() => {
  const len = checkedTypes.value.length
  return len > 0 && len < ALL_VALUES.value.length
})

const canConfirm = computed(() => checkedTypes.value.length > 0)

function handleSelectAllChange(val) {
  checkedTypes.value = val ? [...ALL_VALUES.value] : []
}

function handleCancel() {
  emit('update:visible', false)
}

function handleConfirm() {
  if (!canConfirm.value) return
  emit('confirm', [...checkedTypes.value])
}

watch(() => props.visible, (val) => {
  if (val) {
    checkedTypes.value = [...ALL_VALUES.value]
  }
})
</script>

<style scoped lang="scss">
.export-zip-dialog-body {
  padding: 0 4px;
}

.export-hint {
  margin-bottom: 16px;
  padding: 10px 12px;
  background: var(--el-color-primary-light-9);
  border-radius: 6px;
  font-size: 13px;
  color: var(--el-color-primary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.select-all-row {
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin-bottom: 8px;
}

.type-checkbox-group {
  .type-row {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 0;
  }
}
</style>
