<template>
  <el-card class="table-card">
    <template #header>
      <div class="card-header">
        <span>费用台账</span>
        <div>
          <el-button type="primary" @click="$emit('open-apply')">
            <el-icon><Plus /></el-icon> 费用申请
          </el-button>
          <el-button @click="$emit('export')">
            <el-icon><Download /></el-icon> 导出
          </el-button>
        </div>
      </div>
    </template>

    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="6"><div class="stat-item"><div class="stat-value">¥{{ totalPaid }}万</div><div class="stat-label">已支付总额</div></div></el-col>
      <el-col :xs="12" :sm="6"><div class="stat-item"><div class="stat-value">¥{{ totalPending }}万</div><div class="stat-label">待支付总额</div></div></el-col>
      <el-col :xs="12" :sm="6"><div class="stat-item"><div class="stat-value">{{ depositCount }}</div><div class="stat-label">保证金笔数</div></div></el-col>
      <el-col :xs="12" :sm="6"><div class="stat-item warning"><div class="stat-value">{{ warningCount }}</div><div class="stat-label">待退还提醒</div></div></el-col>
    </el-row>

    <el-table :data="rows" stripe>
      <el-table-column type="index" label="序号" width="100" />
      <el-table-column prop="project" label="项目名称" min-width="150" />
      <el-table-column prop="type" label="费用类型" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.type === '保证金'" type="warning">保证金</el-tag>
          <el-tag v-else-if="row.type === '标书费'" type="success">标书费</el-tag>
          <el-tag v-else-if="row.type === '差旅费'" type="info">差旅费</el-tag>
          <el-tag v-else>其他</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="amount" label="金额(万元)" width="130" align="right">
        <template #default="{ row }"><span class="amount">¥{{ row.amount.toFixed(2) }}</span></template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="140">
        <template #default="{ row }">
          <el-tag v-if="row.approvalStatus === 'pending'" type="info">待审批</el-tag>
          <el-tag v-else-if="row.approvalStatus === 'approved' && row.status === 'pending'" type="warning">待支付</el-tag>
          <el-tag v-else-if="row.status === 'paid'" type="success">已支付</el-tag>
          <el-tag v-else-if="row.status === 'returned'" type="info">已退还</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="approvalStatus" label="审批状态" width="140">
        <template #default="{ row }">
          <el-tag v-if="row.approvalStatus === 'pending'" type="warning">待审批</el-tag>
          <el-tag v-else-if="row.approvalStatus === 'approved'" type="success">已通过</el-tag>
          <el-tag v-else-if="row.approvalStatus === 'rejected'" type="danger">已拒绝</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="date" label="发生日期" width="160" />
      <el-table-column prop="expectedReturnDate" label="预计退还日期" width="170">
        <template #default="{ row }"><span>{{ row.expectedReturnDate || '-' }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="$emit('detail', row)">详情</el-button>
          <el-button v-if="row.status === 'paid' && row.type === '保证金'" link type="success" size="small" @click="$emit('return', row)">申请退还</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { Download, Plus } from '@element-plus/icons-vue'

defineProps({
  rows: { type: Array, required: true },
  totalPaid: { type: String, required: true },
  totalPending: { type: String, required: true },
  depositCount: { type: Number, required: true },
  warningCount: { type: Number, required: true }
})

defineEmits(['open-apply', 'export', 'detail', 'return'])
</script>
