<template>
  <div class="kb-layout">
    <div class="kb-content">
      <router-view />
    </div>
  </div>
</template>

<script setup>
</script>

<style scoped>
.kb-layout {
  min-height: 100vh;
  background: var(--bg-page);
}

.kb-content {
  padding: 24px;
}
</style>
