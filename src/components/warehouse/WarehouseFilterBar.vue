<template>
  <div class="filter-bar">
    <el-form :model="localFilters" inline @submit.prevent="handleSearch">
      <el-form-item label="关键词">
        <el-input v-model="localFilters.keyword" placeholder="仓库名称/地址/联系人" clearable style="width:160px" @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="仓库类型">
        <el-select v-model="localFilters.types" multiple collapse-tags collapse-tags-tooltip placeholder="全部" clearable style="width:160px">
          <el-option label="自营" value="SELF_OPERATED" />
          <el-option label="云仓" value="CLOUD" />
        </el-select>
      </el-form-item>
      <el-form-item label="仓库状态">
        <el-select v-model="localFilters.statuses" multiple collapse-tags collapse-tags-tooltip placeholder="全部" clearable style="width:180px">
          <el-option label="使用中" value="IN_USE" />
          <el-option label="即将到期" value="EXPIRING" />
          <el-option label="已过期" value="EXPIRED" />
          <el-option label="已关仓" value="CLOSED" />
        </el-select>
      </el-form-item>
      <el-form-item label="省份">
        <el-input v-model="localFilters.province" placeholder="所在省份" clearable style="width:120px" />
      </el-form-item>
      <el-form-item label="到期日期">
        <el-date-picker v-model="localFilters.endDateRange" type="daterange" range-separator="至" start-placeholder="开始"
          end-placeholder="结束" value-format="YYYY-MM-DD" style="width:220px" />
      </el-form-item>
      <el-form-item label="附件">
        <el-checkbox v-model="localFilters.hasPropertyCert">有产权证</el-checkbox>
        <el-checkbox v-model="localFilters.hasInvoice">有发票</el-checkbox>
        <el-checkbox v-model="localFilters.hasPhotos">有照片</el-checkbox>
      </el-form-item>
      <el-form-item label="联系人">
        <el-input v-model="localFilters.contactPersonKeyword" placeholder="区域联系人" clearable style="width:120px" @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon> 搜索</el-button>
        <el-button @click="handleReset"><el-icon><RefreshRight /></el-icon> 重置</el-button>
      </el-form-item>
    </el-form>
    <div class="filter-bar-right">
      <span class="total-tip">共 <strong>{{ total }}</strong> 条</span>
      <el-button type="primary" @click="$emit('create')"><el-icon><Plus /></el-icon> 新增仓库</el-button>
    </div>
  </div>
</template>

<script setup>
import { reactive, watch } from 'vue'
import { Search, RefreshRight, Plus } from '@element-plus/icons-vue'

const props = defineProps({
  filters: { type: Object, default: () => ({}) },
  total: { type: Number, default: 0 }
})
const emit = defineEmits(['update:filters', 'search', 'reset', 'create'])

const localFilters = reactive({
  keyword: props.filters.keyword || '',
  types: props.filters.types || [],
  statuses: props.filters.statuses || [],
  province: props.filters.province || '',
  endDateRange: props.filters.endDateRange || null,
  hasPropertyCert: props.filters.hasPropertyCert || false,
  hasInvoice: props.filters.hasInvoice || false,
  hasPhotos: props.filters.hasPhotos || false,
  contactPersonKeyword: props.filters.contactPersonKeyword || ''
})

watch(() => props.filters, (f) => {
  localFilters.keyword = f.keyword || ''
  localFilters.types = f.types || []
  localFilters.statuses = f.statuses || []
  localFilters.province = f.province || ''
  localFilters.endDateRange = f.endDateRange || null
  localFilters.hasPropertyCert = f.hasPropertyCert || false
  localFilters.hasInvoice = f.hasInvoice || false
  localFilters.hasPhotos = f.hasPhotos || false
  localFilters.contactPersonKeyword = f.contactPersonKeyword || ''
}, { deep: true })

const buildFilters = () => ({
  keyword: localFilters.keyword || undefined,
  types: localFilters.types.length ? localFilters.types : undefined,
  statuses: localFilters.statuses.length ? localFilters.statuses : undefined,
  province: localFilters.province || undefined,
  endDateFrom: localFilters.endDateRange?.[0] || undefined,
  endDateTo: localFilters.endDateRange?.[1] || undefined,
  hasPropertyCert: localFilters.hasPropertyCert || undefined,
  hasInvoice: localFilters.hasInvoice || undefined,
  hasPhotos: localFilters.hasPhotos || undefined,
  contactPersonKeyword: localFilters.contactPersonKeyword || undefined
})

const handleSearch = () => { emit('update:filters', buildFilters()); emit('search') }
const handleReset = () => {
  Object.assign(localFilters, { keyword:'', types:[], statuses:[], province:'', endDateRange:null,
    hasPropertyCert:false, hasInvoice:false, hasPhotos:false, contactPersonKeyword:'' })
  emit('update:filters', buildFilters()); emit('reset')
}
</script>

<style scoped lang="scss">
.filter-bar { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:12px 16px;
  background:#fff; border-radius:8px; border:1px solid var(--el-border-color-lighter); margin-bottom:12px;
  .el-form { flex-wrap:wrap; gap:4px 0 }
  .el-form-item { margin-bottom:0; margin-right:8px }
}
.filter-bar-right { display:flex; align-items:center; gap:12px; flex-shrink:0 }
.total-tip { color:var(--el-text-color-secondary); font-size:13px }
</style>
