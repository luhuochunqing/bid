<template>
  <div class="qualification-page-header">
    <h2 class="page-title">资质库</h2>
    <div class="header-actions">
      <el-button v-if="isAdmin" :icon="Setting" @click="$emit('alert-config')">
        告警配置
      </el-button>
      <el-button :icon="Download" @click="$emit('export')">
        导出列表
      </el-button>
      <el-button type="primary" :icon="Upload" @click="$emit('upload')">
        上传资质
      </el-button>
    </div>
  </div>

  <el-card class="search-card">
    <el-form :inline="true" :model="searchForm">
      <el-form-item label="资质名称">
        <el-input
          v-model="searchForm.name"
          placeholder="请输入资质名称"
          clearable
          :prefix-icon="Search"
        />
      </el-form-item>
      <el-form-item label="资质类型">
        <el-select v-model="searchForm.type" placeholder="全部类型" clearable>
          <el-option label="企业资质" value="enterprise" />
          <el-option label="人员资质" value="personnel" />
          <el-option label="产品资质" value="product" />
          <el-option label="行业认证" value="industry" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="searchForm.status" placeholder="全部状态" clearable>
          <el-option label="有效" value="valid" />
          <el-option label="即将到期" value="expiring" />
          <el-option label="已过期" value="expired" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="$emit('search')">
          搜索
        </el-button>
        <el-button @click="$emit('reset')">重置</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { Download, Search, Setting, Upload } from '@element-plus/icons-vue'

defineModel('searchForm', { type: Object, required: true })

defineProps({
  isAdmin: {
    type: Boolean,
    default: false
  }
})

defineEmits(['alert-config', 'export', 'reset', 'search', 'upload'])
</script>

<style scoped lang="scss">
.qualification-page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  margin: 0;
  color: var(--gray-750);
  font-size: 20px;
  font-weight: 600;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.search-card {
  margin-bottom: 20px;
}
</style>
