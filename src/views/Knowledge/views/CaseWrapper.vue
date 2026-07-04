<template>
  <div class="case-wrapper-container">
    <keep-alive include="CaseGrid"><CaseGrid /></keep-alive>
  </div>
</template>

<script setup>
import CaseGrid from './CaseGrid.vue'
</script>

<style scoped>
/* Padding is provided by KbLayout .kb-content */
</style>
