<template>
  <div class="alert-filters-row">
    <div class="filter-group">
      <span class="filter-label">视图：</span>
      <el-radio-group :model-value="viewMode" @update:model-value="$emit('update:viewMode', $event)">
        <el-radio-button value="all">全部告警</el-radio-button>
        <el-radio-button value="unresolved">仅未解决</el-radio-button>
      </el-radio-group>
    </div>
    <div class="filter-group">
      <span class="filter-label">状态：</span>
      <el-select
        :model-value="filters.status"
        @update:model-value="emitFilter('status', $event)"
        placeholder="全部状态"
        clearable
        style="width: 140px"
        :disabled="viewMode === 'unresolved'"
      >
        <el-option label="活动" value="ACTIVE" />
        <el-option label="已确认" value="ACKNOWLEDGED" />
        <el-option label="已解决" value="RESOLVED" />
      </el-select>
    </div>
    <div class="filter-group">
      <span class="filter-label">严重性：</span>
      <el-select
        :model-value="filters.level"
        @update:model-value="emitFilter('level', $event)"
        placeholder="全部级别"
        clearable
        style="width: 140px"
        :disabled="viewMode === 'unresolved'"
      >
        <el-option label="低" value="LOW" />
        <el-option label="中" value="MEDIUM" />
        <el-option label="高" value="HIGH" />
        <el-option label="严重" value="CRITICAL" />
      </el-select>
    </div>
    <el-button @click="$emit('reset')">重置</el-button>
    <el-button type="primary" @click="$emit('search')">查询</el-button>
  </div>
</template>

<script setup>
// 告警历史过滤栏：包含视图切换 + 状态/严重性筛选 + 重置/查询按钮
// 通过 update:viewMode 和 update:filters 事件与父组件通信
defineProps({
  viewMode: { type: String, default: 'all' },
  filters: {
    type: Object,
    default: () => ({ status: '', level: '' })
  }
})

const emit = defineEmits(['update:viewMode', 'update:filters', 'reset', 'search'])

function emitFilter(key, value) {
  // 通知父组件更新 filters 对象的某个字段
  emit('update:filters', { key, value })
}
</script>

<style scoped>
.alert-filters-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}
.filter-group { display: flex; align-items: center; gap: 8px; }
.filter-label { color: var(--el-text-color-regular); font-size: 14px; }
</style>
