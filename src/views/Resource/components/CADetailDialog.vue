<template>
  <el-dialog
    v-model="visible"
    :title="ca ? 'CA 证书详情' : ''"
    width="680px"
    destroy-on-close
  >
    <template v-if="ca">
      <el-tabs v-model="activeTab" class="detail-tabs">
        <!-- Tab 1: 基本信息 -->
        <el-tab-pane label="基本信息" name="info">
          <el-descriptions :column="1" border size="small" class="detail-section">
            <el-descriptions-item label="关联平台">
              <template v-if="ca.platformIds && ca.platformIds.length">
                <el-tag v-for="p in ca.platformIds" :key="p" size="small" class="platform-tag">{{ ca.platformNamesById?.[p] || p }}</el-tag>
              </template>
              <span v-else>-</span>
            </el-descriptions-item>

            <el-descriptions-item label="CA 类型">
              <el-tag :type="ca.caType === 'ENTITY_CA' ? 'primary' : 'success'" size="small">
                {{ ca.caTypeLabel }}
              </el-tag>
            </el-descriptions-item>

            <el-descriptions-item label="印章类型">{{ ca.sealTypeLabel }}</el-descriptions-item>

            <el-descriptions-item v-if="ca.caType === 'ELECTRONIC_CA'" label="电子账号">
              {{ ca.electronicAccount || '-' }}
            </el-descriptions-item>

            <el-descriptions-item label="CA 密码">
              {{ ca.caPasswordMasked || '未设置' }}
            </el-descriptions-item>

            <el-descriptions-item label="有效期至">
              <el-tag
                :type="ca.status === 'EXPIRED' ? 'danger' : ca.status === 'EXPIRING' ? 'warning' : 'success'"
                size="small"
              >
                {{ ca.expiryDate || '-' }}
              </el-tag>
            </el-descriptions-item>

            <el-descriptions-item label="到期天数">
              <span v-if="ca.remainingDays < 0" style="color: var(--el-color-danger); font-weight: 600;">
                {{ ca.remainingDays }}天（已过期）
              </span>
              <span v-else-if="ca.remainingDays <= 30" style="color: var(--el-color-warning); font-weight: 600;">
                剩{{ ca.remainingDays }}天
              </span>
              <span v-else-if="ca.remainingDays && ca.remainingDays < Infinity">
                {{ ca.remainingDays }}天
              </span>
              <span v-else>-</span>
            </el-descriptions-item>

            <el-descriptions-item label="状态">
              <el-tag
                :type="caStatusTagType(ca.status)"
                size="small"
              >
                {{ ca.statusLabel }}
              </el-tag>
            </el-descriptions-item>

            <el-descriptions-item label="借用状态">
              <el-tag
                :type="caBorrowStatusTagType(ca.borrowStatus)"
                size="small"
              >
                {{ ca.borrowStatusLabel }}
              </el-tag>
            </el-descriptions-item>

            <el-descriptions-item v-if="ca.borrowStatus === 'BORROWED'" label="当前借用人">
              {{ ca.currentBorrowerName || '-' }}
            </el-descriptions-item>

            <el-descriptions-item label="平台地址/APP">{{ ca.caPlatformUrl || '-' }}</el-descriptions-item>

            <!-- CO-451: 保管员显示为"姓名（工号）"格式，删除保管员ID字段 -->
            <el-descriptions-item label="保管员">{{ formatDisplayName(ca.custodianName, ca.custodianEmployeeNumber) }}</el-descriptions-item>

            <el-descriptions-item v-if="ca.remark" label="备注">{{ ca.remark }}</el-descriptions-item>

            <el-descriptions-item v-if="ca.createdAt" label="创建时间"><DateTimeDisplay :value="ca.createdAt" /></el-descriptions-item>

            <el-descriptions-item v-if="ca.updatedAt" label="更新时间"><DateTimeDisplay :value="ca.updatedAt" /></el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <!-- Tab 2: 借用记录（CO-515: 按 PRD 9 列展示，抽为子组件 CABorrowRecordsTable） -->
        <el-tab-pane label="借用记录" name="borrow">
          <CABorrowRecordsTable :applications="borrowApplications" />
        </el-tab-pane>

        <!-- Tab 3: 操作日志（CO-515: 对齐账户详情，改为表格形式展示） -->
        <el-tab-pane label="操作日志" name="log">
          <el-table :data="operationEvents" stripe size="small" max-height="360" scrollbar-always-on style="width: 100%">
            <el-table-column label="操作类型" width="120">
              <template #default="{ row }">
                <el-tag :type="caEventTypeColor(row.eventType)" size="small">
                  {{ row.eventTypeLabel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="operatorName" label="操作人" width="140" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="时间" width="160" />
            <el-table-column prop="detail" label="详情" min-width="240" show-overflow-tooltip />
          </el-table>
          <el-empty v-if="!operationEvents.length" description="暂无操作日志" :image-size="60" />
        </el-tab-pane>
      </el-tabs>
    </template>

    <template v-else>
      <el-empty description="暂无数据" />
    </template>

    <!-- Bottom actions -->
    <div v-if="ca" class="detail-actions">
      <el-button
        v-if="actions.canBorrow"
        type="primary"
        @click="$emit('borrow', ca)"
      >
        <el-icon><Share /></el-icon>申请使用
      </el-button>
      <el-button
        v-if="actions.canReturn"
        type="warning"
        @click="$emit('return', ca)"
      >
        <el-icon><Share /></el-icon>登记归还
      </el-button>
      <el-button
        v-if="actions.canEdit"
        @click="$emit('edit', ca)"
      >
        <el-icon><Edit /></el-icon>编辑
      </el-button>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Share, Edit } from '@element-plus/icons-vue'
import {
  caStatusTagType,
  caBorrowStatusTagType,
  caEventTypeColor
} from '../composables/useCaBorrowEligibility'
import { formatDisplayName } from '@/utils/formatDisplayName'
import DateTimeDisplay from '@/components/common/DateTimeDisplay.vue'
// CO-515: 借用记录表格抽为子组件，保持父组件在 line-budget 内
import CABorrowRecordsTable from './CABorrowRecordsTable.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  ca: { type: Object, default: null },
  borrowApplications: { type: Array, default: () => [] },
  operationEvents: { type: Array, default: () => [] },
  actions: {
    type: Object,
    default: () => ({ canEdit: false, canBorrow: false, canReturn: false })
  }
})

const emit = defineEmits(['update:modelValue', 'edit', 'borrow', 'return'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const activeTab = ref('info')

// Reset tab when dialog opens
watch(() => props.modelValue, (v) => {
  if (v) activeTab.value = 'info'
})
</script>

<style scoped>
.detail-tabs {
  margin-bottom: 16px;
}

.detail-section {
  margin-bottom: 0;
}

.platform-tag {
  margin-right: 4px;
  margin-bottom: 2px;
}

.detail-actions {
  display: flex;
  gap: 12px;
  padding-top: 16px;
  margin-top: 16px;
  border-top: 1px solid var(--el-border-color-light);
}
</style>
