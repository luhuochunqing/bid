<template>
  <section class="agent-section">
    <header>
      商务条款分类
      <el-badge :value="items?.length || 0" :hidden="!items?.length" type="primary" class="panel-badge">
        <el-button size="small" text :loading="loading" @click="$emit('fetch')">获取商务条款</el-button>
      </el-badge>
    </header>
    <div v-if="items?.length" class="com-list">
      <div v-for="(item, idx) in items" :key="idx" class="com-item">
        <el-tag :type="tagType(item.subType)" size="small" effect="plain">{{ tagLabel(item.subType) }}</el-tag>
        <span class="com-text">{{ item.text }}</span>
      </div>
    </div>
    <div v-else-if="fetched" class="com-empty">
      <el-empty description="未解析出商务条款" :image-size="48" />
    </div>
  </section>
</template>

<script setup>
defineProps({ items: { type: Array, default: () => [] }, loading: { type: Boolean, default: false }, fetched: { type: Boolean, default: false } })
defineEmits(['fetch'])
const tagLabel = (type) => ({ PAYMENT_TERMS: '付款方式', PERFORMANCE_BOND: '履约保证金', DELIVERY_CYCLE: '交付周期', WARRANTY_PERIOD: '质保期', BREACH_LIABILITY: '违约责任', IP_OWNERSHIP: '知识产权' }[type] || type)
const tagType = (type) => ({ PAYMENT_TERMS: 'primary', PERFORMANCE_BOND: 'warning', DELIVERY_CYCLE: 'success', WARRANTY_PERIOD: 'info', BREACH_LIABILITY: 'danger', IP_OWNERSHIP: '' }[type] || 'info')
</script>

<style scoped>
.com-list { display: grid; gap: 8px; }
.com-item { display: flex; align-items: flex-start; gap: 8px; padding: 10px; border-radius: 10px; border: 1px solid var(--el-border-color-light); background: var(--el-bg-color); }
.com-text { line-height: 1.5; color: var(--el-text-color-primary); font-size: 13px; }
.com-empty { padding: 8px 0; }
.panel-badge { display: inline-flex; vertical-align: middle; }
</style>
