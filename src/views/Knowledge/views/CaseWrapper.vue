<template>
  <div class="case-wrapper-container">
    <keep-alive include="CaseGrid"><CaseGrid /></keep-alive>
  </div>
</template>

<script setup>
import CaseGrid from './CaseGrid.vue'
</script>

<style scoped>
/* Remove duplicate wrapper padding to keep layouts unified */
:deep(.case-grid-container) {
  padding: 24px 0 0 0 !important;
}
</style>
