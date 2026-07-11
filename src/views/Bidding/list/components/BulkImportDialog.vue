<template>
  <el-dialog
    v-model="modelValue"
    title="批量导入标讯"
    width="720px"
    :close-on-click-modal="false"
    @close="$emit('reset')"
  >
    <div class="bulk-import-tips">
      <p>· 仅支持 <strong>.xlsx</strong> 模板，单次最多 <strong>500</strong> 行，文件大小不超过 <strong>5MB</strong>。</p>
      <p>· 请先点击「下载批量导入模板」获取最新模板，按字典参考填写后再上传。</p>
      <p>· 总部所在地请按字典参考填写一级+二级拼接格式（如 <strong>广东省深圳市</strong>、<strong>北京市北京市</strong>），勿使用连字符分隔。</p>
      <p>· 导入为异步处理：提交后可在下方查看实时进度，处理期间可关闭对话框，进度不会丢失。</p>
    </div>

    <el-upload
      class="bulk-import-upload"
      drag
      :auto-upload="false"
      :show-file-list="false"
      :accept="accept"
      :on-change="onFileChange"
      :disabled="polling"
    >
      <el-icon class="el-icon--upload"><Upload /></el-icon>
      <div class="el-upload__text">
        {{ selectedFile ? selectedFile.name : '将 .xlsx 文件拖到此处，或点击选择文件' }}
      </div>
    </el-upload>

    <!-- 异步进度区域：轮询中或终态时显示 -->
    <div v-if="polling || result" class="bulk-import-progress">
      <!-- 进度条（轮询中 + 非终态） -->
      <template v-if="polling && importProgress">
        <div class="progress-header">
          <span class="progress-status">
            <el-icon class="is-loading"><Loading /></el-icon>
            正在导入... {{ importProgress.status === 'PENDING' ? '排队中' : '处理中' }}
          </span>
          <span class="progress-counts">
            已处理 {{ importProgress.processedRows }} / {{ importProgress.totalRows }} 行
            （成功 {{ importProgress.successCount }}，失败 {{ importProgress.failureCount }}）
          </span>
        </div>
        <el-progress
          :percentage="importProgress.percent || 0"
          :status="importProgress.failureCount > 0 ? 'warning' : null"
          :stroke-width="10"
          :striped="true"
          :striped-flow="true"
        />
      </template>

      <!-- 仅排队中（PENDING），尚未开始处理 -->
      <template v-else-if="polling && !importProgress">
        <div class="progress-header">
          <span class="progress-status">
            <el-icon class="is-loading"><Loading /></el-icon>
            导入任务已创建，等待处理...
          </span>
        </div>
        <el-progress :percentage="0" :stroke-width="10" :striped="true" :striped-flow="true" />
      </template>

      <!-- 终态结果 -->
      <template v-else-if="result">
        <el-alert
          v-if="result.status === 'COMPLETED'"
          type="success"
          :closable="false"
          :title="`共 ${result.totalRows} 行全部导入成功`"
          show-icon
        />
        <el-alert
          v-else-if="result.status === 'PARTIAL_SUCCESS'"
          type="warning"
          :closable="false"
          :title="`导入完成（部分成功）：共 ${result.totalRows} 行，成功 ${result.successCount} 行，失败 ${result.failureCount} 行`"
          description="成功的标讯已写入数据库，失败行请在下方表格逐行修正后重新上传。"
          show-icon
        />
        <el-alert
          v-else
          type="error"
          :closable="false"
          :title="`导入失败：共 ${result.totalRows} 行，失败 ${result.failureCount} 行（未写入任何数据）`"
          description="请按下方表格逐行修正 Excel 后重新上传；如反复失败建议重新下载模板对照字段格式。"
          show-icon
        />
        <el-table
          v-if="result.failureCount > 0 && result.errors?.length"
          :data="result.errors"
          size="small"
          class="bulk-import-error-table"
          max-height="320"
        >
          <el-table-column prop="rowNumber" label="行号" width="80" />
          <el-table-column prop="field" label="错误类型" width="160" :formatter="formatField" />
          <el-table-column prop="tenderTitle" label="标讯标题" width="180" show-overflow-tooltip />
          <el-table-column prop="errorMessage" label="错误说明" show-overflow-tooltip />
        </el-table>
      </template>
    </div>

    <template #footer>
      <el-button @click="modelValue = false" :disabled="polling">取消</el-button>
      <el-button :loading="templateDownloading" :disabled="polling" @click="$emit('download-template')">
        <el-icon><Download /></el-icon>
        下载批量导入模板
      </el-button>
      <el-button
        type="primary"
        :loading="importing"
        :disabled="!selectedFile || polling"
        @click="$emit('submit')"
      >
        {{ polling ? '导入进行中' : '开始导入' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { Download, Upload, Loading } from '@element-plus/icons-vue'

const modelValue = defineModel({ type: Boolean, default: false })

defineProps({
  selectedFile: { type: Object, default: null },
  // 终态结果（TenderImportProgressDTO，含 status/totalRows/successCount/failureCount/errors）
  result: { type: Object, default: null },
  // 实时进度（TenderImportProgressDTO，轮询中持续更新）
  importProgress: { type: Object, default: null },
  // 是否正在轮询（异步处理中）
  polling: { type: Boolean, default: false },
  templateDownloading: { type: Boolean, default: false },
  importing: { type: Boolean, default: false },
  accept: { type: String, default: '.xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' },
})

const emit = defineEmits(['reset', 'download-template', 'submit', 'file-change'])

const onFileChange = (file) => emit('file-change', file)

// 后端 TenderImportTaskError.field 英文标识 → 中文标签
const FIELD_LABELS = {
  duplicate: '标讯重复(三字段一致)',
  row: '行数据错误',
  file: '文件错误',
  purchaserName: '采购人字段错误',
  projectNo: '项目编号错误',
  title: '标讯标题错误',
  deadline: '截止时间错误',
}
const formatField = (_row, _column, cellValue) => FIELD_LABELS[cellValue] || cellValue || '-'
</script>

<style scoped>
.bulk-import-tips {
  margin-bottom: 12px;
  padding: 12px 16px;
  background: var(--bg-subtle);
  border-radius: 6px;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.6;
}

.bulk-import-tips p {
  margin: 0;
}

.bulk-import-upload {
  width: 100%;
}

.bulk-import-upload :deep(.el-upload),
.bulk-import-upload :deep(.el-upload-dragger) {
  width: 100%;
  box-sizing: border-box;
}

.bulk-import-progress {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 16px;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #4b5563;
}

.progress-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
  color: var(--el-color-primary);
}

.progress-counts {
  color: #6b7280;
}

.bulk-import-error-table {
  width: 100%;
}
</style>
