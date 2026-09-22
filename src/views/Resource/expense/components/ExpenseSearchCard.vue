<template>
  <el-card class="search-card">
    <el-form :inline="true" :model="searchForm">
      <el-form-item label="项目名称">
        <el-input v-model="searchForm.project" placeholder="请输入" clearable />
      </el-form-item>
      <el-form-item label="费用类型">
        <el-select v-model="searchForm.type" placeholder="全部" clearable>
          <el-option label="保证金" value="保证金" />
          <el-option label="标书费" value="标书费" />
          <el-option label="差旅费" value="差旅费" />
          <el-option label="其他" value="其他" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="searchForm.status" placeholder="全部" clearable>
          <el-option label="已支付" value="paid" />
          <el-option label="待支付" value="pending" />
          <el-option label="已退还" value="returned" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="$emit('search')">
          <el-icon><Search /></el-icon> 搜索
        </el-button>
        <el-button @click="$emit('reset')">重置</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { Search } from '@element-plus/icons-vue'

defineModel('searchForm', { type: Object, required: true })

defineEmits(['search', 'reset'])
</script>
