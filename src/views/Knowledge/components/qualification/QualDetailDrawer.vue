<template>
  <el-drawer
    :model-value="modelValue"
    title="资质详情"
    direction="rtl"
    size="800px"
    :with-header="false"
    :destroy-on-close="false"
    @update:model-value="(val) => $emit('update:modelValue', val)"
  >
    <div class="qual-detail-drawer">
      <!-- 顶部 header：证书名称 + 状态Tag + [编辑] [下架/恢复] [×] -->
      <header class="qd-header">
        <div class="qd-title-row">
          <h3 class="qd-title">{{ qualification?.name || '资质详情' }}</h3>
          <el-tag v-if="qualification" :type="statusTagType(qualification.status)" size="small" class="qd-status-tag" data-testid="qd-status-tag">
            {{ statusLabel(qualification.status) }}
          </el-tag>
        </div>
        <div class="qd-header-actions">
          <el-button
            v-if="canManage && qualification && qualification.status !== 'RETIRED'"
            type="primary"
            link
            data-testid="qd-edit-btn"
            @click="$emit('edit', qualification)"
          >
            <el-icon><Edit /></el-icon> 编辑
          </el-button>
          <el-button
            v-if="canManage && qualification && qualification.status !== 'RETIRED'"
            type="warning"
            link
            data-testid="qd-retire-btn"
            @click="$emit('retire', qualification)"
          >
            <el-icon><Bottom /></el-icon> 下架
          </el-button>
          <el-button
            v-if="canManage && qualification && qualification.status === 'RETIRED'"
            type="success"
            link
            data-testid="qd-restore-btn"
            @click="$emit('restore', qualification)"
          >
            <el-icon><Top /></el-icon> 恢复
          </el-button>
          <el-button
            type="info"
            link
            data-testid="qd-close-btn"
            @click="$emit('update:modelValue', false)"
          >
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </header>

      <el-tabs v-model="activeTab" class="qd-tabs" data-testid="qd-tabs">
        <!-- Tab 1: 基本信息 -->
        <el-tab-pane label="基本信息" name="basic" data-testid="qd-tab-basic">
          <el-descriptions v-if="qualification" :column="2" border class="qd-desc">
            <el-descriptions-item label="证书名称">{{ qualification.name || '—' }}</el-descriptions-item>
            <el-descriptions-item label="等级">{{ qualification.level || '—' }}</el-descriptions-item>
            <el-descriptions-item label="认证机构">{{ qualification.issuer || '—' }}</el-descriptions-item>
            <el-descriptions-item label="代理机构">{{ qualification.agency || '—' }}</el-descriptions-item>
            <el-descriptions-item label="证书号">{{ qualification.certificateNo || '—' }}</el-descriptions-item>
            <el-descriptions-item label="代理联系方式">{{ qualification.agencyContact || '—' }}</el-descriptions-item>
            <el-descriptions-item label="发证日期">{{ formatDate(qualification.issueDate) }}</el-descriptions-item>
            <el-descriptions-item label="证书有效期">{{ formatDate(qualification.expiryDate) }}</el-descriptions-item>
            <el-descriptions-item label="持证人">{{ qualification.holderName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="证书状态">{{ statusLabel(qualification.status) }}</el-descriptions-item>
            <el-descriptions-item label="认证范围" :span="2">
              <pre class="qd-scope">{{ qualification.certScope || '—' }}</pre>
            </el-descriptions-item>
            <el-descriptions-item label="证书审核提醒" :span="2">
              {{ qualification.certReviewNote || '—' }}
            </el-descriptions-item>
            <el-descriptions-item v-if="qualification?.status === 'retired'" label="下架原因" :span="2">
              {{ qualification.retireReason || '—' }}
            </el-descriptions-item>
          </el-descriptions>

          <!-- 附件区 -->
          <section class="qd-attachments" data-testid="qd-attachments">
            <h4 class="qd-section-title">附件</h4>
            <template v-if="attachments && attachments.length">
              <div
                v-for="att in attachments"
                :key="att.id || att.fileUrl"
                class="qd-attachment-card"
                data-testid="qd-attachment-card"
              >
                <div class="qd-att-icon">
                  <el-icon :size="28"><Document /></el-icon>
                </div>
                <div class="qd-att-info">
                  <div class="qd-att-name">{{ att.fileName || '附件' }}</div>
                  <div class="qd-att-meta">
                    <span v-if="att.fileSize">{{ formatSize(att.fileSize) }} · </span>
                    <span>{{ att.uploadedAt || '—' }}</span>
                  </div>
                </div>
                <div class="qd-att-actions">
                  <el-button size="small" link type="primary" data-testid="qd-att-preview" @click="$emit('preview', att)">预览</el-button>
                  <el-button size="small" link type="primary" data-testid="qd-att-download" @click="handleDownload(att)">下载</el-button>
                  <el-button v-if="canManage" size="small" link type="warning" data-testid="qd-att-replace" @click="$emit('replace', att)">替换</el-button>
                  <el-button v-if="canManage" size="small" link type="danger" data-testid="qd-att-delete" @click="$emit('delete', att)">删除</el-button>
                </div>
              </div>
            </template>
            <el-empty v-else description="暂无附件" :image-size="80" data-testid="qd-attachment-empty">
              <el-button v-if="canManage" type="primary" plain data-testid="qd-att-upload" @click="$emit('upload')">
                <el-icon><Upload /></el-icon> 上传附件
              </el-button>
            </el-empty>
          </section>
        </el-tab-pane>

        <!-- Tab 2: 操作日志 (4.1.3.7 实现) -->
        <el-tab-pane label="操作日志" name="audit" data-testid="qd-tab-audit">
          <OperationLogTab :qualification-id="qualification?.id" />
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref } from 'vue'
import { Edit, Bottom, Top, Close, Document, Upload } from '@element-plus/icons-vue'
import { formatDate, qualificationStatusTagTypes, qualificationStatusLabels } from './qualificationMeta.js'
import OperationLogTab from '@/components/qualification/OperationLogTab.vue'

const STATUS_LABELS = { ...qualificationStatusLabels, valid: '在库', expiring: '即将到期', expired: '已过期', retired: '已下架' }

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  qualification: { type: Object, default: null },
  attachments: { type: Array, default: () => [] },
  canManage: { type: Boolean, default: false }
})

const emit = defineEmits([
  'update:modelValue', 'edit', 'retire', 'restore',
  'preview', 'download', 'replace', 'delete', 'upload'
])

const activeTab = ref('basic')

const statusLabel = (s) => STATUS_LABELS[s] || s || '—'
const statusTagType = (s) => qualificationStatusTagTypes[s] || 'info'

const handleDownload = (att) => {
  // 蓝图 4.1.3.6: 下载文件名 = 证书名称 + 证书编号 + 原文件名
  const certName = props.qualification?.name || '证书'
  const certNo = props.qualification?.certificateNo || ''
  const fileName = `${certName}_${certNo}_${att.fileName || '附件'}`
  emit('download', { ...att, fileName })
}

const formatSize = (bytes) => {
  if (!bytes) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
</script>

<style scoped lang="scss">
.qual-detail-drawer {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.qd-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  .qd-title-row {
    display: flex;
    align-items: center;
    gap: 12px;
    min-width: 0;
  }
  .qd-title {
    font-size: 18px;
    font-weight: 600;
    color: var(--el-text-color-primary);
    margin: 0;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .qd-status-tag { flex-shrink: 0; }
  .qd-header-actions { display: flex; align-items: center; gap: 4px; }
}

.qd-tabs {
  flex: 1;
  padding: 16px 24px 0;
  overflow: auto;
}

.qd-desc {
  margin-bottom: 24px;
}

.qd-scope {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.qd-section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 0 0 12px;
}

.qd-attachment-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  margin-bottom: 8px;
  transition: all 0.2s;
  &:hover { box-shadow: 0 2px 8px rgba(0,0,0,.05); }
}

.qd-att-icon {
  color: var(--el-color-primary);
  flex-shrink: 0;
}

.qd-att-info {
  flex: 1;
  min-width: 0;
}

.qd-att-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.qd-att-meta {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  margin-top: 4px;
}

.qd-att-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

</style>
