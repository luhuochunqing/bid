<template>
  <div class="center-panel">
    <el-card shadow="never" class="editor-card">
      <template #header>
        <div class="editor-header-bar">
          <span class="section-title">{{ currentSection?.name || '请选择章节' }}</span>
          <div class="editor-tools">
            <el-button-group size="small">
              <el-button :icon="ZoomOut" @click="$emit('zoom-out')" />
              <el-button>{{ zoomLevel }}%</el-button>
              <el-button :icon="ZoomIn" @click="$emit('zoom-in')" />
            </el-button-group>
          </div>
        </div>
      </template>

      <div v-if="currentSection" class="editor-content">
        <textarea
          v-model="currentSection.content"
          class="content-textarea"
          :style="{ fontSize: baseFontSize * zoomLevel / 100 + 'px' }"
          placeholder="在此处编辑内容..."
          @input="$emit('content-change')"
        />

        <div v-if="sources.length > 0" class="source-records">
          <div class="source-records-header">来源记录</div>
          <div class="source-record-list">
            <el-tag
              v-for="(source, index) in sources"
              :key="`${source.kind || 'source'}-${index}`"
              class="source-record-tag"
              type="info"
              effect="plain"
            >
              {{ source.sourceLabel || source.kind || '来源' }} · {{ source.title }}
            </el-tag>
          </div>
        </div>

        <KnowledgeFloatPanel
          v-if="knowledgeMatches.length > 0"
          :matches="knowledgeMatches"
          @insert="(match) => $emit('insert-knowledge', match)"
        />
      </div>

      <div v-else class="empty-state">
        <el-icon :size="48" color="#c0c4cc"><Document /></el-icon>
        <p>请从左侧选择章节进行编辑</p>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ZoomIn, ZoomOut, Document } from '@element-plus/icons-vue'
import KnowledgeFloatPanel from './KnowledgeFloatPanel.vue'

defineModel('currentSection', { type: Object, default: null })
defineProps({
  zoomLevel: { type: Number, default: 100 },
  baseFontSize: { type: Number, default: 14 },
  sources: { type: Array, default: () => [] },
  knowledgeMatches: { type: Array, default: () => [] }
})

defineEmits(['zoom-in', 'zoom-out', 'content-change', 'insert-knowledge'])
</script>

<style scoped>
.center-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.editor-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.editor-card :deep(.el-card__body) {
  flex: 1;
  overflow: hidden;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.editor-header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title { font-weight: 600; color: var(--gray-750); }

.editor-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

.content-textarea {
  flex: 1;
  width: 100%;
  resize: none;
  outline: none;
  padding: 20px;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  line-height: 1.8;
  color: var(--gray-750);
  background: var(--bg-card);
  border-radius: 8px;
  border: 1.5px solid #e5e7eb;
}

.content-textarea:focus {
  outline: none;
  border-color: #e5e7eb;
  box-shadow: none;
}

.content-textarea::placeholder { color: #c0c4cc; }

.source-records {
  padding: 0 20px 16px;
  border-top: 1px solid #f0f0f0;
  background: var(--bg-card);
}

.source-records-header {
  padding: 12px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary-ui);
}

.source-record-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.source-record-tag { max-width: 100%; }

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
}

.empty-state p {
  margin-top: 16px;
  font-size: 14px;
}

:deep(.el-button-group) {
  border-radius: 8px;
  overflow: hidden;
}

:deep(.el-button-group .el-button) { border-radius: 0; }

:deep(.el-button-group .el-button:first-child) {
  border-top-left-radius: 8px;
  border-bottom-left-radius: 8px;
}

:deep(.el-button-group .el-button:last-child) {
  border-top-right-radius: 8px;
  border-bottom-right-radius: 8px;
}

:deep(.el-tag--info) {
  background: linear-gradient(135deg, var(--text-slate), var(--sidebar-text-secondary));
  color: var(--bg-card);
  border-radius: 6px;
  font-size: 12px;
  padding: 4px 10px;
  border: none;
}

@media (max-width: 1200px) {
  .center-panel { min-height: 500px; }
}
</style>
