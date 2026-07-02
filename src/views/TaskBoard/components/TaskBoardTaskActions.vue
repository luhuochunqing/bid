<template>
  <div v-if="canSubmitDeliverable" class="card-actions" @click.stop>
    <el-button
      size="small"
      data-testid="deliverable-upload-btn"
      :disabled="!isTaskAssignee(item)"
      @click="openDeliverableUpload(item)"
    >交付物上传</el-button>
    <el-button
      size="small"
      type="primary"
      data-testid="submit-task-btn"
      :disabled="!isTaskAssignee(item) || !hasDeliverable(item)"
      @click="openSubmitDialog(item)"
    >提交</el-button>
  </div>

  <!-- 交付物上传 + 提交对话框 -->
  <el-dialog
    v-model="showSubmitDialog"
    :title="'提交任务 - ' + (submittingTask?.title || '')"
    width="480px"
    :close-on-click-modal="false"
    append-to-body
  >
    <el-form label-width="100px">
      <el-form-item label="交付物" required>
        <el-upload
          ref="deliverableUploadRef"
          :auto-upload="false"
          :file-list="deliverableFileList"
          :limit="1"
          accept=".pdf,.doc,.docx,.xlsx,.jpg,.png"
        >
          <el-button size="small">选择文件</el-button>
          <template #tip>
            <span style="font-size: 11px; color: #909399">上传交付物（PDF/Word/Excel/图片）</span>
          </template>
        </el-upload>
      </el-form-item>
      <el-form-item label="完成情况说明" required>
        <el-input
          v-model="submitNotes"
          type="textarea"
          :rows="3"
          placeholder="请填写完成情况说明"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showSubmitDialog = false">取消</el-button>
      <el-button type="primary" :loading="submittingTaskLoading" @click="confirmSubmit">提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { useTaskActions } from '@/composables/useTaskActions.js'
import { isTaskActionable } from '@/constants/taskStatus.js'

const props = defineProps({
  item: { type: Object, required: true },
})
const emit = defineEmits(['deliverable-changed'])

const {
  hasDeliverable,
  isTaskAssignee,
  showSubmitDialog,
  submittingTask,
  submittingTaskLoading,
  deliverableFileList,
  deliverableUploadRef,
  submitNotes,
  openDeliverableUpload,
  openSubmitDialog,
  confirmSubmit,
} = useTaskActions({
  getProjectId: (task) => task?.projectId,
  onSubmitted: async (task) => {
    emit('deliverable-changed', task)
  },
})

const canSubmitDeliverable = computed(() =>
  props.item.type === 'TASK' && isTaskActionable(props.item.status)
)
</script>

<style scoped lang="scss">
.card-actions {
  margin-top: 8px;
  display: flex;
  gap: 6px;
  justify-content: flex-end;
}
</style>
