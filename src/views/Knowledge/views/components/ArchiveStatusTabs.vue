<template>
  <div class="status-tabs">
    <el-radio-group :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)">
      <el-radio-button value="ALL">全部</el-radio-button>
      <el-radio-button value="PENDING_INITIATION">待立项</el-radio-button>
      <el-radio-button value="INITIATED">已立项</el-radio-button>
      <el-radio-button value="BIDDING">投标中</el-radio-button>
      <el-radio-button value="EVALUATING">评标中</el-radio-button>
      <el-radio-button value="WON">已中标</el-radio-button>
      <el-radio-button value="LOST">未中标</el-radio-button>
      <el-radio-button value="FAILED">已流标</el-radio-button>
      <el-radio-button value="ABANDONED">已放弃</el-radio-button>
    </el-radio-group>
  </div>
</template>

<script setup>
defineProps({
  modelValue: { type: String, default: 'ALL' }
})
defineEmits(['update:modelValue'])
</script>

<style scoped lang="scss">
.status-tabs {
  display: flex;
  gap: 8px;
  padding: 0 4px;
}
</style>
