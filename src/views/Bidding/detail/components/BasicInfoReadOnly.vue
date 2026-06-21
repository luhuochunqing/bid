<template>
  <div class="tab-content">
    <el-card shadow="never">
      <el-form label-width="100px" label-position="left" :disabled="true">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="客户类型">
              <el-input :model-value="tender.customerType || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目类型">
              <el-input :model-value="tender.projectType || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <div class="contact-group-title">联系人1</div>
          </el-col>
          <el-col :span="6">
            <el-form-item label="姓名">
              <el-input :model-value="tender.contactName || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="手机号">
              <el-input :model-value="tender.contactPhone || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="座机">
              <el-input :model-value="tender.contactTel || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="邮箱">
              <el-input :model-value="tender.contactMail || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <div class="contact-group-title">联系人2</div>
          </el-col>
          <el-col :span="6">
            <el-form-item label="姓名">
              <el-input :model-value="tender.contactName2 || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="手机号">
              <el-input :model-value="tender.contactPhone2 || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="座机">
              <el-input :model-value="tender.contactTel2 || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="邮箱">
              <el-input :model-value="tender.contactMail2 || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="标讯描述">
              <textarea :value="tender.description || '-'" rows="10" readonly class="readonly-textarea" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="标讯信息">
              <textarea :value="tender.tenderInfo || '-'" rows="10" readonly class="readonly-textarea" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="标讯文件">
              <template v-if="tender.attachments && tender.attachments.length">
                <div v-for="(file, idx) in tender.attachments" :key="idx" style="margin-bottom:4px">
                  <el-link type="primary" @click.prevent="downloadWithFilename(file.fileUrl, file.fileName)" :underline="false">
                    📄 {{ file.fileName }}
                  </el-link>
                </div>
              </template>
              <template v-else-if="tender.sourceDocumentName && sourceDocumentDownloadUrl">
                <el-link type="primary" @click.prevent="downloadWithFilename(tender.sourceDocumentFileUrl, tender.sourceDocumentName)" :underline="false">
                  📄 {{ tender.sourceDocumentName }}
                </el-link>
              </template>
              <span v-else>-</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目负责人">
              <el-input :model-value="tender.projectManagerName || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目部门">
              <el-input :model-value="tender.department || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="创建人">
              <el-input :model-value="tender.creatorName || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="创建时间">
              <el-input :model-value="formatTenderDateTime(tender.createdAt) || '-'" readonly />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { formatTenderDateTime } from '../../bidding-utils.js'
import { API_BASE_URL } from '@/api/config.js'

const props = defineProps({
  tender: { type: Object, default: null },
})

function normalizeDownloadUrl(url) {
  if (!url) return ''
  if (url.startsWith('doc-insight://')) {
    return `${API_BASE_URL}/api/doc-insight/download?fileUrl=${encodeURIComponent(url)}`
  }
  if (url.startsWith('/api/') && API_BASE_URL) {
    return `${API_BASE_URL}${url}`
  }
  return url
}

async function downloadWithFilename(url, fallbackName) {
  const fullUrl = normalizeDownloadUrl(url)
  if (!fullUrl) return
  try {
    const resp = await fetch(fullUrl, { credentials: 'include' })
    if (!resp.ok) { window.open(fullUrl, '_blank'); return }
    const disposition = resp.headers.get('Content-Disposition') || ''
    const match = disposition.match(/filename\*=UTF-8''(.+?)(?:;|$)/i)
      || disposition.match(/filename="?([^";\n]+)"?/i)
    const filename = match ? decodeURIComponent(match[1].trim()) : fallbackName
    const blob = await resp.blob()
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(a.href)
  } catch {
    window.open(fullUrl, '_blank')
  }
}

const sourceDocumentDownloadUrl = computed(() => normalizeDownloadUrl(props.tender?.sourceDocumentFileUrl))
</script>

<style scoped>
.contact-group-title {
  font-weight: 600;
  margin: 12px 0 8px;
  color: #606266;
  line-height: 32px;
  padding-left: 0;
}
.readonly-textarea {
  width: 100%;
  min-height: 72px;
  padding: 5px 11px;
  border: 1px solid var(--gray-100, #E8E8E8);
  border-radius: 6px;
  font-family: inherit;
  font-size: inherit;
  line-height: 1.5;
  color: var(--text-primary-ui, #303133);
  background: var(--bg-subtle, #F5F7FA);
  resize: vertical;
  overflow-y: scroll;
}
</style>
