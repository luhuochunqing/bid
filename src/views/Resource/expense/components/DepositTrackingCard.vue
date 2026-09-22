<template>
  <el-card class="deposit-tracking-card">
    <template #header>
      <div class="card-header">
        <span>保证金归还跟踪</span>
        <el-tag v-if="overdueCount > 0" type="danger">{{ overdueCount }}笔超期未退</el-tag>
        <el-tag v-else type="success">无超期</el-tag>
      </div>
    </template>

    <el-table :data="rows" stripe>
      <el-table-column prop="project" label="项目名称" min-width="150" />
      <el-table-column prop="amount" label="金额(万元)" width="130" align="right">
        <template #default="{ row }"><span class="amount">¥{{ row.amount.toFixed(2) }}</span></template>
      </el-table-column>
      <el-table-column prop="date" label="缴纳日期" width="160" />
      <el-table-column prop="expectedReturnDate" label="应退日期" width="160">
        <template #default="{ row }"><span :class="{ 'overdue-text': row.overdue }">{{ row.expectedReturnDate || '-' }}</span></template>
      </el-table-column>
      <el-table-column prop="trackingStatus" label="状态" width="140">
        <template #default="{ row }"><el-tag :type="getTrackingStatusType(row)">{{ getTrackingStatusLabel(row) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="lastRemindedAt" label="最近提醒" width="180">
        <template #default="{ row }">{{ row.lastRemindedAt || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.trackingStatus !== 'returned'" link type="primary" size="small" @click="$emit('remind', row)">提醒</el-button>
          <el-button v-if="row.trackingStatus !== 'returned'" link type="success" size="small" @click="$emit('confirm-return', row)">确认退还</el-button>
          <span v-else class="text-muted">已完成</span>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { getTrackingStatusLabel, getTrackingStatusType } from '../depositTracking.js'

defineProps({
  rows: { type: Array, required: true },
  overdueCount: { type: Number, required: true }
})

defineEmits(['remind', 'confirm-return'])
</script>
