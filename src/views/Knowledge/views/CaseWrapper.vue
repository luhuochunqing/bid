<template>
  <div class="case-wrapper-container">
    <el-tabs v-model="activeTab" class="case-tabs">
      <el-tab-pane label="AI 案例网格" name="grid">
        <keep-alive include="CaseGrid"><CaseGrid /></keep-alive>
      </el-tab-pane>
      <el-tab-pane label="传统案例库" name="traditional">
        <TraditionalCase />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import CaseGrid from './CaseGrid.vue'
import TraditionalCase from '../Case.vue'

const activeTab = ref('grid')
</script>

<style scoped>
.case-wrapper-container {
  padding: 24px;
}

:deep(.el-tabs__header) {
  margin-bottom: 0px;
}

/* Remove duplicate wrapper padding to keep layouts unified */
:deep(.case-grid-container),
:deep(.case-container) {
  padding: 24px 0 0 0 !important;
}
</style>
