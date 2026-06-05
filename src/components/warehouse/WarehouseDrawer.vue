<template>
  <el-drawer v-model="visible" :title="detail ? `仓库详情 - ${detail.name}` : '仓库详情'" size="800px" :with-header="true">
    <div v-if="loading" v-loading="true" style="padding:40px;text-align:center" />
    <template v-else-if="detail">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基础信息" name="basic">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="仓库名称">{{ detail.name }}</el-descriptions-item>
            <el-descriptions-item label="仓库类型"><el-tag size="small">{{ detail.type === 'SELF_OPERATED' ? '自营' : '云仓' }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="所属区域">{{ detail.region }}</el-descriptions-item>
            <el-descriptions-item label="所在省份">{{ detail.province }}</el-descriptions-item>
            <el-descriptions-item label="具体地址" :span="2">{{ detail.address }}</el-descriptions-item>
            <el-descriptions-item label="仓库面积">{{ detail.area }} ㎡</el-descriptions-item>
            <el-descriptions-item label="区域联系人">{{ detail.contactPerson }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small" :type="statusType(detail.status)">{{ statusLabel(detail.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ detail.remarks || '—' }}</el-descriptions-item>
          </el-descriptions>
          <el-divider content-position="left">租约/服务信息</el-divider>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="开始时间">{{ detail.startDate || '—' }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{ detail.endDate || '—' }}</el-descriptions-item>
            <el-descriptions-item label="出租方">{{ detail.lessor || '—' }}</el-descriptions-item>
            <el-descriptions-item label="承租方">{{ detail.lessee || '—' }}</el-descriptions-item>
            <el-descriptions-item label="发票租期" :span="2">{{ detail.invoicePeriod || '—' }}</el-descriptions-item>
            <el-descriptions-item label="关仓计划" :span="2">{{ detail.closePlan || '—' }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.closeReason" label="关仓原因" :span="2">{{ detail.closeReason }}</el-descriptions-item>
          </el-descriptions>
          <el-divider content-position="left">资料核验</el-divider>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="产权证"><el-tag size="small" :type="detail.hasPropertyCert?'success':'info'">{{ detail.hasPropertyCert?'有':'无' }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="发票"><el-tag size="small" :type="detail.hasInvoice?'success':'info'">{{ detail.hasInvoice?'有':'无' }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="内外照片"><el-tag size="small" :type="detail.hasPhotos?'success':'info'">{{ detail.hasPhotos?'有':'无' }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="核验备注" :span="3">{{ detail.certRemarks || '—' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        <el-tab-pane label="附件管理" name="attachments">
          <div class="attach-toolbar">
            <el-button size="small" type="primary" @click="triggerUpload"><el-icon><Upload /></el-icon> 上传附件</el-button>
            <el-select v-model="uploadType" size="small" style="width:120px;margin-left:8px">
              <el-option label="产权证" value="PROPERTY_CERTIFICATE" />
              <el-option label="发票" value="INVOICE" />
              <el-option label="内外照片" value="PHOTOS" />
            </el-select>
          </div>
          <input ref="fileInputRef" type="file" style="display:none" accept="*" @change="handleFileChange" />
          <el-table :data="attachments" style="width:100%;margin-top:12px" size="small" empty-text="暂无附件">
            <el-table-column label="附件类型" width="100">
              <template #default="s">{{ typeLabel(s.row.type) }}</template>
            </el-table-column>
            <el-table-column prop="originalFilename" label="文件名" min-width="180" show-overflow-tooltip />
            <el-table-column label="大小" width="90" align="right">
              <template #default="s">{{ formatSize(s.row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="上传时间" width="150">
              <template #default="s">{{ s.row.uploadedAt }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="center">
              <template #default="s">
                <el-button link type="primary" size="small" @click="downloadAttach(s.row)">下载</el-button>
                <el-button link type="danger" size="small" @click="deleteAttach(s.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="操作日志" name="logs">
          <el-table :data="logs" style="width:100%" size="small" empty-text="暂无操作记录">
            <el-table-column label="时间" width="160">
              <template #default="s">{{ s.row.createdAt }}</template>
            </el-table-column>
            <el-table-column label="操作人" width="100">
              <template #default="s">{{ s.row.operatorUsername }}</template>
            </el-table-column>
            <el-table-column label="操作类型" width="100">
              <template #default="s">{{ actionTypeLabel(s.row.actionType) }}</template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
          </el-table>
          <div v-if="logTotal > pageSize" style="text-align:center;margin-top:12px">
            <el-pagination v-model:current-page="logPage" :page-size="pageSize" :total="logTotal" small layout="prev,pager,next" @current-change="loadLogs" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>
    <template #footer>
      <div style="text-align:right">
        <el-button @click="visible = false">关闭</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import http from '@/api/client'

const props = defineProps({
  modelValue: Boolean,
  warehouseId: { type: Number, default: null }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({ get: () => props.modelValue, set: (v) => emit('update:modelValue', v) })
const detail = ref(null); const attachments = ref([]); const logs = ref([])
const loading = ref(false); const activeTab = ref('basic')
const uploadType = ref('PROPERTY_CERTIFICATE')
const fileInputRef = ref(); const logPage = ref(1); const logTotal = ref(0)
const pageSize = 10

const STATUS_MAP = { IN_USE:'使用中', EXPIRING:'即将到期', EXPIRED:'已过期', CLOSED:'已关仓' }
const ATTACH_TYPE_MAP = { PROPERTY_CERTIFICATE:'产权证', INVOICE:'发票', PHOTOS:'内外照片' }
const statusLabel = (s) => STATUS_MAP[s] || s
const statusType = (s) => s==='IN_USE'?'success':s==='EXPIRING'?'warning':s==='EXPIRED'?'danger':'info'
const typeLabel = (t) => ATTACH_TYPE_MAP[t] || t
const ACTION_TYPE_MAP = { CREATE:'创建', EDIT:'编辑', CLOSE:'关仓', RESTORE:'恢复', ATTACH_UPLOAD:'上传附件', ATTACH_DELETE:'删除附件' }
const actionTypeLabel = (a) => ACTION_TYPE_MAP[a] || a

const loadDetail = async () => {
  if (!props.warehouseId) return
  loading.value = true
  try {
    const { data } = await http.get(`/api/knowledge/warehouses/${props.warehouseId}`)
    detail.value = data
    attachments.value = data?.attachments || []
  } catch { ElMessage.error('加载详情失败') }
  finally { loading.value = false }
}

const loadLogs = async () => {
  if (!props.warehouseId) return
  try {
    const { data } = await http.get(`/api/knowledge/warehouses/${props.warehouseId}/logs?page=${logPage.value - 1}&size=${pageSize}`)
    logs.value = data?.content || []; logTotal.value = data?.totalElements || 0
  } catch {}
}

watch(() => props.modelValue, (v) => { if (v) { loadDetail(); loadLogs() } else { detail.value = null; attachments.value = []; logs.value = [] } })
watch(() => props.warehouseId, () => { if (visible.value) { loadDetail(); loadLogs() } })

const triggerUpload = () => fileInputRef.value?.click()

const handleFileChange = async (e) => {
  const file = e.target.files?.[0]; if (!file) return; e.target.value = ''
  const formData = new FormData(); formData.append('file', file); formData.append('type', uploadType.value)
  try {
    await http.post(`/api/knowledge/warehouses/${props.warehouseId}/attachments`, formData, { headers:{ 'Content-Type':'multipart/form-data' } })
    ElMessage.success('上传成功'); loadDetail()
  } catch (err) { ElMessage.error(err.response?.data?.message || '上传失败') }
}

const downloadAttach = (row) => { window.open(`/api/knowledge/warehouses/${props.warehouseId}/attachments/${row.id}/download`, '_blank') }

const deleteAttach = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除附件「${row.originalFilename}」？`, '删除确认', { type: 'warning' })
    await http.delete(`/api/knowledge/warehouses/${props.warehouseId}/attachments/${row.id}`)
    ElMessage.success('已删除'); loadDetail()
  } catch {}
}

const formatSize = (bytes) => { if (!bytes) return '—'; if (bytes < 1024) return `${bytes}B`; if (bytes < 1048576) return `${(bytes/1024).toFixed(1)}KB`; return `${(bytes/1048576).toFixed(1)}MB` }
</script>

<style scoped lang="scss">
.attach-toolbar { display:flex; align-items:center; margin-bottom:8px }
</style>
