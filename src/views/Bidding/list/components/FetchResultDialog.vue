<template>
  <el-dialog :model-value="modelValue" title="标讯同步结果" width="520px" @update:model-value="$emit('update:modelValue', $event)">
    <div class="fetch-result-grid">
      <el-statistic title="新增入库" :value="result.saved || 0">
        <template #suffix>条</template>
      </el-statistic>
      <el-statistic title="跳过重复" :value="result.skipped || 0">
        <template #suffix>条</template>
      </el-statistic>
    </div>
    <el-alert :title="result.msg || '标讯同步完成'" type="success" :closable="false" show-icon />
    <template #footer>
      <el-button type="primary" @click="$emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  modelValue: { type: Boolean, default: false },
  result: { type: Object, default: () => ({ saved: 0, skipped: 0, message: '' }) },
})

defineEmits(['update:modelValue'])
</script>
