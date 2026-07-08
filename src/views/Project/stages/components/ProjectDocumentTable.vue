<template>
  <el-card class="project-documents" shadow="never">
    <template #header>
      <div class="doc-header">
        <span class="doc-title">项目文档</span>
        <div v-if="!readonly" class="doc-actions">
          <el-button size="small" @click="handleExport">导出</el-button>
          <el-button size="small" @click="handleUpload">上传</el-button>
        </div>
      </div>
    </template>
    <el-table :data="pagedDocuments" stripe size="small" v-loading="loading" empty-text="暂无文档">
      <el-table-column label="文档名称" min-width="200">
        <template #default="{ row, $index }">
          {{ (currentPage - 1) * pageSize + $index + 1 }}. {{ row.name }}
        </template>
      </el-table-column>
      <el-table-column label="上传者" prop="uploader" width="120" />
      <el-table-column label="上传时间" width="160">
        <template #default="{ row }">{{ row.createdAt ? row.createdAt.slice(0, 16).replace('T', ' ') : '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleDownload(row)">下载</el-button>
          <el-button v-if="!readonly" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-if="documents.length > pageSize"
      v-model:current-page="currentPage"
      :page-size="pageSize"
      :total="documents.length"
      layout="prev, pager, next"
      size="small"
      class="doc-pagination"
    />
    <input v-if="!readonly" ref="fileInputRef" type="file" multiple style="display:none" @change="onFileSelected" />
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { projectsApi } from '@/api/modules/projects.js'
import { downloadWithFilename } from '@/utils/download.js'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  readonly: { type: Boolean, default: false }
})
const emit = defineEmits(['export'])
const documents = ref([])
const loading = ref(false)
const fileInputRef = ref(null)
const currentPage = ref(1)
const pageSize = 5

const pagedDocuments = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return documents.value.slice(start, start + pageSize)
})

async function loadDocuments() {
  loading.value = true
  try {
    const r = await projectsApi.getDocuments(props.projectId)
    documents.value = Array.isArray(r?.data) ? r.data : []
  } catch { documents.value = [] }
  finally { loading.value = false }
}

function handleUpload() { fileInputRef.value?.click() }

async function onFileSelected(e) {
  const files = e.target?.files
  if (!files?.length) return
  for (const file of files) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('name', file.name)
    try {
      await projectsApi.uploadDocument(props.projectId, formData)
      ElMessage.success(`${file.name} 上传成功`)
    } catch { ElMessage.error(`${file.name} 上传失败`) }
  }
  if (fileInputRef.value) fileInputRef.value.value = ''
  currentPage.value = 1
  await loadDocuments()
}

async function handleDownload(row) {
  const downloadUrl = projectsApi.getDocumentDownloadUrl(props.projectId, row.id)
  if (!downloadUrl) {
    ElMessage.info('文件地址不可用')
    return
  }
  try {
    await downloadWithFilename(downloadUrl, row.name || 'download')
  } catch {
    ElMessage.error('文件下载失败')
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.name}」？`, '删除确认', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    await projectsApi.deleteDocument(props.projectId, row.id)
    ElMessage.success('已删除')
    await loadDocuments()
    const maxPage = Math.max(1, Math.ceil(documents.value.length / pageSize))
    if (currentPage.value > maxPage) {
      currentPage.value = maxPage
    }
  } catch (error) {
    // CO-487: 区分用户取消和后端错误，展示友好提示（如结项不可删除）
    if (error === 'cancel' || error?.toString?.()?.includes('cancel')) return
    const msg = error?.response?.data?.msg || error?.message
    if (msg) ElMessage.error(msg)
  }
}

function handleExport() { emit('export') }

onMounted(loadDocuments)
</script>
<style scoped>
.doc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.doc-title {
  font-weight: 600;
  font-size: 15px;
}
.doc-actions {
  display: flex;
  gap: 8px;
}
.doc-pagination {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
