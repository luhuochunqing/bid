<template>
  <el-dialog :model-value="visible" title="忽略外部结果" width="520px" @close="$emit('close')">
    <p class="hint">请填写忽略原因，后续复盘会保留这条人工决策。</p>
    <el-input
      :model-value="reason"
      type="textarea"
      :rows="4"
      placeholder="请输入忽略原因"
      @update:model-value="$emit('update:reason', $event)"
    />
    <template #footer>
      <el-button @click="$emit('close')">取消</el-button>
      <el-button type="danger" :loading="submitting" @click="$emit('submit')">确认忽略</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  visible: Boolean,
  reason: {
    type: String,
    default: ''
  },
  submitting: Boolean
})

defineEmits(['close', 'submit', 'update:reason'])
</script>

<style scoped>
.hint {
  margin: 0 0 12px;
  color: var(--text-secondary-ui);
}
</style>
