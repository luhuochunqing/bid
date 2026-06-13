<template>
  <el-drawer v-model="localVisible" title="档案详情" size="900px" destroy-on-close>
    <div v-if="archive" class="drawer-container" v-loading="drawerLoading">
      <div class="section-title">基础信息</div>
      <el-descriptions :column="2" border class="project-desc">
        <el-descriptions-item label="项目名称">{{ archive.projectName }}</el-descriptions-item>
        <el-descriptions-item label="招标主体">{{ fullDetail?.tenderAgency || '-' }}</el-descriptions-item>
        <el-descriptions-item label="项目类型">
          <el-tag>{{ getProjectTypeLabel(archive.projectType) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="项目状态">
          <el-tag :type="getStatusTagType(archive.projectStatus)">{{ getStatusLabel(archive.projectStatus) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="中标结果">
          <el-tag :type="getBidResultTagType(archive.bidResult)">{{ getBidResultLabel(archive.bidResult) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="项目负责人">{{ archive.projectManager || '-' }}</el-descriptions-item>
        <el-descriptions-item label="投标负责人">{{ archive.bidManager || '-' }}</el-descriptions-item>
        <el-descriptions-item label="立项日期">{{ formatDateTime(fullDetail?.initiatedAt) }}</el-descriptions-item>
        <el-descriptions-item label="标书提交日期">{{ formatDateTime(fullDetail?.bidSubmissionAt) }}</el-descriptions-item>
        <el-descriptions-item label="开标日期">{{ formatDateTime(fullDetail?.bidOpeningAt) }}</el-descriptions-item>
        <el-descriptions-item label="结项日期">{{ formatDateTime(fullDetail?.closedAt) }}</el-descriptions-item>
      </el-descriptions>

      <ArchiveFileTable
        :files="detailFiles"
        @preview="(f) => emit('preview-file', f)"
        @download="(f) => emit('download-file', f)"
        @download-package="handleDownloadPackage"
      />

      <div class="section-title mt-6">操作日志</div>
      <ArchiveAuditLogTimeline :logs="detailLogs" />
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import httpClient from '@/api/client.js'
import { getStatusLabel, getStatusTagType, formatDateTime } from '../archiveLabels.js'
import ArchiveFileTable from './ArchiveFileTable.vue'
import ArchiveAuditLogTimeline from './ArchiveAuditLogTimeline.vue'

const props = defineProps({
  archive: { type: Object, default: null },
  visible: { type: Boolean, default: false }
})

const emit = defineEmits(['update:visible', 'preview-file', 'download-file'])

const localVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v)
})

const drawerLoading = ref(false)
const detailFiles = ref([])
const detailLogs = ref([])
const fullDetail = ref(null)

async function fetchDetail() {
  if (!props.archive) return
  drawerLoading.value = true
  detailFiles.value = []
  detailLogs.value = []
  fullDetail.value = null
  try {
    const res = await httpClient.get(`/api/archive/${props.archive.archiveId}`)
    fullDetail.value = res
    detailFiles.value = Array.isArray(res.files) ? res.files : []
    detailLogs.value = Array.isArray(res.logs) ? res.logs : []
  } catch (e) {
    detailFiles.value = []
    detailLogs.value = []
    fullDetail.value = null
    ElMessage.warn('未能获取完整归档详情')
  } finally {
    drawerLoading.value = false
  }
}

watch(() => props.archive, fetchDetail, { immediate: true })

defineExpose({ fetchDetail })

const getProjectTypeLabel = (type) => {
  const map = { OFFICE: '办公', COMPREHENSIVE: '综合', CENTRALIZED: '集采', INDUSTRIAL: '工业品', OTHER: '其他' }
  return map[type] || type || '-'
}

const getBidResultLabel = (result) => {
  const map = { WON: '已中标', LOST: '未中标', FAILED: '流标', ABANDONED: '弃标', IN_PROGRESS: '进行中', OTHER: '其他' }
  return map[result] || result || '-'
}

const getBidResultTagType = (result) => {
  if (result === 'WON') return 'success'
  if (result === 'LOST') return 'danger'
  if (result === 'FAILED') return 'warning'
  return 'info'
}

const handleDownloadPackage = async () => {
  if (!props.archive || !props.archive.projectId) {
    ElMessage.warning('当前档案信息不完整，无法下载文件包')
    return
  }
  try {
    const res = await httpClient.get(`/api/archive/export-zip/${props.archive.projectId}`, { responseType: 'blob' })
    const link = document.createElement('a')
    link.href = window.URL.createObjectURL(new Blob([res], { type: 'application/zip' }))
    link.download = `项目档案文件包-${new Date().toISOString().replace(/[-:T]/g, '').slice(0, 12)}.zip`
    link.click()
    window.URL.revokeObjectURL(link.href)
    ElMessage.success('导出文件包成功')
  } catch (e) {
    ElMessage.error('导出文件包失败：' + (e?.response?.data?.msg || e?.message || '未知错误'))
  }
}
</script>

<style scoped lang="scss">
.drawer-container {
  padding: 0 16px 24px 16px;
  height: 100%;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 12px;
  border-left: 4px solid var(--el-color-primary);
  padding-left: 8px;
}

.project-desc {
  margin-bottom: 20px;
}

.mt-6 {
  margin-top: 24px;
}
</style>
