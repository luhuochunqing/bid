<!-- Input: title prop for the integration name -->
<!-- Output: placeholder card with "即将支持" state -->
<!-- Pos: src/views/System/settings/integration/ -->
<!-- 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->

<template>
  <div class="coming-soon-card">
    <div class="card-header">
      <p class="card-kicker">Integration</p>
      <h3 class="card-title">{{ title }}</h3>
    </div>
    <div class="coming-soon-body">
      <el-tag type="info" effect="plain" size="small" class="soon-tag">即将支持</el-tag>
      <p class="soon-text">该集成功能正在规划中，敬请期待。</p>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
})
</script>

<style scoped>
.coming-soon-card {
  padding: 20px 24px;
  border: 1px solid rgba(67, 89, 55, 0.1);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.6);
}

.card-header {
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(67, 89, 55, 0.08);
}

.card-kicker {
  margin: 0 0 4px;
  color: #6d7d5d;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.card-title {
  margin: 0;
  color: #1f2d1d;
  font-size: 16px;
}

.coming-soon-body {
  display: flex;
  align-items: center;
  gap: 12px;
}

.soon-tag {
  flex-shrink: 0;
}

.soon-text {
  margin: 0;
  color: #66705f;
  font-size: 14px;
}
</style>
