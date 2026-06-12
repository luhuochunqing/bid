<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="批量导出人员"
    width="560px"
    :close-on-click-modal="false"
    @close="resetAll"
  >
    <div v-if="!task.taskId.value">
      <el-form :model="exportFilters" label-width="100px">
        <el-form-item label="姓名/工号">
          <el-input v-model="exportFilters.keyword" placeholder="留空导出全部" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="exportFilters.status" placeholder="全部" clearable style="width:100%">
            <el-option label="在职" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
            <el-option label="离职" value="TERMINATED" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="exportFilters.departmentCode" placeholder="留空不限制" clearable />
        </el-form-item>
        <el-form-item label="持有证书">
          <el-input v-model="exportFilters.certificateKeyword" placeholder="证书名称关键词" clearable />
        </el-form-item>
      </el-form>
      <div class="export-hint">
        <el-icon><InfoFilled /></el-icon>
        导出文件包含 Excel（人员+教育+证书三Sheet）及证书附件ZIP
      </div>
    </div>

    <div v-else-if="task.isProcessing.value" class="export-progress">
      <el-progress :percentage="task.progressPercent.value" :status="task.progressPercent.value === 100 ? 'success' : ''" />
      <p class="progress-text">{{ task.progressText.value }}</p>
    </div>

    <div v-else-if="task.isCompleted.value" class="export-result">
      <el-result icon="success" title="导出完成" :sub-title="`共导出 ${task.totalCount.value} 条记录`">
        <template #extra>
          <el-button type="primary" @click="downloadExportFile">下载导出文件</el-button>
          <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
        </template>
      </el-result>
    </div>

    <div v-else-if="task.isFailed.value" class="export-result">
      <el-result icon="error" title="导出失败" :sub-title="task.errorMessage.value">
        <template #extra>
          <el-button type="primary" @click="resetAll">重试</el-button>
        </template>
      </el-result>
    </div>

    <template #footer v-if="!task.taskId.value">
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="task.active.value" @click="handleStartExport">开始导出</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import personnelBatchApi from '@/api/modules/personnelBatchApi.js'
import { usePersonnelBatchTask } from './usePersonnelBatchTask.js'

defineProps({ modelValue: { type: Boolean, default: false } })

const exportFilters = reactive({ keyword: '', status: '', departmentCode: '', certificateKeyword: '' })

const task = usePersonnelBatchTask({
  startApi: (filters) => personnelBatchApi.startExport(filters),
  pollApi: (taskId) => personnelBatchApi.getExportProgress(taskId)
})

function resetAll() {
  Object.assign(exportFilters, { keyword: '', status: '', departmentCode: '', certificateKeyword: '' })
  task.reset()
}

async function handleStartExport() {
  await task.startTask({ ...exportFilters })
}

async function downloadExportFile() {
  try {
    await personnelBatchApi.downloadExportFile(task.taskId.value)
    ElMessage.success('导出文件下载成功')
  } catch {
    ElMessage.error('导出文件下载失败')
  }
}
</script>

<style scoped>
.export-hint { margin-top: 16px; padding: 10px 12px; background: var(--el-color-primary-light-9); border-radius: 4px; font-size: 13px; color: var(--el-color-primary); display: flex; align-items: center; gap: 6px; }
.export-progress { text-align: center; padding: 20px 0; }
.progress-text { margin-top: 12px; color: var(--el-text-color-secondary); font-size: 13px; }
.export-result { padding: 20px 0; }
</style>
