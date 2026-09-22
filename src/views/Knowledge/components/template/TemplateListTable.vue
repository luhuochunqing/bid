<template>
  <el-card class="table-card" shadow="never">
    <div class="table-toolbar">
      <div>
        <p class="toolbar-label">模板资产列表</p>
        <h3 class="toolbar-title">优先查看正式三维分类，再决定是否进入历史大类视图</h3>
      </div>
      <div class="toolbar-meta">
        <span class="meta-item">结果 {{ total }}</span>
        <span class="meta-item">每页 {{ pageSize }}</span>
      </div>
    </div>

    <div v-if="!isMobile" class="desktop-table">
      <el-table v-loading="loading" :data="templates" stripe style="width: 100%">
      <el-table-column prop="name" label="模板名称" min-width="220">
        <template #default="{ row }">
          <div class="name-cell">
            <el-icon class="category-icon" :color="getCategoryColor(row.category)">
              <component :is="resolveCategoryIcon(row.category)" />
            </el-icon>
            <div class="name-content">
              <span class="name-text">{{ row.name }}</span>
              <span class="name-desc">{{ row.description }}</span>
              <div class="classification-priority">
                <span v-if="row.productType" class="primary-pill">{{ row.productType }}</span>
                <span v-if="row.industry" class="primary-pill is-success">{{ row.industry }}</span>
                <span v-if="row.documentType" class="primary-pill is-warning">{{ row.documentType }}</span>
              </div>
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="category" label="历史大类" width="120">
        <template #default="{ row }">
          <el-tag :type="getCategoryTagType(row.category)" size="small">
            {{ getCategoryLabel(row.category) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="正式分类" min-width="260">
        <template #default="{ row }">
          <div class="classification-cell">
            <el-tag v-if="row.productType" size="small" effect="plain">{{ row.productType }}</el-tag>
            <el-tag v-if="row.industry" size="small" effect="plain" type="success">{{ row.industry }}</el-tag>
            <el-tag v-if="row.documentType" size="small" effect="plain" type="warning">{{ row.documentType }}</el-tag>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="tags" label="标签" min-width="180">
        <template #default="{ row }">
          <div class="classification-cell">
            <el-tag v-for="tag in row.tags.slice(0, 3)" :key="tag" size="small" effect="plain">
              {{ tag }}
            </el-tag>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="downloads" label="下载量" width="120">
        <template #default="{ row }">{{ formatNumber(row.downloads) }}</template>
      </el-table-column>

      <el-table-column prop="updateTime" label="更新时间" width="120">
        <template #default="{ row }">{{ formatDate(row.updateTime) }}</template>
      </el-table-column>

      <el-table-column prop="version" label="版本" width="90">
        <template #default="{ row }">
          <el-tag type="info" size="small">v{{ row.version }}</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link :icon="View" size="small" @click="$emit('preview', row)">预览</el-button>
          <el-button type="success" link :icon="DocumentAdd" size="small" @click="$emit('use-template', row)">
            一键使用
          </el-button>
          <el-dropdown @command="(command) => $emit('more-action', command, row)">
            <el-button type="info" link :icon="MoreFilled" size="small">更多</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit" :icon="Edit">编辑模板</el-dropdown-item>
                <el-dropdown-item command="copy" :icon="CopyDocument">复制模板</el-dropdown-item>
                <el-dropdown-item command="version" :icon="Clock">版本历史</el-dropdown-item>
                <el-dropdown-item command="download" :icon="Download">下载</el-dropdown-item>
                <el-dropdown-item command="delete" :icon="Delete" divided>删除模板</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
      </el-table>
    </div>

    <div v-else class="mobile-list">
      <article v-for="row in templates" :key="row.id" class="mobile-card">
        <div class="mobile-card-head">
          <div>
            <h4 class="mobile-name">{{ row.name }}</h4>
            <p class="mobile-desc">{{ row.description }}</p>
          </div>
          <el-tag size="small" :type="getCategoryTagType(row.category)">
            {{ getCategoryLabel(row.category) }}
          </el-tag>
        </div>

        <div class="mobile-classification">
          <span v-if="row.productType" class="primary-pill">{{ row.productType }}</span>
          <span v-if="row.industry" class="primary-pill is-success">{{ row.industry }}</span>
          <span v-if="row.documentType" class="primary-pill is-warning">{{ row.documentType }}</span>
        </div>

        <div class="mobile-meta">
          <span>版本 v{{ row.version }}</span>
          <span>下载 {{ formatNumber(row.downloads) }}</span>
          <span>更新 {{ formatDate(row.updateTime) }}</span>
        </div>

        <div class="mobile-tags">
          <el-tag v-for="tag in row.tags.slice(0, 3)" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
        </div>

        <div class="mobile-actions">
          <el-button type="primary" @click="$emit('preview', row)">预览</el-button>
          <el-button type="success" plain @click="$emit('use-template', row)">使用</el-button>
          <el-dropdown @command="(command) => $emit('more-action', command, row)">
            <el-button plain>更多</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit" :icon="Edit">编辑模板</el-dropdown-item>
                <el-dropdown-item command="copy" :icon="CopyDocument">复制模板</el-dropdown-item>
                <el-dropdown-item command="version" :icon="Clock">版本历史</el-dropdown-item>
                <el-dropdown-item command="download" :icon="Download">下载</el-dropdown-item>
                <el-dropdown-item command="delete" :icon="Delete" divided>删除模板</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </article>
    </div>

    <div class="pagination-wrapper">
      <el-pagination
        :current-page="page"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @update:current-page="$emit('update:page', $event)"
        @update:page-size="$emit('update:page-size', $event)"
      />
    </div>
  </el-card>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import {
  Clock,
  CopyDocument,
  Delete,
  Document,
  DocumentAdd,
  DocumentCopy,
  Download,
  Edit,
  Medal,
  MoreFilled,
  Notebook,
  Operation,
  Tickets,
  View
} from '@element-plus/icons-vue'
import { formatDate, formatNumber } from './templateLibraryHelpers.js'
import {
  getCategoryColor,
  getCategoryLabel,
  getCategoryTagType,
  normalizeTemplateCategory
} from '@/config/templateLibrary.js'

const iconMap = {
  technical: Document,
  commercial: DocumentCopy,
  implementation: Operation,
  quotation: Tickets,
  qualification: Medal,
  contract: Notebook
}

defineProps({
  templates: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  page: { type: Number, required: true },
  pageSize: { type: Number, required: true },
  total: { type: Number, required: true }
})

defineEmits(['preview', 'use-template', 'more-action', 'update:page', 'update:page-size'])

const isMobile = ref(false)

const syncViewportMode = () => {
  if (typeof window === 'undefined') return
  isMobile.value = window.innerWidth < 768
}

onMounted(() => {
  syncViewportMode()
  window.addEventListener('resize', syncViewportMode)
})

onUnmounted(() => {
  window.removeEventListener('resize', syncViewportMode)
})

function resolveCategoryIcon(category) {
  return iconMap[normalizeTemplateCategory(category)] || Document
}
</script>

<style scoped>
.table-card {
  margin-top: 16px;
  border-radius: 20px;
  border: 1px solid #dde8f5;
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.toolbar-label {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #335d92;
}

.toolbar-title {
  margin: 0;
  font-size: 20px;
  color: #1f2937;
}

.toolbar-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.meta-item {
  display: inline-flex;
  min-height: 36px;
  align-items: center;
  padding: 0 12px;
  border-radius: 999px;
  background: #f4f8fd;
  color: #52627a;
  font-size: 13px;
}

.name-cell {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.category-icon {
  margin-top: 2px;
}

.name-content {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.name-text {
  font-weight: 600;
  color: var(--gray-950);
}

.name-desc {
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.4;
}

.classification-priority {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.primary-pill {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: #edf5ff;
  color: #225a9b;
  font-size: 12px;
  font-weight: 600;
}

.primary-pill.is-success {
  background: #edf9f1;
  color: #287847;
}

.primary-pill.is-warning {
  background: #fff4e8;
  color: #b25a12;
}

.classification-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.mobile-list {
  display: none;
}

.mobile-card {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid #e3ebf5;
  background: linear-gradient(180deg, var(--bg-card) 0%, #f9fbff 100%);
}

.mobile-card + .mobile-card {
  margin-top: 12px;
}

.mobile-card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.mobile-name {
  margin: 0;
  font-size: 16px;
  color: var(--gray-950);
}

.mobile-desc {
  margin: 6px 0 0;
  color: var(--gray-650);
  font-size: 13px;
  line-height: 1.5;
}

.mobile-classification,
.mobile-meta,
.mobile-tags,
.mobile-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.mobile-meta {
  color: #52627a;
  font-size: 13px;
}

.mobile-actions :deep(.el-button) {
  min-height: 44px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

@media (max-width: 900px) {
  .table-toolbar {
    flex-direction: column;
  }

  .desktop-table {
    display: none;
  }

  .mobile-list {
    display: block;
  }

  .pagination-wrapper {
    justify-content: flex-start;
  }
}
</style>
