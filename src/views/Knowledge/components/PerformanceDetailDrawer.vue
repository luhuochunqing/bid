<template>
  <el-drawer
    :model-value="visible"
    @update:model-value="val => $emit('update:visible', val)"
    title="业绩详情档案"
    size="800px"
    class="premium-drawer"
  >
    <template #header>
      <div class="drawer-header-custom">
        <h3>业绩详情档案</h3>
        <el-tag :type="getStatusTagType(data.status)" effect="dark" size="large">
          {{ data.statusLabel }}
        </el-tag>
      </div>
    </template>

    <el-tabs v-if="visible" v-model="activeDetailTab" class="detail-tabs" @tab-change="onTabChange">
      <!-- Tab 1: 合同基础信息 -->
      <el-tab-pane label="合同基础" name="base">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="合同名称" :span="2">{{ data.contractName }}</el-descriptions-item>
          <el-descriptions-item label="签约单位" :span="2">{{ data.signingEntity }}</el-descriptions-item>
          <el-descriptions-item label="集团公司名称">{{ data.groupCompany }}</el-descriptions-item>
          <el-descriptions-item label="客户类型">
            <el-tag :type="getCustomerTypeTagType(data.customerType)">{{ data.customerTypeLabel }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="所属行业">{{ data.industry }}</el-descriptions-item>
          <el-descriptions-item label="项目类型">{{ data.projectTypeLabel }}</el-descriptions-item>
          <el-descriptions-item label="对接方式">{{ data.dockingMethodLabel }}</el-descriptions-item>
          <el-descriptions-item label="客户级别">{{ data.customerLevelLabel }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>

      <!-- Tab 2: 关键日期 -->
      <el-tab-pane label="关键日期" name="dates">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="签约日期">{{ data.signingDate }}</el-descriptions-item>
          <el-descriptions-item label="截止日期">
            <span :class="getExpiryDateClass(data)">{{ data.expiryDate }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="总截止日期（含可续约期）">{{ data.totalExpiryDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="到期余天">
            <span :class="getDaysRemainingClass(data)" style="font-weight: 600">
              {{ formatDaysRemaining(data.daysRemaining) }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="到期提醒" v-if="data.expiryReminder">
            <el-alert :title="data.expiryReminder" type="warning" show-icon :closable="false" />
          </el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>

      <!-- Tab 3: 客户与联系人 -->
      <el-tab-pane label="客户信息" name="contact">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="客户联系人">{{ data.contactPerson || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系方式">{{ data.contactInfo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="属地(省市区)" :span="2">{{ data.territory || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户详细地址" :span="2">{{ data.customerAddress || '-' }}</el-descriptions-item>
          <el-descriptions-item label="西域负责人" :span="2">{{ data.xiyuProjectManager || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>

      <!-- Tab 4: 附件资料 -->
      <el-tab-pane label="附件资料" name="attachments">
        <div class="attachment-detail-list">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="客户商城网址">
              <a v-if="data.mallWebsiteUrl" :href="data.mallWebsiteUrl" target="_blank" class="link-url">
                {{ data.mallWebsiteUrl }}
              </a>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="包含中标通知书">
              {{ data.hasBidNotice ? '是' : '否' }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="detail-subtitle">附件清单</div>
          <el-table :data="data.attachments" border style="width: 100%">
            <el-table-column prop="fileType" label="附件分类" width="180">
              <template #default="{ row }">
                <span style="font-weight: 600">{{ getFileTypeLabel(row.fileType) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="fileName" label="附件文件名" min-width="200" />
            <el-table-column label="下载链接">
              <template #default="{ row }">
                <a :href="row.fileUrl" target="_blank" class="link-url">查看/下载附件</a>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- Tab 5: 操作日志（CO-440: 改用业绩专属审计端点） -->
      <el-tab-pane label="操作日志" name="logs">
        <PerformanceOperationLogTimeline
          :performance-id="data.id"
          :load-trigger="logsTrigger"
        />
      </el-tab-pane>
    </el-tabs>
  </el-drawer>
</template>

<script setup>
import { ref, watch } from 'vue'
import PerformanceOperationLogTimeline from './PerformanceOperationLogTimeline.vue'

const props = defineProps({
  visible: Boolean,
  data: {
    type: Object,
    default: () => ({ attachments: [] })
  }
})

defineEmits(['update:visible'])

const activeDetailTab = ref('base')
// 用于触发子组件加载日志的信号（每次切到 logs tab 时切换）
const logsTrigger = ref(false)

const onTabChange = (tab) => {
  if (tab === 'logs' && props.data.id) {
    logsTrigger.value = !logsTrigger.value
  }
}

watch(() => props.visible, (v) => {
  if (!v) { activeDetailTab.value = 'base' }
})

const getCustomerTypeTagType = (t) => {
  if (t === 'CENTRAL_SOE') return 'danger'
  if (t === 'LOCAL_SOE') return 'warning'
  if (t === 'GOVERNMENT_INSTITUTION') return 'success'
  return 'primary'
}

const getStatusTagType = (s) => {
  if (s === 'EXPIRED') return 'danger'
  if (s === 'EXPIRING') return 'warning'
  return 'success'
}

const getExpiryDateClass = (row) => {
  if (row.status === 'EXPIRED') return 'text-danger'
  if (row.status === 'EXPIRING') return 'text-warning'
  return 'text-normal'
}

const getDaysRemainingClass = (row) => {
  if (row.daysRemaining != null && row.daysRemaining < 0) return 'text-danger'
  if (row.status === 'EXPIRING') return 'text-warning'
  return 'text-success'
}

const formatDaysRemaining = (days) => {
  if (days == null || days > 999999999 || days === 2147483647) return '-'
  if (days < 0) return `已逾期 ${Math.abs(days)} 天`
  return `${days} 天`
}

const getFileTypeLabel = (type) => {
  const map = {
    CONTRACT_AGREEMENT: '合同协议扫描件',
    MALL_SCREENSHOT: '商城上架截图',
    SOE_DIRECTORY: '央企名录页证明',
    CATEGORY_PAGE: '品类授权页证明',
    RELATIONSHIP_PROOF: '组织层级关系证明',
    BID_NOTICE: '中标通知书',
    OTHER: '其他关联证明'
  }
  return map[type] || type
}
</script>

<style scoped lang="scss">
.drawer-header-custom {
  display: flex;
  align-items: center;
  gap: 16px;
  h3 {
    margin: 0;
    font-size: 20px;
    color: var(--el-text-color-primary);
  }
}

.detail-tabs {
  margin-top: -10px;
  :deep(.el-tabs__item) {
    font-size: 14px;
    font-weight: 500;
  }
}

.detail-subtitle {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 20px 0 12px 0;
  border-left: 4px solid var(--el-color-primary);
  padding-left: 8px;
}

.text-danger { color: var(--el-color-danger); }
.text-warning { color: var(--el-color-warning); }
.text-success { color: var(--el-color-success); }
.text-normal { color: var(--el-text-color-regular); }

.link-url {
  color: var(--el-color-primary);
  text-decoration: none;
  font-weight: 500;
  &:hover {
    text-decoration: underline;
  }
}
</style>
