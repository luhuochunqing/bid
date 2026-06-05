<template>
  <div class="bidding-header">
    <div>
      <h2 class="page-title">标讯中心</h2>
      <p class="page-subtitle">AI智能匹配，发现优质商机</p>
    </div>
    <div class="header-actions">
      <el-button v-if="customerOpportunityEnabled" @click="$emit('open-customer-opportunities')">
        <el-icon><UserFilled /></el-icon>
        客户商机中心
      </el-button>
      <el-button v-if="canSyncExternalSource" type="primary" @click="$emit('open-source-config')">
        <el-icon><Setting /></el-icon>
        标讯源配置
      </el-button>
      <el-button
        v-if="canSyncExternalSource"
        type="success"
        :loading="fetchingTenders"
        @click="$emit('sync-external')"
      >
        <el-icon><Download /></el-icon>
        一键获取标讯
      </el-button>
      <el-button v-if="canCreateTender" type="warning" @click="handleManualAdd">
        <el-icon><Plus /></el-icon>
        人工录入
      </el-button>
      <el-button v-if="canCreateTender" @click="$emit('download-import-template')">
        <el-icon><Download /></el-icon>
        下载批量导入模板
      </el-button>
      <el-button v-if="canBulkImport" type="warning" plain @click="$emit('open-bulk-import')">
        <el-icon><Upload /></el-icon>
        批量导入
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { Download, Plus, Setting, Upload, UserFilled } from '@element-plus/icons-vue'

defineProps({
  customerOpportunityEnabled: { type: Boolean, default: true },
  canCreateTender: { type: Boolean, default: false },
  canSyncExternalSource: { type: Boolean, default: false },
  canBulkImport: { type: Boolean, default: false },
  fetchingTenders: { type: Boolean, default: false },
})

defineEmits([
  'open-customer-opportunities',
  'open-source-config',
  'sync-external',
  'open-manual-add',
  'download-import-template',
  'open-bulk-import'
])

const router = useRouter()
function handleManualAdd() {
  router.push('/bidding/create')
}
</script>
