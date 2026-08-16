<template>
  <el-dialog v-model="visible" title="解析招标文件" width="560px" destroy-on-close>
    <el-upload
      drag
      :show-file-list="false"
      :before-upload="handleUpload"
      :disabled="isParsing"
      accept=".doc,.docx,.pdf"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">将招标文件拖到此处，或<em>点击选择</em></div>
      <template #tip>
        <div class="el-upload__tip">支持 doc、docx、文本型 pdf。解析后可用于拆解任务和评分标准解析。</div>
      </template>
    </el-upload>

    <ObsUploadProgress
      :visible="obsUploading || obsProgressPercent > 0"
      :file-name="obsCurrentFile?.name || ''"
      :file-size="obsCurrentFile?.size || 0"
      :progress-percent="obsProgressPercent"
      :uploading="obsUploading"
      :has-error="!!obsError"
      @cancel="handleObsCancel"
    />

    <el-alert
      v-if="isParsing"
      title="正在解析招标文件，请稍候"
      type="info"
      show-icon
      :closable="false"
      class="parse-alert"
    />

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'
import ObsUploadProgress from '@/components/common/ObsUploadProgress.vue'
import { useProjectDetailContext } from '@/composables/projectDetail/context.js'

const ctx = useProjectDetailContext()

const visible = computed({
  get: () => ctx.tenderBreakdownDialogVisible,
  set: (value) => { ctx.tenderBreakdownDialogVisible = value },
})

const isParsing = computed(() => Boolean(ctx.tenderBreakdownParsing))

const handleUpload = (file) => ctx.handleTenderBreakdownUpload(file)

const obsUpload = ctx.obsUpload || {}
const { uploading: obsUploading, progressPercent: obsProgressPercent, currentFile: obsCurrentFile, error: obsError } = obsUpload
const handleObsCancel = () => obsUpload?.cancel?.()
</script>

<style scoped>
.parse-alert {
  margin-top: 14px;
}
</style>
