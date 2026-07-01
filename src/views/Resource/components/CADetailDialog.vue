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
                <el-tag v-for="p in ca.platformIds" :key="p" size="small" class="platform-tag">{{ p }}</el-tag>
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

            <el-descriptions-item v-if="ca.createdAt" label="创建时间">{{ ca.createdAt }}</el-descriptions-item>

            <el-descriptions-item v-if="ca.updatedAt" label="更新时间">{{ ca.updatedAt }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <!-- Tab 2: 借用记录 -->
        <el-tab-pane label="借用记录" name="borrow">
          <div v-if="borrowApplications.length === 0" class="empty-tab">
            <el-empty description="暂无借用记录" :image-size="80" />
          </div>
          <el-table
            v-else
            :data="borrowApplications"
            stripe
            size="small"
            max-height="400"
          >
            <el-table-column prop="applicantName" label="申请人" min-width="80" />
            <el-table-column prop="purpose" label="用途" min-width="120" />
            <el-table-column prop="borrowDate" label="借用日期" width="105" />
            <el-table-column prop="expectedReturnDate" label="预计归还" width="105" />
            <el-table-column prop="actualReturnDate" label="实际归还" width="105">
              <template #default="{ row }">
                {{ row.actualReturnDate || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag
                  :type="caApplicationStatusTagType(row.status)"
                  size="small"
                >
                  {{ row.statusLabel }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Tab 3: 操作日志 -->
        <el-tab-pane label="操作日志" name="log">
          <div v-if="operationEvents.length === 0" class="empty-tab">
            <el-empty description="暂无操作日志" :image-size="80" />
          </div>
          <el-timeline v-else>
            <el-timeline-item
              v-for="event in operationEvents"
              :key="event.id"
              :timestamp="event.createdAt"
              placement="top"
              :type="caEventTypeColor(event.eventType)"
            >
              <div class="timeline-content">
                <el-tag size="small" :type="caEventTypeColor(event.eventType)" class="event-tag">
                  {{ event.eventTypeLabel }}
                </el-tag>
                <span class="event-detail">{{ event.detail || '' }}</span>
                <span class="event-operator">操作人：{{ event.operatorName }}</span>
              </div>
            </el-timeline-item>
          </el-timeline>
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
        <el-icon><Share /></el-icon>借用
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
  caApplicationStatusTagType,
  caEventTypeColor
} from '../composables/useCaBorrowEligibility'
import { formatDisplayName } from '@/utils/formatDisplayName'

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

.empty-tab {
  padding: 40px 0;
}

.timeline-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.event-tag {
  align-self: flex-start;
}

.event-detail {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.event-operator {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.detail-actions {
  display: flex;
  gap: 12px;
  padding-top: 16px;
  margin-top: 16px;
  border-top: 1px solid var(--el-border-color-light);
}
</style>
