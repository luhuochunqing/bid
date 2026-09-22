<template>
  <el-card class="search-card">
    <el-form :inline="true" :model="model">
      <el-form-item label="项目">
        <el-select v-model="model.projectId" placeholder="全部项目" clearable filterable>
          <el-option
            v-for="project in projectOptions"
            :key="project.id"
            :label="project.name"
            :value="project.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="部门">
        <el-select v-model="model.department" placeholder="全部部门" clearable filterable>
          <el-option
            v-for="department in departmentOptions"
            :key="department.key"
            :label="department.label"
            :value="department.label"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="费用类型">
        <el-select v-model="model.expenseType" placeholder="全部" clearable>
          <el-option label="保证金" value="保证金" />
          <el-option label="标书费" value="标书费" />
          <el-option label="差旅费" value="差旅费" />
          <el-option label="材料费" value="材料费" />
          <el-option label="其他" value="其他" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="model.status" placeholder="全部" clearable>
          <el-option label="待审批" value="PENDING_APPROVAL" />
          <el-option label="待支付" value="APPROVED" />
          <el-option label="已支付" value="PAID" />
          <el-option label="已驳回" value="REJECTED" />
          <el-option label="退还中" value="RETURN_REQUESTED" />
          <el-option label="已退还" value="RETURNED" />
        </el-select>
      </el-form-item>
      <el-form-item label="发生日期">
        <el-date-picker
          v-model="model.dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="$emit('search')">查询</el-button>
        <el-button @click="$emit('reset')">重置</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
// Input: current expense filter model and option sets
// Output: multidimensional expense ledger filters
// Pos: src/views/Resource/expense/ - Expense page filter bar
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

const model = defineModel({ type: Object, required: true })

defineProps({
  projectOptions: { type: Array, default: () => [] },
  departmentOptions: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

defineEmits(['search', 'reset'])
</script>

<style scoped>
.search-card {
  margin-bottom: 20px;
}
</style>
