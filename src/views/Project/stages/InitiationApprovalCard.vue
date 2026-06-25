<!-- 蓝图 §3.3.1.1 立项审批操作（投标管理员/组长） -->
<template>
  <el-card class="section-card" shadow="never">
    <template #header><span>审批操作（投标管理员/组长）</span></template>
    <el-form label-width="140px">
      <el-form-item label="投标负责人" required>
        <UserPicker
          v-model="form.primaryLeadUserId"
          placeholder="搜索投标负责人（姓名/工号/拼音）"
        />
      </el-form-item>
      <el-form-item label="投标辅助人员">
        <UserPicker
          v-model="form.auxiliaryUserIds"
          multiple
          placeholder="搜索投标辅助人员（姓名/工号/拼音）"
        />
      </el-form-item>
      <el-form-item label="审核备注">
        <el-input v-model="form.reviewerNotes" type="textarea" :rows="2" />
      </el-form-item>
      <div class="approval-actions">
        <el-button type="success" :loading="approving" @click="$emit('approve', buildPayload())">审核通过</el-button>
        <el-button type="danger" @click="$emit('show-reject')">驳回</el-button>
      </div>
    </el-form>
  </el-card>
</template>
<script setup>
import { reactive } from 'vue'
import UserPicker from '@/components/common/UserPicker.vue'

defineProps({ approving: { type: Boolean, default: false } })
defineEmits(['approve', 'show-reject'])

const form = reactive({ primaryLeadUserId: null, auxiliaryUserIds: [], reviewerNotes: '' })

function buildPayload() {
  const payload = {
    primaryLeadUserId: Number(form.primaryLeadUserId),
    reviewerNotes: form.reviewerNotes
  }
  if (form.auxiliaryUserIds?.length > 0) {
    payload.auxiliaryUserIds = form.auxiliaryUserIds.map(id => Number(id))
  }
  return payload
}
</script>
<style scoped>
.approval-actions { display: flex; gap: 12px; padding: 8px 0; }
</style>
