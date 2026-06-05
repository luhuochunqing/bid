<template>
  <el-dialog
    v-model="visible"
    title="智能装配中"
    width="500px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
  >
    <div class="assembly-progress">
      <div class="progress-icon">
        <el-icon :size="48" class="rotating"><Loading /></el-icon>
      </div>
      <div class="progress-steps">
        <div
          v-for="(step, index) in steps"
          :key="index"
          class="step-item"
          :class="{
            'step-active': index === currentStepIndex,
            'step-done': index < currentStepIndex,
            'step-pending': index > currentStepIndex
          }"
        >
          <div class="step-icon">
            <el-icon v-if="index < currentStepIndex"><CircleCheckFilled /></el-icon>
            <el-icon v-else-if="index === currentStepIndex"><Loading /></el-icon>
            <span v-else>{{ index + 1 }}</span>
          </div>
          <div class="step-text">{{ step }}</div>
        </div>
      </div>
    </div>
    <template #footer>
      <span></span>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { CircleCheckFilled, Loading } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  steps: { type: Array, default: () => [] },
  currentStepIndex: { type: Number, default: 0 }
})

const emit = defineEmits(['update:modelValue'])

const visible = ref(props.modelValue)

watch(() => props.modelValue, (val) => {
  visible.value = val
})

watch(visible, (val) => {
  if (val !== props.modelValue) {
    emit('update:modelValue', val)
  }
})
</script>

<style scoped>
.assembly-progress {
  padding: 20px 0;
}

.progress-icon {
  text-align: center;
  margin-bottom: 24px;
  color: #409eff;
}

.progress-steps {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-radius: 8px;
  transition: all 0.3s;
}

.step-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: #f0f0f0;
  color: var(--text-muted);
}

.step-text {
  font-size: 14px;
  color: var(--text-muted);
}

.step-active {
  background: #ecf5ff;
}

.step-active .step-icon {
  background: #409eff;
  color: var(--bg-card);
}

.step-active .step-text {
  color: #409eff;
  font-weight: 600;
}

.step-done .step-icon {
  background: #67c23a;
  color: var(--bg-card);
}

.step-done .step-text {
  color: #67c23a;
}

.rotating {
  animation: rotate 1s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
