<template>
  <div>
    <div v-if="results.length === 0" class="empty-result">
      <el-empty description="未找到匹配的CRM商机" />
    </div>

    <el-table
      v-else
      :data="results"
      highlight-current-row
      @current-change="(row) => $emit('select', row)"
      max-height="280"
      stripe
      style="width: 100%"
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="expanded-detail">
            <el-descriptions :column="3" size="small" border>
              <el-descriptions-item label="商机编号">{{ row.code || '-' }}</el-descriptions-item>
              <el-descriptions-item label="集团名称">{{ row.groupName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="招标主体">{{ row.tenderSubject || '-' }}</el-descriptions-item>
              <el-descriptions-item label="项目负责人">{{ row.projectLeaderName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="项目状态">{{ row.projectStatusText || '-' }}</el-descriptions-item>
              <el-descriptions-item label="项目风险">{{ row.projectRiskText || '-' }}</el-descriptions-item>
              <el-descriptions-item label="评标时间">{{ row.evaluationTime || '-' }}</el-descriptions-item>
              <el-descriptions-item label="电商流水(万)">{{ row.ecommerceMroAmount ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="客户营收(亿)">{{ row.customerRevenue ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="计划入围">{{ row.planSupplierCount ?? '-' }}家</el-descriptions-item>
              <el-descriptions-item label="兜底方案">{{ row.backupPlanText || '-' }}</el-descriptions-item>
              <el-descriptions-item label="剩余时间">{{ row.bidRemainTime || '-' }}</el-descriptions-item>
            </el-descriptions>
            <div v-if="row.remark" class="remark-block">
              <strong>备注/支持需求：</strong>{{ row.remark }}
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="商机名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="code" label="商机编号" width="120" show-overflow-tooltip />
      <el-table-column prop="groupName" label="集团" width="100" show-overflow-tooltip />
      <el-table-column prop="projectLeaderName" label="项目负责人" width="100" show-overflow-tooltip />
      <el-table-column prop="projectStatusText" label="项目状态" width="80" />
      <el-table-column label="操作" width="80" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            size="small"
            type="primary"
            :disabled="selectedId === row.id"
            @click.stop="$emit('select', row)"
          >
            {{ selectedId === row.id ? '已选' : '选择' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="totalCount > 0" class="pagination-row">
      <el-pagination
        :current-page="currentPage" @update:current-page="(page) => $emit('page-change', page)"
        :page-size="pageSize"
        :total="totalCount"
        layout="prev, pager, next, total"
        background
        small
        @current-change="(page) => $emit('page-change', page)"
      />
    </div>
  </div>
</template>

<script setup>
defineProps({
  results: { type: Array, default: () => [] },
  totalCount: { type: Number, default: 0 },
  currentPage: { type: Number, default: 1 },
  pageSize: { type: Number, default: 10 },
  selectedId: { type: [Number, String], default: null },
})
defineEmits(['select', 'page-change'])
</script>

<style scoped>
.empty-result { padding: 30px 0; }
.expanded-detail { padding: 12px 16px; }
.remark-block { margin-top: 8px; padding: 8px 12px; background: var(--bg-muted-2); border-radius: 4px; font-size: 13px; }
.pagination-row { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
