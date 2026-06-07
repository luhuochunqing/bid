<template>
  <section class="agent-section tender-upload-step">
    <header>上传招标文件</header>
    <el-upload
      class="tender-upload"
      drag
      :auto-upload="false"
      :show-file-list="false"
      accept=".doc,.docx,.pdf"
      :before-upload="agent.selectTenderFile"
      :on-change="agent.selectTenderFile"
    >
      <div class="upload-copy">
        <strong>{{ agent.selectedTenderFileName.value || '选择 .doc/.docx/文本型 PDF 招标文件' }}</strong>
        <span>上传后会先提取正文并拆解要求，再生成初稿；生成或写入失败都可以单独重试。</span>
      </div>
    </el-upload>

    <div class="upload-actions">
      <el-button type="primary" :loading="agent.importing.value" @click="agent.importTenderDocument()">
        上传招标文件并核对要求
      </el-button>
      <el-button :disabled="!agent.selectedTenderFileName.value || agent.importing.value" @click="agent.clearTenderFile">
        清空
      </el-button>
    </div>

    <el-alert
      v-if="agent.importResult.value?.document"
      type="success"
      show-icon
      :closable="false"
      :title="importSummary"
    />
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { useProjectDetailContext } from '@/composables/projectDetail/context.js'

const detail = useProjectDetailContext()
const agent = detail.bidAgent

const importSummary = computed(() => {
  const document = agent.importResult.value?.document
  if (!document) return ''
  const textLength = document.extractedTextLength ? `，已提取 ${document.extractedTextLength} 字` : ''
  return `已解析 ${document.name || '招标文件'}${textLength}`
})
</script>

<style scoped>
.tender-upload-step {
  display: grid;
  gap: 12px;
}

.upload-copy {
  display: grid;
  gap: 6px;
  padding: 8px;
  color: #244233;
}

.upload-copy span {
  color: #66796c;
  font-size: 13px;
}

.upload-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
</style>
