<template>
  <el-dialog :model-value="visible" title="竞争对手分析报表" width="920px" @close="$emit('close')">
    <el-table :data="rows" border>
      <el-table-column prop="company" label="竞争对手" width="180" fixed="left" />
      <el-table-column prop="skuCount" label="SKU数量" width="100" align="center" />
      <el-table-column prop="category" label="品类" width="120" />
      <el-table-column prop="discount" label="折扣" width="100" align="center" />
      <el-table-column prop="payment" label="账期" width="120" />
      <el-table-column prop="winRate" label="赢面均值" width="100" align="center" />
      <el-table-column prop="projectCount" label="项目数" width="100" align="center" />
      <el-table-column prop="trend" label="趋势" width="80" align="center" />
    </el-table>
  </el-dialog>
</template>

<script setup>
defineProps({
  visible: Boolean,
  rows: {
    type: Array,
    default: () => []
  }
})

defineEmits(['close'])
</script>
