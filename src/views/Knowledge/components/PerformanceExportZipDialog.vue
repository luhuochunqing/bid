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
        <span>{{ exportHintText }}</span>
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
          <el-tag v-if="item.required" size="small" type="danger" effect="light">必传</el-tag>
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
import { PERF_ATTACHMENT_TYPES, PERF_ALL_VALUES } from './attachmentTypeConstants'

const props = defineProps({
  visible: { type: Boolean, default: false },
  selectedCount: { type: Number, default: 0 },
  totalCount: { type: Number, default: 0 }
})

const emit = defineEmits(['update:visible', 'confirm'])

const ATTACHMENT_TYPES = PERF_ATTACHMENT_TYPES

const ALL_VALUES = PERF_ALL_VALUES

const checkedTypes = ref([...ALL_VALUES])

const selectAll = computed(() => checkedTypes.value.length === ALL_VALUES.length)

const indeterminate = computed(() => {
  const len = checkedTypes.value.length
  return len > 0 && len < ALL_VALUES.length
})

const canConfirm = computed(() => checkedTypes.value.length > 0)

const exportHintText = computed(() => {
  if (props.selectedCount > 0) {
    return `将导出选中的 ${props.selectedCount} 条业绩的附件`
  }
  return `将导出全部 ${props.totalCount} 条业绩的附件`
})

function handleSelectAllChange(val) {
  checkedTypes.value = val ? [...ALL_VALUES] : []
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
    checkedTypes.value = [...ALL_VALUES]
  }
})

defineExpose({ checkedTypes, selectAll, indeterminate, canConfirm, handleSelectAllChange })
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
