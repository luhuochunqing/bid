<template>
  <el-card class="info-card">
    <template #header>
      <div class="card-title">
        <el-icon><InfoFilled /></el-icon>
        <span>项目信息</span>
        <el-tag v-if="tenderLoading" size="small" type="info" class="loading-tag">加载标讯...</el-tag>
      </div>
    </template>

    <el-descriptions :column="2" border size="small">
      <el-descriptions-item label="项目名称">{{ project?.name }}</el-descriptions-item>
      <el-descriptions-item label="招标主体">{{ project?.ownerUnit }}</el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ formatDate(project?.createdAt) }}</el-descriptions-item>
      <el-descriptions-item label="项目类型">{{ project?.projectType }}</el-descriptions-item>
      <el-descriptions-item label="客户类型">{{ project?.customerType }}</el-descriptions-item>
      <el-descriptions-item label="优先级">
        <el-tag v-if="project?.priority" :type="priorityTagType(project?.priority)" size="small">{{ priorityLabel(project?.priority) }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="总部所在地">{{ project?.region }}</el-descriptions-item>
      <el-descriptions-item label="报名截止时间">{{ formatDate(tf('registrationDeadline')) }}</el-descriptions-item>
      <el-descriptions-item label="开标时间">{{ formatDate(tf('bidOpeningTime')) }}</el-descriptions-item>
      <el-descriptions-item label="投标月份" :span="2">{{ project?.bidMonth }}</el-descriptions-item>
      <el-descriptions-item label="项目负责人">{{ project?.projectLeaderName || tf('projectManagerName') }}</el-descriptions-item>
      <el-descriptions-item label="项目负责人部门">{{ project?.leaderDepartment || tf('department') }}</el-descriptions-item>
      <el-descriptions-item label="联系人1">{{ tf('contactName') }}</el-descriptions-item>
      <el-descriptions-item label="联系人1手机号">{{ tf('contactPhone') }}</el-descriptions-item>
      <el-descriptions-item label="联系人1座机">{{ tf('contactTel') }}</el-descriptions-item>
      <el-descriptions-item label="联系人1邮箱">{{ tf('contactMail') }}</el-descriptions-item>
      <el-descriptions-item label="联系人2">{{ tf('contactName2') }}</el-descriptions-item>
      <el-descriptions-item label="联系人2手机号">{{ tf('contactPhone2') }}</el-descriptions-item>
      <el-descriptions-item label="联系人2座机">{{ tf('contactTel2') }}</el-descriptions-item>
      <el-descriptions-item label="联系人2邮箱">{{ tf('contactMail2') }}</el-descriptions-item>
      <el-descriptions-item label="标讯" :span="2">
        <span class="tender-info-text">{{ tf('title') || tf('tenderInfo') }}</span>
      </el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import { httpClient } from '@/api'
import { priorityLabel } from '@/views/Project/utils/projectListFormatters.js'

const props = defineProps({
  project: {
    type: Object,
    default: null,
  },
})

const tenderData = ref(null)
const tenderLoading = ref(false)
const tenderUnavailable = ref(false)

const TENDER_FALLBACK = '暂无'

function tf(key) {
  if (tenderUnavailable.value) return TENDER_FALLBACK
  return tenderData.value?.[key] ?? ''
}

function formatDate(value) {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  return d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

function priorityTagType(priority) {
  const p = String(priority || '').toUpperCase()
  if (p === 'S') return 'danger'
  if (p === 'A') return 'warning'
  if (p === 'B') return 'primary'
  return undefined
}

async function fetchTender(tenderId) {
  if (!tenderId) {
    tenderUnavailable.value = true
    ElMessage.warning('该项目未关联标讯，联系人信息暂不可用')
    return
  }
  tenderLoading.value = true
  tenderUnavailable.value = false
  try {
    const resp = await httpClient.get(`/api/tenders/${tenderId}`)
    tenderData.value = resp?.data ?? null
  } catch (e) {
    console.warn('[ProjectBasicInfoCard] fetch tender failed', e)
    tenderUnavailable.value = true
    ElMessage.warning('标讯数据加载失败，联系人及标讯信息暂不可用')
  } finally {
    tenderLoading.value = false
  }
}

watch(() => props.project?.tenderId, (tid) => {
  if (tid) fetchTender(tid)
  else {
    tenderUnavailable.value = true
    ElMessage.warning('该项目未关联标讯，联系人信息暂不可用')
  }
}, { immediate: true })
</script>

<style scoped>
.info-card {
  margin-bottom: 20px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}

.loading-tag {
  margin-left: auto;
}

.tender-info-text {
  font-size: 14px;
  color: var(--text-secondary-ui);
}
</style>
