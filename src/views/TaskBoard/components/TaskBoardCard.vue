<template>
  <div
    class="task-card"
    :class="{ 'task-high': item.priority === 'HIGH', 'task-review': item.type === 'BID_REVIEW' }"
  >
    <div class="task-header">
      <div class="header-tags">
        <el-tag v-if="item.type" :type="item.type === 'BID_REVIEW' ? 'danger' : 'info'" size="small" class="type-tag">
          {{ item.type === 'BID_REVIEW' ? '标书审核' : '任务' }}
        </el-tag>
        <el-tag v-if="item.priority" :type="priorityType" size="small">
          {{ priorityText }}
        </el-tag>
        <el-tag v-if="item.deliverables?.length" type="success" size="small">交付物 {{ item.deliverables.length }}</el-tag>
      </div>
      <el-dropdown v-if="canUpdate" trigger="click" @click.stop>
        <el-icon class="more-icon"><MoreFilled /></el-icon>
        <template #dropdown>
          <template v-if="item.type === 'BID_REVIEW'">
            <el-dropdown-item @click="$emit('approve-bid', item)">
              <el-icon><Select /></el-icon>
              通过审核
            </el-dropdown-item>
            <el-dropdown-item divided @click="$emit('reject-bid', item)">
              <el-icon><CloseBold /></el-icon>
              驳回审核
            </el-dropdown-item>
          </template>
          <template v-else>
            <el-dropdown-item
              v-for="s in availableStatuses"
              :key="s.code"
              :disabled="item.status === s.code"
              @click="$emit('status-change', item, s.code)"
            >
              设为{{ s.name }}
            </el-dropdown-item>
            <el-dropdown-item divided @click="openUploadDialog">
              <el-icon><Upload /></el-icon>
              上传交付物
            </el-dropdown-item>
          </template>
        </template>
      </el-dropdown>
    </div>
    <div class="task-name">{{ item.title }}</div>
    <div class="task-desc" v-if="item.description">{{ item.description }}</div>
    <div class="task-meta">
      <div class="task-owner" v-if="item.assigneeName || item.submitterName">
        <el-icon><User /></el-icon>
        <span>{{ item.assigneeName || item.submitterName }}</span>
      </div>
      <div class="task-deadline" :class="{ 'deadline-urgent': isUrgent }" v-if="item.dueDate">
        <el-icon><Calendar /></el-icon>
        <span>{{ formattedDate }}</span>
      </div>
    </div>
    <div class="task-project" v-if="item.projectName">
      <el-icon><OfficeBuilding /></el-icon>
      <span>{{ item.projectName }}</span>
    </div>
    <div v-if="item.deliverables?.length" class="deliverables">
      <div class="deliverable-title">交付物:</div>
      <div class="deliverable-list">
        <el-tag
          v-for="del in item.deliverables"
          :key="del.id"
          size="small"
          closable
          @close="handleRemoveDeliverable(del)"
        >
          <el-link :href="del.url" target="_blank" type="primary" @click.stop>
            <el-icon><Document /></el-icon>
            {{ del.name }}
          </el-link>
        </el-tag>
      </div>
    </div>

    <el-dialog v-model="uploadDialogVisible" title="上传交付物" width="420px" append-to-body>
      <el-form :model="uploadForm" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="uploadForm.name" placeholder="请输入交付物名称" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="uploadForm.type" placeholder="请选择类型">
            <el-option label="文档" value="document" />
            <el-option label="资质" value="qualification" />
            <el-option label="技术" value="technical" />
            <el-option label="报价" value="quotation" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件">
          <el-upload
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :file-list="uploadFileList"
          >
            <el-button size="small">选择文件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleSaveDeliverable">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled, User, Calendar, OfficeBuilding, Upload, Document, Select, CloseBold } from '@element-plus/icons-vue'
import { projectsApi } from '@/api/modules/projects'

const props = defineProps({
  item: { type: Object, required: true },
  availableStatuses: { type: Array, required: true }
})

const emit = defineEmits(['status-change', 'deliverable-changed', 'approve-bid', 'reject-bid'])

const PRIORITY_TYPE_MAP = { HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }
const PRIORITY_TEXT_MAP = { HIGH: '高', MEDIUM: '中', LOW: '低' }

const priorityType = computed(() => PRIORITY_TYPE_MAP[props.item.priority] || 'info')
const priorityText = computed(() => PRIORITY_TEXT_MAP[props.item.priority] || props.item.priority)
const canUpdate = computed(() => !!props.item.id && (props.item.type === 'TASK' || props.item.type === 'BID_REVIEW'))

const isUrgent = computed(() => {
  if (!props.item.dueDate) return false
  const diff = new Date(props.item.dueDate) - new Date()
  return diff > 0 && diff < 3 * 24 * 60 * 60 * 1000
})

const formattedDate = computed(() => {
  const d = new Date(props.item.dueDate)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})

// 交付物上传
const uploadDialogVisible = ref(false)
const uploading = ref(false)
const uploadForm = ref({ name: '', type: 'document' })
const uploadFileList = ref([])

const openUploadDialog = () => {
  uploadForm.value = { name: '', type: 'document' }
  uploadFileList.value = []
  uploadDialogVisible.value = true
}

const handleFileChange = (file) => {
  uploadForm.value.file = file.raw
  if (!uploadForm.value.name) {
    uploadForm.value.name = file.name
  }
}

const handleFileRemove = () => {
  uploadForm.value.file = null
}

const TYPE_MAP = {
  document: 'DOCUMENT',
  qualification: 'QUALIFICATION',
  technical: 'TECHNICAL',
  quotation: 'QUOTATION',
  other: 'OTHER'
}

const handleSaveDeliverable = async () => {
  if (!uploadForm.value.name) {
    ElMessage.warning('请填写交付物名称')
    return
  }
  if (!uploadForm.value.file) {
    ElMessage.warning('请选择文件')
    return
  }
  uploading.value = true
  try {
    const task = props.item
    const file = uploadForm.value.file
    const formData = new FormData()
    formData.append('file', file)
    formData.append('taskId', task.id)
    formData.append('category', 'TASK_DELIVERABLE')
    const uploadResult = await projectsApi.uploadDocument(task.projectId, formData)
    if (!uploadResult?.success || !uploadResult?.data) {
      throw new Error(uploadResult?.message || '文件上传失败')
    }
    const uploadedDoc = uploadResult.data
    const payload = {
      name: uploadForm.value.name,
      deliverableType: TYPE_MAP[uploadForm.value.type] || 'DOCUMENT',
      size: uploadedDoc?.size || `${(file.size / 1024).toFixed(1)}KB`,
      fileType: uploadedDoc?.fileType || file.type || null,
      url: uploadedDoc?.fileUrl || null
    }
    await projectsApi.createTaskDeliverable(task.projectId, task.id, payload)
    uploadDialogVisible.value = false
    ElMessage.success('交付物已保存')
    emit('deliverable-changed', task)
  } catch (e) {
    ElMessage.error(e?.message || '交付物上传失败')
  } finally {
    uploading.value = false
  }
}

const handleRemoveDeliverable = async (deliverable) => {
  try {
    await ElMessageBox.confirm(`确定删除交付物「${deliverable.name}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await projectsApi.deleteTaskDeliverable(props.item.projectId, props.item.id, deliverable.id)
    ElMessage.success('交付物已删除')
    emit('deliverable-changed', props.item)
  } catch (e) {
    ElMessage.error(e?.message || '删除交付物失败')
  }
}
</script>

<style scoped lang="scss">
.task-card {
  background: #fff;
  border-radius: 6px;
  padding: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  transition: box-shadow 0.2s ease;
  &:hover { box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12); }
  &.task-high { border-left: 3px solid #f56c6c; }
  &.task-review { border-left: 3px solid #e6a23c; }
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  .header-tags { display: flex; align-items: center; gap: 6px; }
  .type-tag { flex-shrink: 0; }
  .more-icon { cursor: pointer; color: #909399; &:hover { color: #409eff; } }
}

.task-name { font-size: 14px; font-weight: 500; margin-bottom: 6px; color: #303133; }

.task-desc {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #606266;
  .task-owner, .task-deadline { display: flex; align-items: center; gap: 4px; }
  .deadline-urgent { color: #f56c6c; }
}

.task-project {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  .el-icon { flex-shrink: 0; }
}

.deliverables { margin-top: 8px; padding-top: 8px; border-top: 1px dashed #dcdfe6; }
.deliverable-title { font-size: 12px; color: #909399; margin-bottom: 6px; }
.deliverable-list { display: flex; flex-wrap: wrap; gap: 6px; }
</style>
