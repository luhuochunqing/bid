<template>
  <div class="competitor-editor">
    <div class="toolbar">
      <span class="title">竞争对手记录</span>
      <el-button size="small" type="primary" plain @click="handleAdd">添加</el-button>
    </div>

    <el-table :data="modelValue" size="small" border empty-text="暂无竞争对手记录">
      <el-table-column label="公司名称" min-width="140">
        <template #default="{ row }">
          <el-input v-model="row.company" placeholder="竞争对手公司" />
        </template>
      </el-table-column>
      <el-table-column label="SKU数量" width="110">
        <template #default="{ row }">
          <el-input-number v-model="row.skuCount" :min="0" :controls="false" style="width: 100%" />
        </template>
      </el-table-column>
      <el-table-column label="品类" min-width="120">
        <template #default="{ row }">
          <el-input v-model="row.category" placeholder="品类" />
        </template>
      </el-table-column>
      <el-table-column label="折扣" width="110">
        <template #default="{ row }">
          <el-input v-model="row.discount" placeholder="如 85折" />
        </template>
      </el-table-column>
      <el-table-column label="账期" width="130">
        <template #default="{ row }">
          <el-input v-model="row.paymentTerms" placeholder="如 月结30天" />
        </template>
      </el-table-column>
      <el-table-column label="备注" min-width="150">
        <template #default="{ row }">
          <el-input v-model="row.notes" placeholder="补充说明" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="72" fixed="right">
        <template #default="{ $index }">
          <el-button link type="danger" @click="handleRemove($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
defineProps({
  modelValue: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['add', 'remove'])

const handleAdd = () => emit('add')
const handleRemove = (index) => emit('remove', index)
</script>

<style scoped>
.competitor-editor {
  width: 100%;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.title {
  font-size: 13px;
  font-weight: 600;
  color: var(--gray-750);
}
</style>
