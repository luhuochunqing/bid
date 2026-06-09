<template>
  <el-dialog v-model="visible" title="账户详情" width="680px">
    <el-tabs v-model="activeTab" v-if="data">
      <el-tab-pane label="基本信息" name="info">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="平台名称" :span="2">{{ data.platform }}</el-descriptions-item>
          <el-descriptions-item label="网址">{{ data.url || '-' }}</el-descriptions-item>
          <el-descriptions-item label="平台类型">{{ data.platformType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="用户名">{{ data.username || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="data.status === 'available'" type="success">可用</el-tag>
            <el-tag v-else-if="data.status === 'in_use'" type="warning">使用中</el-tag>
            <el-tag v-else type="info">禁用</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="绑定联系人">{{ data.contactPerson || '-' }}</el-descriptions-item>
          <el-descriptions-item label="绑定手机">{{ data.contactPhone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="绑定邮箱">{{ data.contactEmail || '-' }}</el-descriptions-item>
          <el-descriptions-item label="是否有 CA">{{ data.hasCa ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="CA 保管人">{{ data.caCustodianName || data.caCustodian || '-' }}</el-descriptions-item>
          <el-descriptions-item label="账号保管员">{{ data.custodianName || data.custodian || '-' }}</el-descriptions-item>
          <el-descriptions-item label="使用人">{{ data.borrower || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ data.remarks || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最近使用">{{ data.lastUsed || '-' }}</el-descriptions-item>
          <el-descriptions-item label="归还截止">{{ data.dueAt || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
      <el-tab-pane label="借用记录" name="borrows">
        <el-table :data="borrowRecords" stripe size="small" max-height="300">
          <el-table-column prop="borrower" label="借用人" width="100" />
          <el-table-column prop="borrowedAt" label="借用时间" width="160" />
          <el-table-column prop="returnDate" label="归还时间" width="160" />
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 'returned' ? 'success' : 'warning'" size="small">
                {{ row.status === 'returned' ? '已归还' : '使用中' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="purpose" label="用途" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="!borrowRecords.length" description="暂无借用记录" :image-size="60" />
      </el-tab-pane>
      <el-tab-pane label="操作日志" name="logs">
        <el-timeline v-if="logs.length">
          <el-timeline-item v-for="(l, i) in logs" :key="i"
            :timestamp="l.timestamp" placement="top">
            <p class="log-desc">{{ l.description }}</p>
            <p class="log-meta">{{ l.operator }}</p>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无操作记录" :image-size="60" />
      </el-tab-pane>
    </el-tabs>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button v-if="data?.status === 'in_use'" type="success" @click="$emit('return')">登记归还</el-button>
     <el-button type="primary" @click="$emit('edit')">编辑</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import httpClient from '@/api/client'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  data: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'edit', 'return'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const activeTab = ref('info')
const logs = ref([])
const borrowRecords = ref([])

watch(() => props.data, async (d) => {
  activeTab.value = 'info'
  logs.value = []
  borrowRecords.value = []
  if (d?.id) {
    try {
      const res = await httpClient.get(`/api/platform/accounts/${d.id}/logs`)
      if (res?.data?.data) {
        logs.value = (Array.isArray(res.data.data) ? res.data.data : [])
          .map(l => ({
            timestamp: l.timestamp || l.createdAt,
            description: l.description || l.action,
            operator: l.username || l.userId
          }))
      }
    } catch { /* no logs yet */ }
  }
})

const fmtTimestamp = (ts) => ts ? new Date(ts).toLocaleString('zh-CN') : '-'
</script>

<style scoped>
.log-desc { margin: 0; font-size: 13px; color: #303133; }
.log-meta { margin: 2px 0 0; font-size: 12px; color: #909399; }
</style>
