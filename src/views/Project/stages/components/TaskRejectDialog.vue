<template>
  <el-dialog
    :model-value="visible"
    title="驳回任务"
    width="420px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-form label-width="0">
      <el-form-item :label="'驳回：' + (task?.title || '')" />
      <el-input v-model="reason" type="textarea" :rows="3" placeholder="请填写驳回原因" />
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="danger" :loading="loading" @click="handleConfirm">确认驳回</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  visible: Boolean,
  task: Object,
  loading: Boolean,
})
const emit = defineEmits(['update:visible', 'confirm'])

const reason = ref('')

watch(() => props.visible, (v) => {
  if (v) reason.value = ''
})

function handleConfirm() {
  if (!reason.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  emit('confirm', reason.value.trim())
}
</script>
