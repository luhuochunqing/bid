<!-- 蓝图 §3.3.1.1 立项审批操作（投标管理员/组长） -->
<template>
  <el-card class="section-card" shadow="never">
    <template #header><span>审批操作（投标管理员/组长）</span></template>
    <el-form label-width="140px">
      <el-form-item label="投标负责人" required>
        <el-input v-model="form.primaryLeadUserId" placeholder="请输入投标负责人用户ID" />
      </el-form-item>
      <el-form-item label="投标辅助人员">
        <el-input v-model="form.auxiliaryUserIds" placeholder="请输入辅助人员用户ID（逗号分隔）" />
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

defineProps({ approving: { type: Boolean, default: false } })
defineEmits(['approve', 'show-reject'])

const form = reactive({ primaryLeadUserId: '', auxiliaryUserIds: '', reviewerNotes: '' })

function buildPayload() {
  const payload = {
    primaryLeadUserId: Number(form.primaryLeadUserId),
    reviewerNotes: form.reviewerNotes
  }
  if (form.auxiliaryUserIds) {
    payload.auxiliaryUserIds = form.auxiliaryUserIds.split(',').map(id => Number(id.trim())).filter(id => id)
  }
  return payload
}
</script>
<style scoped>
.approval-actions { display: flex; gap: 12px; padding: 8px 0; }
</style>
