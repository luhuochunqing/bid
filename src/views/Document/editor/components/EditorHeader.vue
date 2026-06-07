<template>
  <div class="editor-header">
    <div class="header-left">
      <el-button :icon="ArrowLeft" @click="$emit('back')">返回</el-button>
      <div class="title-section">
        <h2 class="document-title">{{ projectName }} - 标书编辑器</h2>
        <el-tag size="small" type="info">{{ templateName }}</el-tag>
      </div>
    </div>
    <div class="header-actions">
      <el-button :icon="View" @click="$emit('preview')">预览</el-button>
      <el-button v-if="canExport" :icon="Download" @click="$emit('export')">导出</el-button>
      <el-button v-if="canArchive" :icon="DocumentChecked" @click="$emit('archive')">归档</el-button>
      <el-button type="primary" :icon="Check" @click="$emit('save')">保存</el-button>
    </div>
  </div>
</template>

<script setup>
import {
  ArrowLeft,
  View,
  Download,
  DocumentChecked,
  Check
} from '@element-plus/icons-vue'

defineProps({
  projectName: { type: String, default: '' },
  templateName: { type: String, default: '' },
  canExport: { type: Boolean, default: false },
  canArchive: { type: Boolean, default: false }
})

defineEmits(['back', 'preview', 'export', 'archive', 'save'])
</script>

<style scoped>
.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--gray-250);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.title-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.document-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--gray-750);
}

.header-actions {
  display: flex;
  gap: 12px;
}

.header-actions .el-button {
  min-width: 90px;
  height: 36px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  transition: all 200ms cubic-bezier(0.4, 0, 0.2, 1);
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.header-actions .el-button--primary {
  background: linear-gradient(135deg, var(--brand-xiyu-logo, #2E7659), var(--brand-xiyu-logo-hover, #27674E));
  border: none;
  box-shadow: 0 2px 8px var(--brand-xiyu-logo-shadow, rgba(46, 118, 89, 0.24));
}

.header-actions .el-button--primary:hover {
  background: linear-gradient(135deg, #367F61, var(--brand-xiyu-logo-active, #1F553F));
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(46, 118, 89, 0.3);
}

.header-actions .el-button--primary:active {
  transform: translateY(0);
}

.header-actions .el-button--default {
  border: 1.5px solid #e5e7eb;
  color: var(--text-slate);
}

.header-actions .el-button--default:hover {
  border-color: var(--gray-400);
  color: #1e293b;
  background: #f8fafc;
}

.editor-header .el-button {
  height: 36px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
}

:deep(.el-tag) {
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  padding: 4px 10px;
  border: none;
}

:deep(.el-tag--info) {
  background: linear-gradient(135deg, var(--text-slate), var(--sidebar-text-secondary));
  color: var(--bg-card);
}
</style>
