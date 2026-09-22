<!-- Input: current user and submitted callback
Output: Workbench quick-start card with support and borrow request dialogs
Pos: src/views/Dashboard/components/ - Dashboard workflow components
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <QuickActions :actions="actions" @action-click="quickStart.handleQuickAction" />

  <SupportRequestDialog
    v-model="supportRequestDialogVisible"
    v-model:form="supportRequestForm"
    :projects="supportRequestProjects"
    :projects-error="supportProjectsError"
    :submitting="supportRequestSubmitting"
    @submit="quickStart.submitSupportRequest"
    @retry-projects="quickStart.loadSupportRequestProjects"
  />

  <el-dialog v-model="borrowDialogVisible" title="资质借阅申请" width="680px" destroy-on-close>
    <el-form :model="borrowForm" label-width="110px">
      <el-form-item label="关联项目" required>
        <el-select v-model="borrowForm.projectId" filterable placeholder="请选择投标项目" style="width: 100%">
          <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
        </el-select>
      </el-form-item>

      <el-form-item label="借阅资质" required>
        <el-select v-model="borrowForm.qualificationId" filterable placeholder="请选择资质" style="width: 100%">
          <el-option v-for="qualification in qualifications" :key="qualification.id" :label="qualification.name" :value="qualification.id" />
        </el-select>
      </el-form-item>

      <el-form-item label="申请人" required><el-input v-model="borrowForm.borrowerName" /></el-form-item>
      <el-form-item label="部门"><el-input v-model="borrowForm.borrowerDept" /></el-form-item>
      <el-form-item label="预计归还">
        <el-date-picker v-model="borrowForm.expectedReturnDate" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="用途" required><el-input v-model="borrowForm.purpose" type="textarea" :rows="3" /></el-form-item>
      <el-form-item label="备注"><el-input v-model="borrowForm.remark" type="textarea" :rows="2" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="borrowDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="borrowSubmitting" @click="quickStart.submitBorrow">提交申请</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, markRaw } from 'vue'
import { useRouter } from 'vue-router'
import { Document, FolderOpened } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useWorkbenchQuickStart } from '@/views/Dashboard/useWorkbenchQuickStart.js'
import QuickActions from './QuickActions.vue'
import SupportRequestDialog from './SupportRequestDialog.vue'

defineOptions({ name: 'WorkbenchQuickStart' })
const emit = defineEmits(['submitted'])
const router = useRouter()
const userStore = useUserStore()
const currentUser = computed(() => userStore.currentUser || {})
const Icons = markRaw({ Document, FolderOpened })

const quickStart = useWorkbenchQuickStart({
  currentUserRef: currentUser,
  message: ElMessage,
  onSubmitted: () => router.push('/project'),
})

const {
  supportRequestDialogVisible,
  supportRequestForm,
  supportRequestProjects,
  supportProjectsError,
  supportRequestSubmitting,
  borrowDialogVisible,
  borrowSubmitting,
  borrowForm,
  projects,
  qualifications,
} = quickStart

const actions = computed(() => [
  { key: 'support', title: '标书支持申请', desc: '申请技术/商务支持', icon: Icons.Document, iconStyle: 'background: linear-gradient(135deg, #DBEAFE 0%, #BFDBFE 100%); color: #1E40AF;' },
  { key: 'borrow', title: '资质借阅', desc: '资质借阅申请', icon: Icons.FolderOpened, iconStyle: 'background: linear-gradient(135deg, #D1FAE5 0%, #A7F3D0 100%); color: var(--color-success-dark);' },
])

defineExpose({ quickStart })
</script>
