<template>
  <div class="card-actions" @click.stop>
    <el-button
      size="small"
      type="danger"
      plain
      data-testid="reject-bid-btn"
      :disabled="!isBidReviewer(item)"
      @click="openRejectDialog(item)"
    >驳回</el-button>
    <el-button
      size="small"
      type="success"
      data-testid="approve-bid-btn"
      :disabled="!isBidReviewer(item)"
      @click="handleApproveBid(item)"
    >通过审核</el-button>
  </div>

  <!-- 驳回原因对话框 -->
  <el-dialog
    v-model="showRejectDialog"
    title="驳回标书审核"
    width="420px"
    :close-on-click-modal="false"
    append-to-body
  >
    <el-form label-width="0">
      <el-form-item :label="'驳回：' + (rejectingItem?.title || '')" />
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="3"
        placeholder="请填写驳回原因"
      />
    </el-form>
    <template #footer>
      <el-button @click="showRejectDialog = false">取消</el-button>
      <el-button type="danger" :loading="rejectingLoading" @click="confirmReject">确认驳回</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'
import { useUserStore } from '@/stores/user.js'

defineProps({
  item: { type: Object, required: true },
})
const emit = defineEmits(['deliverable-changed'])

const userStore = useUserStore()

function matchesCurrentUser(id) {
  const uid = userStore?.currentUser?.id
  return uid != null && id != null && String(uid) === String(id)
}

function isBidReviewer(item) {
  return matchesCurrentUser(item?.reviewerId)
}

async function handleApproveBid(item) {
  try {
    await projectLifecycleApi.approveBid(item.projectId, { comment: '' })
    ElMessage.success('审核已通过')
    emit('deliverable-changed', item)
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '审核通过失败')
  }
}

const showRejectDialog = ref(false)
const rejectingItem = ref(null)
const rejectReason = ref('')
const rejectingLoading = ref(false)

function openRejectDialog(item) {
  rejectingItem.value = item
  rejectReason.value = ''
  showRejectDialog.value = true
}

async function confirmReject() {
  if (!rejectReason.value.trim()) return ElMessage.warning('请填写驳回原因')
  if (!rejectingItem.value) return
  rejectingLoading.value = true
  try {
    await projectLifecycleApi.rejectBid(rejectingItem.value.projectId, { comment: rejectReason.value.trim() })
    ElMessage.success('已驳回')
    showRejectDialog.value = false
    emit('deliverable-changed', rejectingItem.value)
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '驳回失败')
  } finally {
    rejectingLoading.value = false
    rejectingItem.value = null
  }
}
</script>

<style scoped lang="scss">
.card-actions {
  margin-top: 8px;
  display: flex;
  gap: 6px;
  justify-content: flex-end;
}
</style>
