<template>
  <el-dialog
    :model-value="modelValue"
    title="AI分析中"
    width="480px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
  >
    <div class="parsing-content">
      <div class="parsing-spinner"></div>
      <p>正在解析招标文件...</p>
      <el-progress :percentage="progress" :stroke-width="12">
        <template #default="{ percentage = progress } = {}">{{ Math.round(percentage) }}%</template>
      </el-progress>
    </div>
  </el-dialog>
</template>

<script setup>
defineProps({
  modelValue: { type: Boolean, default: false },
  progress: { type: Number, default: 0 },
})
</script>
