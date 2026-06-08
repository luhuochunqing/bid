<template>
  <div class="kb-layout">
    <el-tabs v-model="activeTab" class="kb-tabs" @tab-click="handleTabClick">
      <el-tab-pane label="档案台账" name="archive" />
      <el-tab-pane label="资质库" name="qualification" />
      <el-tab-pane label="人员库" name="personnel" />
      <el-tab-pane label="业绩库" name="performance" />
      <el-tab-pane label="品牌授权" name="brand-auth" />
      <el-tab-pane label="案例库" name="case" />
      <el-tab-pane label="模板库" name="template" />
    </el-tabs>
    <div class="kb-content">
      <router-view />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const TAB_ROUTES = ['archive', 'qualification', 'personnel', 'performance', 'brand-auth', 'case', 'template']
const DETAIL_PATTERN = /^\/knowledge\/case\/detail/

const activeTab = computed(() => {
  const path = route.path
  if (DETAIL_PATTERN.test(path)) return ''
  const segment = path.replace(/^\/knowledge\//, '')
  return TAB_ROUTES.includes(segment) ? segment : 'archive'
})

function handleTabClick(tab) {
  router.push(`/knowledge/${tab.props.name}`)
}
</script>

<style scoped>
.kb-layout {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.kb-tabs {
  padding: 0 24px;
  background: var(--bg-card, #fff);
  border-bottom: 1px solid var(--gray-200, #e5e7eb);
  flex-shrink: 0;
}

:deep(.el-tabs__header) {
  margin-bottom: 0;
}

.kb-content {
  flex: 1;
  overflow: auto;
}
</style>
