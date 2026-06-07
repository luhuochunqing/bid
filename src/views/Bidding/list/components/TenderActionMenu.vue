<template>
  <div class="table-actions">
    <ElTooltip content="查看详情" placement="top">
      <ElButton size="small" :icon="View" aria-label="查看详情" @click="$emit('view-detail', row.id)" />
    </ElTooltip>
    <ElTooltip v-if="showAiEntry && shouldShowAiButton" content="AI分析" placement="top">
      <ElButton size="small" :icon="MagicStick" aria-label="AI分析" @click="$emit('ai-analysis', row.id)" />
    </ElTooltip>
    <ElTooltip v-if="row.status === 'TRACKING'" content="立即评估" placement="top">
      <ElButton size="small" :icon="Document" aria-label="立即评估" @click="$emit('evaluate', row.id)" />
    </ElTooltip>
    <ElTooltip v-if="row.status === 'EVALUATED'" content="参与投标" placement="top">
      <ElButton size="small" :icon="Document" aria-label="参与投标" @click="$emit('participate', row.id)" />
    </ElTooltip>
    <ElTooltip v-if="canTransfer && (row.status === 'TRACKING' || row.status === 'EVALUATED')" content="转派" placement="top">
      <ElButton size="small" aria-label="转派" @click="$emit('transfer', row)">转派</ElButton>
    </ElTooltip>
    <ElDropdown v-if="hasMoreActions" trigger="click">
      <ElButton size="small" :icon="MoreFilled" aria-label="更多操作" />
      <template #dropdown>
        <ElDropdownMenu>
          <template v-if="row.status === 'PENDING_ASSIGNMENT'">
            <ElDropdownItem @click="$emit('distribute', row)">分发</ElDropdownItem>
            <ElDropdownItem @click="$emit('claim', row)">领取</ElDropdownItem>
            <ElDropdownItem @click="$emit('assign', row)">指派</ElDropdownItem>
          </template>
          <template v-if="row.status === 'TRACKING'">
            <ElDropdownItem v-if="canTransfer" @click="$emit('transfer', row)">转派</ElDropdownItem>
            <ElDropdownItem @click="$emit('evaluate', row)">立即评估</ElDropdownItem>
          </template>
          <template v-if="row.status === 'EVALUATED'">
            <ElDropdownItem v-if="canTransfer" @click="$emit('transfer', row)">转派</ElDropdownItem>
            <ElDropdownItem @click="$emit('participate', row)">立即投标</ElDropdownItem>
          </template>
          <template v-if="row.status === 'BIDDING'">
            <ElDropdownItem @click="$emit('status-change', row, 'WON')">登记中标</ElDropdownItem>
            <ElDropdownItem @click="$emit('status-change', row, 'LOST')">登记未中标</ElDropdownItem>
          </template>
          
          <ElDropdownItem v-if="row.registrationDeadline || row.bidOpeningTime" @click="$emit('set-reminder', row)">
            提醒设置
          </ElDropdownItem>
          
          <ElDropdownItem divided @click="$emit('status-change', row, 'ABANDONED')">标记为已放弃</ElDropdownItem>
          <ElDropdownItem v-if="canDeleteTenders" divided class="danger-item" @click="$emit('delete', row)">
            删除
          </ElDropdownItem>
        </ElDropdownMenu>
      </template>
    </ElDropdown>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { ElButton, ElDropdown, ElDropdownItem, ElDropdownMenu, ElTooltip } from 'element-plus'
import { Document, MagicStick, MoreFilled, View } from '@element-plus/icons-vue'

const props = defineProps({
  row: { type: Object, required: true },
  canManageTenders: { type: Boolean, default: false },
  canDeleteTenders: { type: Boolean, default: false },
  showAiEntry: { type: Boolean, default: true },
  isAdmin: { type: Boolean, default: false },
  canTransfer: { type: Boolean, default: false },
})

defineEmits([
  'view-detail',
  'ai-analysis',
  'participate',
  'claim',
  'assign',
  'evaluate',
  'status-change',
  'delete',
  'set-reminder',
  'transfer',
])

const containerWidth = ref(320)
const resizeObserver = ref(null)

const canEdit = computed(() => {
  // 项目经理可以编辑自己跟踪的标讯
  return props.row.status === 'TRACKING'
})

const hasMoreActions = computed(() => props.canManageTenders || props.canDeleteTenders || canEdit.value || props.isAdmin)

const shouldShowAiButton = computed(() => {
  const width = containerWidth.value
  if (props.showAiEntry) {
    if (hasMoreActions.value) {
      return width >= 310
    }
    return width >= 260
  }
  return false
})

const shouldShowParticipateButton = computed(() => {
  const width = containerWidth.value
  if (props.showAiEntry && shouldShowAiButton.value) {
    return width >= 390
  }
  if (props.showAiEntry) {
    return width >= 310
  }
  return width >= 200
})

onMounted(() => {
  const actionCell = document.querySelector('.table-actions')?.parentElement
  if (actionCell) {
    resizeObserver.value = new ResizeObserver((entries) => {
      for (const entry of entries) {
        containerWidth.value = entry.contentRect.width
      }
    })
    resizeObserver.value.observe(actionCell)
  }
})

onUnmounted(() => {
  resizeObserver.value?.disconnect()
})
</script>
