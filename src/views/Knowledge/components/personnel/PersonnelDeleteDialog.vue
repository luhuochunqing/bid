<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="删除人员档案"
    width="520px"
    :close-on-click-modal="false"
    @close="cancelDelete"
  >
    <div v-if="personnel">
      <p>确认删除以下人员的档案？</p>
      <p><strong>工号：</strong>{{ personnel.employeeNumber }}</p>
      <p><strong>姓名：</strong>{{ personnel.name }}</p>

      <div v-if="personnel.certificates && personnel.certificates.length > 0" class="delete-warning">
        ⚠️ 该人员持有 {{ personnel.certificates.length }} 张证书，删除后这些证书的到期提醒将停止。
      </div>

      <el-form label-width="80px" style="margin-top: 16px;">
        <el-form-item label="删除原因" required>
          <el-input
            v-model="deleteReason"
            type="textarea"
            :rows="3"
            placeholder="请填写删除原因（必填）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="deleteConfirmed">我已确认该人员档案可以删除</el-checkbox>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="cancelDelete">取消</el-button>
      <el-button
        type="danger"
        :loading="deleting"
        :disabled="!deleteReason.trim() || !deleteConfirmed"
        @click="confirmDelete"
      >确认删除</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import personnelApi from '@/api/modules/personnel.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  personnel: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'deleted'])

const deleteReason = ref('')
const deleteConfirmed = ref(false)
const deleting = ref(false)

watch(() => props.modelValue, (visible) => {
  if (visible) {
    deleteReason.value = ''
    deleteConfirmed.value = false
  }
})

function cancelDelete() {
  emit('update:modelValue', false)
}

async function confirmDelete() {
  if (!deleteReason.value.trim() || !deleteConfirmed.value) return
  deleting.value = true
  try {
    await personnelApi.delete(props.personnel.id, deleteReason.value.trim())
    ElMessage.success('删除成功')
    emit('update:modelValue', false)
    emit('deleted')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  } finally {
    deleting.value = false
  }
}
</script>

<style scoped>
.delete-warning {
  color: var(--el-color-danger);
  background-color: #fef0f0;
  padding: 8px 12px;
  border-radius: 4px;
  margin: 12px 0;
  font-size: 13px;
  line-height: 1.5;
}
</style>
