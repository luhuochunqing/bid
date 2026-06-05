<template>
  <main class="log-page">
    <AuditLogPanel mode="operation" />
  </main>
</template>

<script setup>
import AuditLogPanel from './settings/AuditLogPanel.vue'
</script>

<style scoped>
.log-page {
  min-height: 100%;
  padding: 24px;
  background: linear-gradient(135deg, #f7fbf8 0%, #eef6f2 48%, #f8fafc 100%);
}

@media (max-width: 768px) {
  .log-page {
    padding: 14px;
  }
}
</style>
