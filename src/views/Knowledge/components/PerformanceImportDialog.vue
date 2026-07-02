<template>
  <el-dialog
    :model-value="modelValue"
    title="批量导入业绩"
    width="640px"
    :close-on-click-modal="false"
    :before-close="handleClose"
    data-testid="performance-import-dialog"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-alert
      title="请按模板填写 Excel 上传；附件文件名需与模板中填写的文件名一致，附件包中的文件将自动匹配"
      :closable="false"
      type="info"
      show-icon
    />
    <div class="template-download" style="margin-top:12px">
      <el-link type="primary" :underline="false" @click="downloadTemplate">
        <el-icon><DocumentCopy /></el-icon> 下载导入模板
      </el-link>
    </div>
    <el-form label-width="120px" style="margin-top:12px">
      <el-form-item label="选择 Excel 文件" required>
        <el-upload
          :auto-upload="false"
          :limit="1"
          accept=".xlsx,.xls"
          :on-change="onImportFileChange"
          :on-remove="onImportFileRemove"
          drag
        >
          <el-icon class="upload-icon"><UploadFilled /></el-icon>
          <div class="el-upload__text">将 .xlsx 文件拖至此处，或<em>点击选择</em></div>
          <template #tip>
            <div class="el-upload__tip">仅支持 .xlsx，文件不超过 10MB</div>
          </template>
        </el-upload>
      </el-form-item>
      <el-form-item label="附件文件包（可选）">
        <el-upload
          :auto-upload="false"
          multiple
          :limit="100"
          :on-change="onAttachChange"
          :on-remove="onAttachRemove"
          drag
        >
          <el-icon class="upload-icon"><Files /></el-icon>
          <div class="el-upload__text">合同协议、商城截图、央企名录、关系证明、品类页、其他附件</div>
          <div class="el-upload__sub">文件名需与 Excel 中填写的附件文件名一致，系统将自动匹配</div>
          <template #tip>
            <div class="el-upload__tip">命名匹配成功后才归档；不匹配将忽略</div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>
    <el-alert
      v-if="importFile || attachFiles.length > 0"
      :title="`已选择：${importFile ? importFile.name : '未选 Excel'} | 附件 ${attachFiles.length} 个`"
      type="success"
      :closable="false"
      show-icon
      style="margin-top:8px"
    />
    <div v-if="importResult.failures.length > 0" class="import-failures">
      <div class="import-failures-title">失败明细（共 {{ importResult.failures.length }} 条，前 10 条）：</div>
      <div class="failure-table-wrapper">
        <table class="failure-table">
          <thead>
            <tr>
              <th style="width: 60px">行号</th>
              <th style="width: 120px">合同名称</th>
              <th>错误原因</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="f in importResult.failures.slice(0,10)" :key="f.rowNum">
              <td class="row-num">{{ f.rowNum }}</td>
              <td class="contract-name">{{ f.contractName || '-' }}</td>
              <td class="reason">{{ f.reason }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <div v-if="importResult.attachedCount > 0 || importResult.unmatchedFiles.length > 0" class="import-attach-report">
      <span>附件关联：成功 {{ importResult.attachedCount }} 个</span>
      <span v-if="importResult.unmatchedFiles.length > 0" style="color: var(--el-color-warning)"> | 未匹配 {{ importResult.unmatchedFiles.length }} 个</span>
    </div>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :disabled="!importFile" :loading="importLoading" @click="confirmImport">开始导入</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { DocumentCopy, UploadFilled, Files } from '@element-plus/icons-vue'
import { usePerformanceImport } from '@/composables/usePerformanceImport.js'

defineProps({
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'imported'])

const handleClose = () => emit('update:modelValue', false)
const {
  importFile, attachFiles, importLoading, importResult,
  downloadTemplate,
  onImportFileChange, onImportFileRemove,
  onAttachChange, onAttachRemove,
  confirmImport
} = usePerformanceImport(() => emit('imported'))
</script>

<style scoped>
.import-failures { text-align: left; color: var(--el-color-danger); font-size: 13px; max-height: 260px; overflow-y: auto; margin-top: 8px; }
.import-failures-title { font-weight: 600; margin-bottom: 8px; color: var(--el-color-danger); }
.failure-table-wrapper { border: 1px solid var(--el-border-color-lighter); border-radius: 4px; overflow: hidden; }
.failure-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.failure-table th { background: var(--el-color-danger-light-9); color: var(--el-color-danger); padding: 8px; text-align: left; font-weight: 600; border-bottom: 1px solid var(--el-border-color-lighter); }
.failure-table td { padding: 6px 8px; border-bottom: 1px solid var(--el-border-color-lighter); vertical-align: top; color: var(--el-text-color-primary); }
.failure-table tr:last-child td { border-bottom: none; }
.failure-table .row-num { font-weight: 600; color: var(--el-color-danger); }
.failure-table .contract-name { color: var(--el-text-color-regular); max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.failure-table .reason { color: var(--el-text-color-primary); line-height: 1.5; }
.import-attach-report { margin-top: 8px; font-size: 13px; color: var(--el-text-color-regular); }
.upload-icon { font-size: 40px; color: var(--el-color-primary); opacity: 0.7; margin-bottom: 8px; }
:deep(.el-upload__sub) { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 4px; }
</style>
