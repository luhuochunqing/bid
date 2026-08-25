<template>
  <div class="check-item-row" @click="emit('detail', item)">
    <div class="row-main">
      <div class="row-name-line">
        <span class="item-icon" :class="statusKey">{{ statusMeta.icon }}</span>
        <span class="item-title">{{ item.title }}</span>
      </div>
      <div v-if="conclusionText" class="row-conclusion">{{ conclusionText }}</div>
    </div>
    <el-tag size="small" :type="statusMeta.tagType" effect="plain" class="row-status-tag">
      {{ statusMeta.text }}
    </el-tag>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { stripReasonCode, getStatusMeta } from '../composables/qualityCheckDetail.js'

const props = defineProps({
  item: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['detail'])

const statusMeta = computed(() => getStatusMeta(props.item.status))
const statusKey = computed(() => props.item.status?.toLowerCase() || '')

const conclusionText = computed(() => stripReasonCode(props.item.conclusion))
</script>

<style scoped>
.check-item-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  transition: background-color 0.12s;
}
.check-item-row:last-child {
  border-bottom: none;
}
.check-item-row:hover {
  background: #f0f7f4;
}
.row-main {
  flex: 1;
  min-width: 0;
}
.row-name-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.item-icon {
  width: 14px;
  text-align: center;
  font-size: 12px;
  flex-shrink: 0;
}
.item-icon.pass { color: #16a34a; }
.item-icon.fail { color: #dc2626; }
.item-icon.manual_review { color: #d97706; }
.item-title {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
}
.row-conclusion {
  font-size: 12px;
  line-height: 1.6;
  color: #6b7280;
  margin-top: 4px;
  padding-left: 22px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.row-status-tag {
  flex-shrink: 0;
}
</style>
