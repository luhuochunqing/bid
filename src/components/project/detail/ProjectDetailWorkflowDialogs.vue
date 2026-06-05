<template>
  <el-dialog v-model="ctx.processDialogVisible" title="标书编制流程" width="900px"><el-tabs v-model="ctx.activeProcessTab" type="border-card"><el-tab-pane label="初稿编制" name="draft"><el-form :model="ctx.draftForm" label-width="120px"><el-form-item label="编制人"><span>{{ ctx.draftForm.preparer || ctx.userStore.userName }}</span></el-form-item><el-form-item label="使用模板"><el-select v-model="ctx.draftForm.templateId" class="template-select"><el-option v-for="tpl in ctx.templates" :key="tpl.id" :label="tpl.name" :value="tpl.id" /></el-select></el-form-item><el-form-item label="备注说明"><el-input v-model="ctx.draftForm.remark" type="textarea" :rows="3" /></el-form-item></el-form><div class="dialog-footer"><el-button @click="ctx.processDialogVisible = false">取消</el-button><el-button type="primary" @click="ctx.handleSaveDraft">保存初稿</el-button></div></el-tab-pane><el-tab-pane label="内部评审" name="review"><div class="review-section"><div class="section-header"><span>评审人员</span><el-button type="primary" size="small" :icon="Plus" @click="ctx.handleAddReviewer">添加评审人</el-button></div><el-table :data="ctx.reviewers" border><el-table-column prop="name" label="评审人" width="120" /><el-table-column prop="role" label="角色" width="120"><template #default="{ row }"><el-tag size="small" :type="ctx.getReviewerRoleType(row.role)">{{ ctx.getReviewerRoleText(row.role) }}</el-tag></template></el-table-column><el-table-column prop="status" label="评审状态" width="100"><template #default="{ row }"><el-tag :type="ctx.getReviewStatusType(row.status)" size="small">{{ ctx.getReviewStatusText(row.status) }}</el-tag></template></el-table-column></el-table></div><div class="dialog-footer"><el-button @click="ctx.processDialogVisible = false">关闭</el-button><el-button type="primary" @click="ctx.handleCompleteReview" :disabled="!ctx.canCompleteReview()">完成评审</el-button></div></el-tab-pane></el-tabs></el-dialog>
  <el-dialog v-model="ctx.reviewerDialogVisible" title="添加评审人" width="500px"><el-form :model="ctx.reviewerForm" label-width="100px"><el-form-item label="评审人" required><el-select v-model="ctx.reviewerForm.userId" style="width: 100%;"><el-option v-for="user in ctx.availableReviewers" :key="user.id" :label="user.name" :value="user.id" /></el-select></el-form-item><el-form-item label="评审角色" required><el-select v-model="ctx.reviewerForm.role" style="width: 100%;"><el-option label="技术评审" value="tech" /><el-option label="商务评审" value="business" /></el-select></el-form-item></el-form><template #footer><el-button @click="ctx.reviewerDialogVisible = false">取消</el-button><el-button type="primary" @click="ctx.handleConfirmAddReviewer">确定</el-button></template></el-dialog>
</template>

<script setup>
import { Plus } from '@element-plus/icons-vue'
import { useProjectDetailContext } from '@/composables/projectDetail/context.js'

const ctx = useProjectDetailContext()
</script>

<style scoped>
.template-select { width: 300px; }
</style>
