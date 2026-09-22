<template>
  <el-dialog
    v-model="visible"
    title="投标资产检查"
    width="500px"
    :close-on-click-modal="false"
  >
    <div v-if="assetCheckResult" class="asset-check-result">
      <div class="check-header">
        <span class="site-name">{{ assetCheckResult.site?.name }}</span>
        <el-tag
          v-if="assetCheckResult.capability?.status === 'available'"
          type="success"
          size="large"
        >
          可投标
        </el-tag>
        <el-tag
          v-else-if="assetCheckResult.capability?.status === 'risk'"
          type="warning"
          size="large"
        >
          有风险
        </el-tag>
        <el-tag v-else type="danger" size="large">不可投标</el-tag>
      </div>

      <div class="check-items">
        <div class="check-item">
          <el-icon :class="assetCheckResult.capability?.hasAccount ? 'icon-success' : 'icon-error'">
            <component :is="assetCheckResult.capability?.hasAccount ? 'CircleCheck' : 'CircleClose'" />
          </el-icon>
          <span>账号：{{ assetCheckResult.capability?.hasAccount ? '已注册' : '未注册' }}</span>
        </div>
        <div class="check-item">
          <el-icon :class="assetCheckResult.capability?.hasAvailableUK ? 'icon-success' : 'icon-error'">
            <component :is="assetCheckResult.capability?.hasAvailableUK ? 'CircleCheck' : 'CircleClose'" />
          </el-icon>
          <span>UK：{{ ukLabel }}</span>
        </div>
        <div v-if="assetCheckResult.capability?.primaryOwner" class="check-item">
          <el-icon class="icon-user"><User /></el-icon>
          <span>责任人：{{ assetCheckResult.capability?.primaryOwner }} ({{ assetCheckResult.capability?.primaryPhone }})</span>
        </div>
      </div>

      <el-alert
        v-if="assetCheckResult.capability?.hasRisk"
        type="warning"
        :closable="false"
        show-icon
      >
        存在风险项，请确认后继续
      </el-alert>
    </div>

    <el-empty v-else description="未找到该站点的资产信息" :image-size="80" />

    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button
        v-if="assetCheckResult?.capability?.status !== 'unavailable'"
        type="primary"
        @click="$emit('confirm')"
      >
        继续创建
      </el-button>
      <el-button v-else type="primary" @click="$emit('go-to-management')">
        前去管理资产
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { User } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  assetCheckResult: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'confirm', 'go-to-management'])

const visible = ref(props.modelValue)

watch(() => props.modelValue, (val) => {
  visible.value = val
})

watch(visible, (val) => {
  if (val !== props.modelValue) emit('update:modelValue', val)
})

const ukLabel = computed(() => {
  const cap = props.assetCheckResult?.capability
  if (!cap) return ''
  if (cap.ukCount > 0) return cap.hasAvailableUK ? '在库' : '已借出'
  return '不需要'
})

function handleCancel() {
  visible.value = false
}
</script>

<style scoped>
.asset-check-result { padding: 16px 0; }

.check-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #eee;
}

.site-name { font-size: 18px; font-weight: 600; color: var(--gray-750); }

.check-items { margin-bottom: 20px; }

.check-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  font-size: 15px;
}

.check-item .icon-success { color: #67c23a; font-size: 22px; }
.check-item .icon-error { color: #f56c6c; font-size: 22px; }
.check-item .icon-user { color: var(--text-muted); font-size: 20px; }
</style>
