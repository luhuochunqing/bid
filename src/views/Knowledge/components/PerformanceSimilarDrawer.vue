<template>
  <el-drawer v-model="visible" title="相似业绩推荐" size="560px">
    <div v-loading="loading" class="similar-drawer">
      <el-empty v-if="!loading && records.length === 0" description="暂无相似业绩" />
      <div v-else class="similar-list">
        <div
          v-for="(row, idx) in records"
          :key="row.id"
          class="similar-card"
          :class="{ 'similar-highlight': row._similarScore >= 3 }"
        >
          <div class="similar-header">
            <span class="similar-rank">#{{ idx + 1 }}</span>
            <el-tag v-if="row._similarScore >= 3" type="success" size="small">高度相似</el-tag>
            <el-tag v-else-if="row._similarScore >= 1" type="warning" size="small">部分相似</el-tag>
            <el-tag v-else type="info" size="small">弱相关</el-tag>
          </div>
          <p class="similar-name">{{ row.contractName }}</p>
          <p class="similar-entity">签约单位：{{ row.signingEntity }}</p>
          <div class="similar-meta">
            <el-tag :type="getCustomerTypeTagType(row.customerType)" size="small">{{ row.customerTypeLabel }}</el-tag>
            <el-tag type="info" size="small">{{ row.projectTypeLabel }}</el-tag>
            <span class="similar-date">{{ row.signingDate }} ~ {{ row.expiryDate }}</span>
          </div>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: Boolean,
  records: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const getCustomerTypeTagType = (t) => t === 'CENTRAL_SOE' ? 'danger' : t === 'LOCAL_SOE' ? 'warning' : t === 'GOVERNMENT_INSTITUTION' ? 'success' : 'primary'
</script>

<style scoped>
.similar-drawer { min-height: 200px; }
.similar-list { display: flex; flex-direction: column; gap: 12px; }
.similar-card { background: var(--el-bg-color); border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 12px 14px; }
.similar-highlight { border-left: 3px solid var(--el-color-success); }
.similar-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.similar-rank { font-weight: 700; color: var(--el-text-color-secondary); font-size: 13px; }
.similar-name { font-weight: 600; font-size: 14px; color: var(--el-text-color-primary); margin: 0 0 4px; }
.similar-entity { font-size: 13px; color: var(--el-text-color-regular); margin: 0 0 8px; }
.similar-meta { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.similar-date { font-size: 12px; color: var(--el-text-color-secondary); margin-left: auto; }
</style>
