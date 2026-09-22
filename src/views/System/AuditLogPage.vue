<template>
  <main class="log-page">
    <AuditLogPanel mode="audit" />
  </main>
</template>

<script setup>
import AuditLogPanel from './settings/AuditLogPanel.vue'
</script>

<style scoped>
.log-page {
  min-height: 100%;
  padding: 24px;
  background: linear-gradient(135deg, #f8fafc 0%, #eef3f6 48%, #f7fbf8 100%);
}

@media (max-width: 768px) {
  .log-page {
    padding: 14px;
  }
}
</style>
