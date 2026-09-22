<template>
  <el-dialog
    :model-value="visible"
    :title="template ? `预览: ${template.name}` : '模板预览'"
    width="900px"
    class="preview-dialog"
    @update:model-value="$emit('update:visible', $event)"
  >
    <div v-if="template" class="template-preview">
      <div class="template-meta">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="模板名称">{{ template.name }}</el-descriptions-item>
          <el-descriptions-item label="历史大类">
            <el-tag :type="getCategoryTagType(template.category)" size="small">
              {{ getCategoryLabel(template.category) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="文档类型">{{ template.documentType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="产品类型">{{ template.productType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="行业">{{ template.industry || '-' }}</el-descriptions-item>
          <el-descriptions-item label="版本">v{{ template.version }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatDate(template.updateTime) }}</el-descriptions-item>
          <el-descriptions-item label="下载量">{{ formatNumber(template.downloads) }} 次</el-descriptions-item>
          <el-descriptions-item label="标签" :span="3">
            <el-tag v-for="tag in template.tags" :key="tag" size="small" style="margin-right: 6px">
              {{ tag }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="描述" :span="3">{{ template.description }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <el-tabs :model-value="activeTab" class="preview-tabs" @update:model-value="$emit('update:active-tab', $event)">
        <el-tab-pane label="内容预览" name="content">
          <div class="content-frame">
            <pre class="template-content-text">{{ template.content || '暂无内容' }}</pre>
          </div>
        </el-tab-pane>
        <el-tab-pane label="文件结构" name="structure">
          <el-tree
            :data="template.structure"
            :props="{ label: 'name', children: 'children' }"
            default-expand-all
            class="template-tree"
          >
            <template #default="{ node, data }">
              <span class="tree-node">
                <el-icon><component :is="data.type === 'folder' ? Folder : Document" /></el-icon>
                {{ node.label }}
              </span>
            </template>
          </el-tree>
        </el-tab-pane>
      </el-tabs>

      <div class="preview-actions">
        <el-button @click="$emit('download', template)">下载模板</el-button>
        <el-button @click="$emit('update:visible', false)">关闭</el-button>
        <el-button type="primary" :icon="DocumentAdd" @click="$emit('use-template', template)">
          使用此模板
        </el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { Document, DocumentAdd, Folder } from '@element-plus/icons-vue'
import { formatDate, formatNumber } from './templateLibraryHelpers.js'
import { getCategoryLabel, getCategoryTagType } from '@/config/templateLibrary.js'

defineProps({
  visible: { type: Boolean, default: false },
  template: { type: Object, default: null },
  activeTab: { type: String, default: 'content' }
})

defineEmits(['update:visible', 'update:active-tab', 'use-template', 'download'])
</script>

<style scoped>
.template-meta {
  margin-bottom: 16px;
}

.content-frame {
  max-height: 420px;
  overflow: auto;
  background: #f8fafc;
  border-radius: 12px;
  padding: 16px;
}

.template-content-text {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.65;
}

.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.preview-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
}
</style>
