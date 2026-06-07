<template>
  <div class="tab-content">
    <el-card shadow="never">
      <el-form label-width="110px" :disabled="true">
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
          <el-col :span="4">
            <el-form-item label="姓名" label-width="56px">
              <el-input :model-value="tender.contactName || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="手机号" label-width="64px">
              <el-input :model-value="tender.contactPhone || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="7">
            <el-form-item label="座机" label-width="56px">
              <el-input :model-value="tender.contactTel || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="7">
            <el-form-item label="邮箱" label-width="56px">
              <el-input :model-value="tender.contactMail || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <div class="contact-group-title">联系人2</div>
          </el-col>
          <el-col :span="4">
            <el-form-item label="姓名" label-width="56px">
              <el-input :model-value="tender.contactName2 || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="手机号" label-width="64px">
              <el-input :model-value="tender.contactPhone2 || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="7">
            <el-form-item label="座机" label-width="56px">
              <el-input :model-value="tender.contactTel2 || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="7">
            <el-form-item label="邮箱" label-width="56px">
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
              <template v-if="tender.sourceDocumentName && tender.sourceDocumentFileUrl">
                <el-link type="primary" :href="tender.sourceDocumentFileUrl" target="_blank" :underline="false">
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
import { formatTenderDateTime } from '../../bidding-utils.js'

const props = defineProps({
  tender: { type: Object, default: null },
})
</script>

<style scoped>
.contact-group-title {
  font-weight: 600;
  margin: 12px 0 8px;
  color: #606266;
}
</style>
