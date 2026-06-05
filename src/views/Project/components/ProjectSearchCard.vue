<template>
  <el-card class="search-card" shadow="never">
    <el-form :inline="true" :model="searchForm" class="search-form">
      <el-form-item label="项目名称">
        <el-input v-model="searchForm.name" placeholder="请输入" clearable style="width:160px" />
      </el-form-item>
      <el-form-item label="来源平台">
        <el-select v-model="searchForm.sourceModule" placeholder="请选择" clearable style="width:150px">
          <el-option v-for="opt in sourceOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="招标主体">
        <el-input v-model="searchForm.ownerUnit" placeholder="请输入" clearable style="width:160px" />
      </el-form-item>
      <el-form-item label="计划入围供应商数量">
        <el-input-number v-model="searchForm.shortlistedCountMin" :min="0" controls-position="right" style="width:90px" /> ~
        <el-input-number v-model="searchForm.shortlistedCountMax" :min="0" controls-position="right" style="width:90px" />
      </el-form-item>
      <el-form-item label="创建时间" class="search-field--datetime">
        <el-date-picker v-model="searchForm.createTimeRange" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" />
      </el-form-item>
      <el-form-item label="开标时间" class="search-field--datetime">
        <el-date-picker v-model="searchForm.bidOpenTimeRange" type="daterange" range-separator="至" start-placeholder="起始" end-placeholder="截止" value-format="YYYY-MM-DD" />
      </el-form-item>
      <el-form-item label="投标月份">
        <el-select v-model="searchForm.bidMonth" placeholder="请选择" clearable style="width:160px">
          <el-option v-for="opt in bidMonthOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目类型">
        <el-select v-model="searchForm.projectType" placeholder="请选择" clearable style="width:130px">
          <el-option v-for="opt in projectTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="营业收入(万)">
        <el-input-number v-model="searchForm.revenueMin" :min="0" :precision="2" controls-position="right" style="width:100px" /> ~
        <el-input-number v-model="searchForm.revenueMax" :min="0" :precision="2" controls-position="right" style="width:100px" />
      </el-form-item>
      <el-form-item label="客户类型">
        <el-select v-model="searchForm.customerType" placeholder="请选择" clearable style="width:180px">
          <el-option v-for="opt in customerTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="优先级">
        <el-select v-model="searchForm.priority" placeholder="请选择" clearable style="width:120px">
          <el-option v-for="opt in priorityOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="总部所在地">
        <el-cascader
          v-model="regionValue"
          :options="chinaRegionOptions"
          :props="{ expandTrigger: 'hover', label: 'name', value: 'name', checkStrictly: false, emitPath: true }"
          placeholder="请选择省市"
          clearable
          filterable
          style="width:180px"
        />
      </el-form-item>
      <el-form-item label="项目负责人">
        <el-select v-model="searchForm.projectLeaderName" placeholder="请选择" clearable filterable remote :remote-method="(q) => searchUsers(q, 'pm')" :loading="userLoading.pm" style="width:170px">
          <el-option v-for="u in userOptions.pm" :key="u.id" :label="u.name" :value="u.name" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目负责人部门">
        <el-select v-model="searchForm.leaderDepartment" placeholder="请选择部门" clearable style="width:150px" />
      </el-form-item>
      <el-form-item label="投标负责人">
        <el-select v-model="searchForm.biddingLeaderName" placeholder="请选择" clearable filterable remote :remote-method="(q) => searchUsers(q, 'bp')" :loading="userLoading.bp" style="width:170px">
          <el-option v-for="u in userOptions.bp" :key="u.id" :label="u.name" :value="u.name" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目状态">
        <el-select v-model="searchForm.bidStatus" placeholder="请选择" clearable style="width:150px">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目阶段">
        <el-select v-model="searchForm.stage" placeholder="请选择" clearable style="width:130px">
          <el-option v-for="opt in stageOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="投标平台">
        <el-input v-model="searchForm.biddingPlatform" placeholder="请输入" clearable style="width:160px" />
      </el-form-item>
      <el-form-item>
        <el-button size="small" type="primary" :icon="Search" @click="$emit('search')">搜索</el-button>
        <el-button size="small" :icon="Refresh" @click="$emit('reset')">重置</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>
<script setup>
import { computed } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'

const props = defineProps({
  searchForm: { type: Object, required: true },
  sourceOptions: { type: Array, default: () => [] },
  statusOptions: { type: Array, default: () => [] },
  stageOptions: { type: Array, default: () => [] },
  priorityOptions: { type: Array, default: () => [] },
  projectTypeOptions: { type: Array, default: () => [] },
  customerTypeOptions: { type: Array, default: () => [] },
  bidMonthOptions: { type: Array, default: () => [] },
  chinaRegionOptions: { type: Array, default: () => [] },
  userOptions: { type: Object, default: () => ({ pm: [], bp: [] }) },
  userLoading: { type: Object, default: () => ({ pm: false, bp: false }) },
  searchUsers: { type: Function, default: () => {} },
})

defineEmits(['search', 'reset'])

const regionValue = computed({
  get: () => {
    const v = props.searchForm.region
    if (!v) return null
    for (const province of props.chinaRegionOptions) {
      if (province.name === v) return [v]
      if (province.children) {
        for (const city of province.children) {
          if (city.name === v) return [province.name, city.name]
          if (v.startsWith(province.name) && v.endsWith(city.name)) return [province.name, city.name]
        }
      }
    }
    return v
  },
  set: (val) => {
    if (!val) { props.searchForm.region = ''; return }
    if (Array.isArray(val)) { props.searchForm.region = val.join('') }
    else { props.searchForm.region = val }
  }
})
</script>
<style scoped>
.search-card { margin-bottom: 16px; }
.search-card :deep(.el-card__body) { padding: 16px 20px; }
.search-card :deep(.el-button) { border-radius: 6px; font-size: 13px; }
.search-card :deep(.el-button--small) { padding: 6px 14px; }
.search-card :deep(.el-input__wrapper),
.search-card :deep(.el-select .el-input__wrapper) { border-radius: 6px; }
.search-field--datetime { flex: 0 0 420px; width: 420px; min-width: 420px; max-width: 420px; }
</style>
