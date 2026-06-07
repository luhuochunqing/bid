<template>
  <el-card class="project-documents" shadow="never">
    <template #header>
      <div class="doc-header">
        <span class="doc-title">项目文档</span>
        <div class="doc-actions">
          <el-button size="small" @click="handleExport">导出</el-button>
          <el-button size="small" @click="handleUpload">上传</el-button>
        </div>
      </div>
    </template>
    <el-table :data="documents" stripe size="small" v-loading="loading" empty-text="暂无文档">
      <el-table-column label="文档名称" prop="name" min-width="200" />
      <el-table-column label="上传者" prop="uploaderName" width="120" />
      <el-table-column label="上传时间" width="160">
        <template #default="{ row }">{{ row.createdAt ? row.createdAt.slice(0, 16).replace('T', ' ') : '-' }}</template>
      </el-table-column>
      <el-table-column label="文档来源" width="120">
        <template #default="{ row }">{{ row.source || '自定义上传' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleDownload(row)">下载</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <input ref="fileInputRef" type="file" multiple style="display:none" @change="onFileSelected" />
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { projectsApi } from '@/api/modules/projects.js'
import httpClient from '@/api/client.js'
import { getApiUrl } from '@/api/config.js'

const props = defineProps({ projectId: { type: [String, Number], required: true } })
const emit = defineEmits(['export'])
const documents = ref([])
const loading = ref(false)
const fileInputRef = ref(null)

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
  await loadDocuments()
}

async function handleDownload(row) {
  const url = row.fileUrl || row.url
  if (!url) { ElMessage.info('文件地址不可用'); return }
  try {
    // Use authenticated download via httpClient to preserve JWT auth headers
    const resolvedUrl = url.startsWith('http') ? url : getApiUrl(url)
    const response = await httpClient.get(resolvedUrl, { responseType: 'blob' })
    const blob = new Blob([response.data])
    const blobUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = blobUrl
    a.download = row.name || 'download'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(blobUrl)
  } catch {
    // Fallback: if authenticated download fails, try direct window.open
    if (url.startsWith('http')) {
      window.open(url, '_blank')
    } else {
      ElMessage.error('文件下载失败')
    }
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.name}」？`, '删除确认', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    await projectsApi.deleteDocument(props.projectId, row.id)
    ElMessage.success('已删除')
    await loadDocuments()
  } catch { /* cancelled */ }
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
</style>
