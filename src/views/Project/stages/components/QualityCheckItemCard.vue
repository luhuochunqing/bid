<template>
  <div class="check-item-card" :class="item.status?.toLowerCase()">
    <div class="item-card-top">
      <div class="item-info">
        <span class="item-order">{{ item.order }}.</span>
        <span class="item-title">{{ item.title }}</span>
        <el-tag size="small" :type="getSeverityTagType(item.severity)" effect="plain" class="severity-tag">
          {{ formatSeverity(item.severity) }}
        </el-tag>
      </div>
      <div class="item-status-tag">
        <el-tag size="small" :type="getStatusTagType(item.status)" effect="dark">
          {{ formatStatus(item.status) }}
        </el-tag>
      </div>
    </div>

    <!-- 结论与建议 -->
    <div class="item-conclusion">
      <div class="conclusion-text">
        <strong>核查结论：</strong>{{ item.conclusion || '核查完成' }}
      </div>
      <div v-if="item.suggestion" class="suggestion-text">
        <strong>建议操作：</strong>{{ item.suggestion }}
      </div>
    </div>

    <!-- 证据链展开面板 -->
    <div v-if="item.evidences && item.evidences.length > 0" class="evidence-panel">
      <div class="evidence-panel-header">
        <el-icon><Paperclip /></el-icon> 溯源证据链 ({{ item.evidences.length }}条)
      </div>
      <div v-for="(ev, evIdx) in item.evidences" :key="evIdx" class="evidence-item">
        <div class="evidence-source-line">
          <el-tag size="small" :type="getEvidenceSourceTagType(ev.sourceType)" effect="light">
            {{ formatEvidenceSource(ev.sourceType) }}
          </el-tag>
          <el-tag
            v-if="ev.sourceType === 'KNOWLEDGE_BASE' && ev.knowledgeSource"
            size="small"
            type="warning"
            effect="plain"
          >
            {{ formatKnowledgeSource(ev.knowledgeSource) }}
          </el-tag>
          <el-tag
            v-if="ev.sourceType === 'KNOWLEDGE_BASE' && ev.matched !== null && ev.matched !== undefined"
            size="small"
            :type="ev.matched ? 'success' : 'danger'"
            effect="plain"
          >
            {{ ev.matched ? '已匹配' : '未匹配' }}
          </el-tag>
          <span class="evidence-title">{{ ev.sourceTitle }}</span>
          <span v-if="ev.pageNumber" class="evidence-page-badge">第 {{ ev.pageNumber }} 页</span>
          <span v-else class="evidence-page-badge unresolved">未定位到原文页</span>
        </div>
        <div v-if="ev.quote" class="evidence-quote-box">
          <span class="quote-mark">“</span>
          {{ ev.quote }}
          <span class="quote-mark">”</span>
        </div>
        <div v-else class="evidence-quote-box unresolved">未在原文中定位到相关内容，需人工核实</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Paperclip } from '@element-plus/icons-vue'

defineProps({
  item: {
    type: Object,
    required: true
  }
})

function getStatusTagType(status) {
  switch (status) {
    case 'PASS': return 'success'
    case 'FAIL': return 'danger'
    case 'MANUAL_REVIEW': return 'warning'
    default: return 'info'
  }
}

function formatStatus(status) {
  switch (status) {
    case 'PASS': return '通过'
    case 'FAIL': return '不通过'
    case 'MANUAL_REVIEW': return '需人工确认'
    default: return status || '未知'
  }
}

function getSeverityTagType(severity) {
  switch (severity) {
    case 'CRITICAL': return 'danger'
    case 'HIGH': return 'warning'
    case 'MEDIUM': return 'info'
    default: return ''
  }
}

function formatSeverity(severity) {
  switch (severity) {
    case 'CRITICAL': return '一票否决/严重'
    case 'HIGH': return '高风险'
    case 'MEDIUM': return '中等风险'
    default: return severity || '普通'
  }
}

function getEvidenceSourceTagType(sourceType) {
  switch (sourceType) {
    case 'TENDER': return 'danger'
    case 'BID': return 'primary'
    case 'KNOWLEDGE_BASE': return 'success'
    default: return 'info'
  }
}

function formatEvidenceSource(sourceType) {
  switch (sourceType) {
    case 'TENDER': return '招标文件要求'
    case 'BID': return '投标文件原文'
    case 'KNOWLEDGE_BASE': return '企业知识库'
    default: return sourceType
  }
}

function formatKnowledgeSource(knowledgeSource) {
  switch (knowledgeSource) {
    case 'qualification': return '资质库'
    case 'brand-authorization': return '品牌授权库'
    case 'personnel': return '人员库'
    case 'performance': return '业绩库'
    default: return knowledgeSource
  }
}
</script>

<style scoped>
.check-item-card {
  padding: 14px 16px;
  border-bottom: 1px solid #f1f5f9;
}
.check-item-card:last-child {
  border-bottom: none;
}
.item-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.item-info {
  display: flex;
  align-items: center;
  gap: 8px;
}
.item-order {
  font-weight: 700;
  color: #6b7280;
  font-size: 13px;
}
.item-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}
.item-conclusion {
  font-size: 13px;
  line-height: 1.6;
  margin-bottom: 8px;
  color: #374151;
}
.conclusion-text {
  margin-bottom: 4px;
}
.suggestion-text {
  color: #b91c1c;
  background: #fef2f2;
  padding: 6px 10px;
  border-radius: 4px;
  margin-top: 6px;
}
.evidence-panel {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 10px 12px;
  margin-top: 10px;
}
.evidence-panel-header {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}
.evidence-item {
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #cbd5e1;
}
.evidence-item:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}
.evidence-source-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.evidence-title {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}
.evidence-page-badge {
  font-size: 11px;
  color: #0284c7;
  background: #e0f2fe;
  padding: 1px 6px;
  border-radius: 3px;
  font-weight: 600;
}
.evidence-page-badge.unresolved {
  color: #b45309;
  background: #fef3c7;
}
.evidence-quote-box {
  font-size: 12px;
  color: #475569;
  background: #ffffff;
  border-left: 3px solid #0284c7;
  padding: 6px 10px;
  border-radius: 2px;
  line-height: 1.5;
  font-style: italic;
}
.evidence-quote-box.unresolved {
  color: #b45309;
  border-left-color: #f59e0b;
  background: #fffbeb;
  font-style: normal;
}
.quote-mark {
  color: #94a3b8;
  font-weight: bold;
}
</style>
