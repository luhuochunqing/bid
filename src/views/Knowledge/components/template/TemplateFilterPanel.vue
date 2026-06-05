<template>
  <el-card class="search-card" shadow="never">
    <div class="panel-head">
      <div>
        <p class="eyebrow">正式分类工作区</p>
        <h3 class="panel-title">先按产品类型、行业、文档类型建立检索视图</h3>
        <p class="panel-description">历史页签只保留为辅助视图，正式筛选与关键词检索全部走真实 API。</p>
      </div>
      <div class="panel-tip">
        <span class="tip-label">本地能力</span>
        <span class="tip-value">标签 / 排序 / 分页</span>
      </div>
    </div>

    <el-form :model="filters" class="template-filter-form" @submit.prevent="$emit('search')">
      <el-form-item label="模板名称" class="field-name">
        <el-input
          v-model="filters.name"
          aria-label="模板名称"
          placeholder="搜索模板名称"
          clearable
          :prefix-icon="Search"
          class="field-control"
        />
      </el-form-item>
      <el-form-item label="产品类型">
        <el-select
          v-model="filters.productType"
          aria-label="产品类型"
          placeholder="选择产品类型"
          clearable
          :clear-icon="LegacyClearIcon"
          class="field-control"
        >
          <el-option v-for="option in productTypeOptions" :key="option" :label="option" :value="option" />
        </el-select>
      </el-form-item>
      <el-form-item label="行业">
        <el-select
          v-model="filters.industry"
          aria-label="行业"
          placeholder="选择行业"
          clearable
          :clear-icon="LegacyClearIcon"
          class="field-control"
        >
          <el-option v-for="option in industryOptions" :key="option" :label="option" :value="option" />
        </el-select>
      </el-form-item>
      <el-form-item label="文档类型">
        <el-select
          v-model="filters.documentType"
          aria-label="文档类型"
          placeholder="选择文档类型"
          clearable
          :clear-icon="LegacyClearIcon"
          class="field-control"
        >
          <el-option v-for="option in documentTypeOptions" :key="option" :label="option" :value="option" />
        </el-select>
      </el-form-item>
      <el-form-item label="标签">
        <el-select
          v-model="filters.tags"
          placeholder="选择标签"
          multiple
          collapse-tags
          clearable
          class="field-control"
        >
          <el-option v-for="tag in allTags" :key="tag" :label="tag" :value="tag" />
        </el-select>
      </el-form-item>
      <el-form-item label="排序">
        <el-select v-model="filters.sort" aria-label="排序" placeholder="默认排序" class="field-control">
          <el-option label="默认排序" value="default" />
          <el-option label="下载量" value="downloads" />
          <el-option label="更新时间" value="updateTime" />
          <el-option label="名称" value="name" />
        </el-select>
      </el-form-item>
      <el-form-item class="action-group">
        <el-button
          type="primary"
          :icon="Search"
          class="action-button"
          aria-label="搜索"
          @click="$emit('search')"
        >
          搜索
        </el-button>
        <el-button class="action-button" aria-label="重置" @click="$emit('reset')">重置</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { CircleClose, Search } from '@element-plus/icons-vue'
import { h } from 'vue'
import { ElIcon } from 'element-plus'

const LegacyClearIcon = {
  name: 'LegacyClearIcon',
  render() {
    return h(
      ElIcon,
      { class: 'el-icon-circle-close' },
      () => h(CircleClose)
    )
  }
}

defineModel('filters', { type: Object, required: true })
defineProps({
  allTags: { type: Array, default: () => [] },
  productTypeOptions: { type: Array, default: () => [] },
  industryOptions: { type: Array, default: () => [] },
  documentTypeOptions: { type: Array, default: () => [] }
})

defineEmits(['search', 'reset'])
</script>

<style scoped>
.search-card {
  border-radius: 20px;
  border: 1px solid #d6e7f7;
  background:
    radial-gradient(circle at top right, rgba(64, 158, 255, 0.16), transparent 22%),
    linear-gradient(180deg, var(--bg-card) 0%, #f8fbff 100%);
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #335d92;
}

.panel-title {
  margin: 0;
  color: #1f2937;
  font-size: 22px;
  line-height: 1.35;
}

.panel-description {
  margin: 8px 0 0;
  color: #52627a;
  line-height: 1.6;
}

.panel-tip {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 148px;
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid #dce9f5;
}

.tip-label {
  font-size: 12px;
  color: var(--gray-650);
}

.tip-value {
  margin-top: 4px;
  font-weight: 600;
  color: #1f2937;
}

.template-filter-form {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 12px 16px;
}

.template-filter-form :deep(.el-form-item) {
  grid-column: span 3;
  margin-bottom: 0;
}

.field-name {
  grid-column: span 6;
}

.action-group {
  grid-column: span 12;
}

.field-control {
  width: 100%;
}

.action-button {
  min-height: 44px;
}

@media (max-width: 1200px) {
  .template-filter-form {
    grid-template-columns: repeat(8, minmax(0, 1fr));
  }

  .template-filter-form :deep(.el-form-item) {
    grid-column: span 4;
  }

  .field-name {
    grid-column: span 8;
  }
}

@media (max-width: 768px) {
  .panel-head {
    flex-direction: column;
  }

  .template-filter-form {
    grid-template-columns: repeat(1, minmax(0, 1fr));
  }

  .template-filter-form :deep(.el-form-item),
  .field-name,
  .action-group {
    grid-column: span 1;
  }
}
</style>
