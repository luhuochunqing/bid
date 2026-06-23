<template>
  <el-card shadow="never" class="permission-panel">
    <template #header>
      <div class="panel-header">
        <div>
          <h3>接口权限矩阵</h3>
          <p>只读展示真实后端接口权限来源，帮助核对菜单权限和接口权限是否一致。</p>
        </div>
        <el-button type="primary" :loading="loading" @click="load">刷新矩阵</el-button>
      </div>
    </template>

    <div class="toolbar">
      <el-input v-model="filters.keyword" placeholder="搜索路径 / Controller / 表达式" clearable />
      <el-select v-model="filters.method" clearable placeholder="方法">
        <el-option v-for="method in methodOptions" :key="method" :label="method" :value="method" />
      </el-select>
      <el-select v-model="filters.role" clearable placeholder="角色">
        <el-option label="管理员" value="admin" />
        <el-option label="经理" value="manager" />
        <el-option label="投标专员" value="bid-Team" />
      </el-select>
      <el-select v-model="filters.riskLevel" clearable placeholder="风险">
        <el-option label="高" value="HIGH" />
        <el-option label="中" value="MEDIUM" />
        <el-option label="低" value="LOW" />
      </el-select>
    </div>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="panel-alert"
    />

    <div class="matrix-summary">
      <span>共 {{ rows.length }} 个接口，当前筛选 {{ filteredRows.length }} 个</span>
      <span>展示入口层权限；Service 内部二次授权需结合专项审查。</span>
    </div>

    <el-table v-loading="loading" :data="filteredRows" stripe border class="matrix-table">
      <el-table-column prop="method" label="方法" width="88" />
      <el-table-column prop="path" label="接口路径" min-width="260" show-overflow-tooltip />
      <el-table-column prop="module" label="模块" width="110" />
      <el-table-column label="允许角色" min-width="160">
        <template #default="{ row }">
          <el-tag v-for="role in permissionRoleTags(row)" :key="role" class="role-tag" effect="plain">
            {{ role }}
          </el-tag>
          <span v-if="permissionRoleTags(row).length === 0">无角色声明</span>
        </template>
      </el-table-column>
      <el-table-column label="风险" width="90">
        <template #default="{ row }">
          <el-tag :type="riskTagType(row.riskLevel)" effect="dark">{{ row.riskLevel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="来源" width="130">
        <template #default="{ row }">{{ sourceLabel(row.source) }}</template>
      </el-table-column>
      <el-table-column prop="expression" label="后端表达式" min-width="230" show-overflow-tooltip />
      <el-table-column label="处理器" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ row.controller }}.{{ row.handler }}</template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { permissionMatrixApi } from '@/api'
import {
  filterEndpointPermissions,
  permissionRoleTags,
  riskTagType,
  sourceLabel,
} from './interface-permission-matrix-core.js'

const loading = ref(false)
const errorMessage = ref('')
const rows = ref([])
const filters = reactive({
  keyword: '',
  method: '',
  module: '',
  role: '',
  riskLevel: '',
  source: '',
})

const methodOptions = computed(() => [...new Set(rows.value.map((row) => row.method))].sort())
const filteredRows = computed(() => filterEndpointPermissions(rows.value, filters))

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await permissionMatrixApi.getEndpointPermissions()
    if (!result?.success) throw new Error(result?.msg || '接口权限矩阵加载失败')
    rows.value = result.data
  } catch (error) {
    rows.value = []
    errorMessage.value = error?.message || '接口权限矩阵加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.permission-panel {
  border-radius: 20px;
}

.panel-header,
.toolbar,
.matrix-summary {
  display: flex;
  gap: 12px;
}

.panel-header {
  align-items: flex-start;
  justify-content: space-between;
}

.panel-header h3 {
  margin: 0 0 6px;
  color: #1f2937;
}

.panel-header p,
.matrix-summary {
  color: #52627a;
}

.toolbar {
  align-items: center;
  margin-bottom: 16px;
}

.toolbar .el-input {
  max-width: 340px;
}

.panel-alert,
.matrix-summary {
  margin-bottom: 14px;
}

.matrix-summary {
  justify-content: space-between;
  font-size: 13px;
}

.role-tag {
  margin-right: 6px;
}

.matrix-table {
  width: 100%;
}

@media (max-width: 768px) {
  .panel-header,
  .toolbar,
  .matrix-summary {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar .el-input {
    max-width: none;
  }
}
</style>
