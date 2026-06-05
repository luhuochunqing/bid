<!-- Input: Workbench CustomerFollowUpList props and user actions
Output: presentational Workbench CustomerFollowUpList section
Pos: src/views/Dashboard/components/ - Dashboard display components
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <div class="section-card customers-card">
    <div class="section-header">
      <h3 class="section-title">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="section-icon">
          <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/>
          <circle cx="9" cy="7" r="4"/>
        </svg>
        {{ title }}
      </h3>
    </div>
    <EmptyState
      v-if="customers.length === 0"
      icon="客"
      title="暂无客户跟进"
      description="需要回访或跟进的客户会显示在这里。"
    />
    <div v-else class="customers-list">
      <div v-for="customer in customers" :key="customer.id" class="customer-item">
        <div class="customer-avatar">{{ customer.name?.charAt(0) }}</div>
        <div class="customer-info">
          <span class="customer-name">{{ customer.name }}</span>
          <span class="customer-company">{{ customer.company }}</span>
        </div>
        <div class="customer-status">
          <el-tag :type="customer.statusType" size="small">{{ customer.status }}</el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import EmptyState from './EmptyState.vue'

defineProps({
  title: { type: String, default: '客户跟进' },
  customers: { type: Array, default: () => [] },
})
</script>
