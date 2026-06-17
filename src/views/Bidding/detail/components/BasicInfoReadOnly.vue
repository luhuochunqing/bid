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
              <el-input :model-value="tender.description || '-'" type="textarea" :rows="3" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="标讯信息">
              <el-input :model-value="tender.tenderInfo || '-'" type="textarea" :rows="3" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="标讯文件">
              <template v-if="tender.attachments && tender.attachments.length">
                <div v-for="(file, idx) in tender.attachments" :key="idx" style="margin-bottom:4px">
                  <el-link type="primary" :href="file.fileUrl" target="_blank" :underline="false">
                    📄 {{ file.fileName }}
                  </el-link>
                </div>
              </template>
              <template v-else-if="tender.sourceDocumentName && sourceDocumentDownloadUrl">
                <el-link type="primary" :href="sourceDocumentDownloadUrl" target="_blank" :underline="false">
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

const props = defineProps({
  tender: { type: Object, default: null },
})

const sourceDocumentDownloadUrl = computed(() => {
  const url = props.tender?.sourceDocumentFileUrl
  if (!url) return ''
  if (url.startsWith('doc-insight://')) {
    return `/api/doc-insight/download?fileUrl=${encodeURIComponent(url)}`
  }
  return url
})
</script>

<style scoped>
.contact-group-title {
  font-weight: 600;
  margin: 12px 0 8px;
  color: #606266;
  line-height: 32px;
  padding-left: 0;
}
</style>
