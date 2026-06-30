<template>
  <el-drawer :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" :title="detailTitle" size="800px" :with-header="false">
    <div class="detail-header">
      <div class="detail-title">
        <span class="name">{{ personnel.name }}</span>
        <span class="emp-no">{{ personnel.employeeNumber }}</span>
        <el-tag v-if="personnel.highestEducation && personnel.highestEducation !== '-'" size="small" type="info" class="ml-8">{{ personnel.highestEducation }}</el-tag>
      </div>
      <div class="detail-actions">
        <el-button v-if="canEdit" type="primary" size="small" @click="$emit('edit', personnel)">编辑</el-button>
        <el-button size="small" @click="$emit('update:modelValue', false)">关闭</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" type="border-card" class="detail-tabs">
      <el-tab-pane label="基础信息" name="basic">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="姓名">{{ personnel.name }}</el-descriptions-item>
          <el-descriptions-item label="工号">{{ personnel.employeeNumber }}</el-descriptions-item>
          <el-descriptions-item label="性别">{{ personnel.gender || '-' }}</el-descriptions-item>
          <el-descriptions-item label="入职日期">{{ personnel.entryDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="出生日期">{{ personnel.birthDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="入职年限">{{ personnel.yearsOfService != null ? personnel.yearsOfService + ' 年' : '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号码">{{ personnel.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ personnel.departmentName }}</el-descriptions-item>
          <el-descriptions-item label="最高学历">{{ personnel.highestEducation || personnel.education || '-' }}</el-descriptions-item>
          <el-descriptions-item label="技术职称">{{ personnel.technicalTitle || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ personnel.statusLabel }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="personnel.remark" class="remark-display">
          <label>备注：</label>{{ personnel.remark }}
        </div>
      </el-tab-pane>

      <el-tab-pane label="教育经历" name="education">
        <el-table v-if="sortedEducations.length" :data="sortedEducations" stripe size="small">
          <el-table-column prop="schoolName" label="学校名称" min-width="160" />
          <el-table-column label="时间" width="140">
            <template #default="{row}">{{ row.startDate || '-' }} ~ {{ row.endDate || '-' }}</template>
          </el-table-column>
          <el-table-column prop="highestEducation" label="最高学历" width="80" />
          <el-table-column prop="studyForm" label="学习形式" width="100" />
          <el-table-column prop="major" label="专业" min-width="120" />
          <el-table-column label="最高学历学校" width="100" align="center">
            <template #default="{row}">{{ row.isHighestEducationSchool ? '是' : '否' }}</template>
          </el-table-column>
        </el-table>
        <div v-else class="empty-hint">暂无教育经历记录</div>
      </el-tab-pane>

      <el-tab-pane label="证书与职称" name="certificate">
        <el-table v-if="personnel.certificates && personnel.certificates.length" :data="personnel.certificates" stripe size="small">
          <el-table-column prop="name" label="证书名称" min-width="140" />
          <el-table-column prop="certificateNumber" label="编号" width="120" />
          <el-table-column prop="typeLabel" label="类型" width="90" />
          <el-table-column prop="title" label="职称" width="70" />
          <el-table-column label="永久有效" width="80" align="center">
            <template #default="{row}">{{ row.isPermanent ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column prop="expiryDate" label="到期日" width="100" />
          <el-table-column label="状态" width="90">
            <template #default="{row}">
              <el-tag :type="certStatusTagType(row.status)" size="small">{{ certStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="附件" width="120">
            <template #default="{row}">
              <el-link v-if="row.attachmentUrl" :href="row.attachmentUrl" target="_blank" type="primary" @click.stop>下载</el-link>
              <span v-else class="text-muted">无</span>
            </template>
          </el-table-column>
        </el-table>
        <div v-else class="empty-hint">暂无证书记录</div>
        <div v-if="personnel.expiringCertificatesCount > 0" class="expiry-hint">
          <el-icon><Warning /></el-icon> 该人员有 {{ personnel.expiringCertificatesCount }} 个证书即将到期（30 天内）
        </div>
      </el-tab-pane>

      <el-tab-pane label="操作日志" name="log">
        <el-table :data="operationLogs" stripe size="small" v-loading="operationLogsLoading">
          <el-table-column prop="createdAt" label="时间" width="180">
            <template #default="{row}">{{ row.createdAt ? row.createdAt.replace('T', ' ').slice(0, 19) : '-' }}</template>
          </el-table-column>
          <el-table-column label="操作人" width="180">
            <template #default="{row}">{{ row.operatorName || '-' }}</template>
          </el-table-column>
          <el-table-column prop="operationType" label="类型" width="120">
            <template #default="{row}">{{ formatOperationType(row.operationType) }}</template>
          </el-table-column>
          <el-table-column label="变更摘要" min-width="200">
            <template #default="{row}">
              <span v-if="row.changeDetails && row.changeDetails.length">
                {{ formatChangeSummary(row.operationType, row.changeDetails) }}
              </span>
              <span v-else class="text-muted">—</span>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!operationLogsLoading && !operationLogs.length" class="empty-hint">暂无操作日志（新建人员或无变更时为空）</div>
      </el-tab-pane>
    </el-tabs>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Warning } from '@element-plus/icons-vue'
import { certStatusLabel, certStatusTagType, formatOperationType, formatChangeSummary } from './personnelConstants.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  personnel: { type: Object, default: () => ({}) },
  canEdit: { type: Boolean, default: false }
})

defineEmits(['update:modelValue', 'edit'])

const activeTab = ref('basic')
const operationLogs = ref([])
const operationLogsLoading = ref(false)

const detailTitle = computed(() =>
  props.personnel?.name ? `${props.personnel.name}（${props.personnel.employeeNumber || ''}）详情` : '人员详情'
)

const sortedEducations = computed(() => {
  const list = props.personnel?.educations || []
  return [...list].sort((a, b) => (b.startDate || '').localeCompare(a.startDate || ''))
})

let personnelApi = null

async function loadOperationLogs() {
  if (!props.personnel?.id) return
  if (!personnelApi) {
    const mod = await import('@/api/modules/personnel.js')
    personnelApi = mod.default
  }
  operationLogsLoading.value = true
  try {
    const res = await personnelApi.getOperationLogs(props.personnel.id)
    operationLogs.value = Array.isArray(res?.data) ? res.data : []
  } catch {
    operationLogs.value = []
  } finally {
    operationLogsLoading.value = false
  }
}

watch(() => props.modelValue, (visible) => {
  if (visible) {
    activeTab.value = 'basic'
    operationLogs.value = []
  }
})

watch(activeTab, (tab) => {
  if (tab === 'log') loadOperationLogs()
})
</script>

<style scoped>
.detail-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px 8px; border-bottom: 1px solid var(--el-border-color-lighter); }
.detail-title .name { font-size: 18px; font-weight: 600; margin-right: 8px; }
.detail-title .emp-no { font-size: 14px; color: var(--el-text-color-regular); margin-right: 8px; }
.detail-actions { display: flex; gap: 8px; }
.detail-tabs { margin-top: 8px; }
.empty-hint { color: var(--el-text-color-secondary); font-size: 13px; padding: 20px 0; text-align: center; }
.expiry-hint { margin-top: 8px; color: var(--el-color-warning); font-size: 12px; display: flex; align-items: center; gap: 4px; }
.text-muted { color: var(--el-text-color-placeholder); font-size: 12px; }
.ml-8 { margin-left: 8px; }
.remark-display { margin-top: 12px; padding: 8px 12px; background: #f8f9fa; border-radius: 4px; font-size: 13px; line-height: 1.5; }
.remark-display label { font-weight: 600; color: var(--el-text-color-primary); }
</style>
