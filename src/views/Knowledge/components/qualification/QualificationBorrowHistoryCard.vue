<template>
  <el-card class="borrow-history-card">
    <template #header>
      <div class="card-header">
        <span>借阅记录</span>
        <el-button type="primary" size="small" @click="$emit('create')">
          新增借阅
        </el-button>
      </div>
    </template>

    <FeaturePlaceholder
      v-if="featurePlaceholder"
      :title="featurePlaceholder.title"
      :message="featurePlaceholder.message"
      :hint="featurePlaceholder.hint"
    />

    <el-table v-else v-loading="loading" :data="records" stripe>
      <el-table-column prop="qualificationName" label="资质名称" min-width="180" />
      <el-table-column prop="borrower" label="借用人" width="100" />
      <el-table-column prop="department" label="部门" width="120" />
      <el-table-column prop="purpose" label="用途" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ getPurposeLabel(row.purpose) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="borrowDate" label="借阅日期" width="120" />
      <el-table-column prop="returnDate" label="应归还日期" width="120" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="borrowStatusTagTypes[row.status] || ''" size="small">
            {{ getBorrowStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'borrowed'"
            size="small"
            type="primary"
            link
            @click="$emit('return', row)"
          >
            归还
          </el-button>
          <el-button v-else size="small" link disabled>
            已归还
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import FeaturePlaceholder from '@/components/common/FeaturePlaceholder.vue'
import {
  borrowStatusTagTypes,
  getBorrowStatusLabel,
  getPurposeLabel
} from './qualificationMeta.js'

defineProps({
  featurePlaceholder: {
    type: Object,
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  },
  records: {
    type: Array,
    default: () => []
  }
})

defineEmits(['create', 'return'])
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
