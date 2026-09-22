<template>
  <el-dialog
    :model-value="visible"
    title="版本历史"
    width="700px"
    @update:model-value="$emit('update:visible', $event)"
  >
    <FeaturePlaceholder
      v-if="placeholder"
      compact
      :title="placeholder.title"
      :message="placeholder.message"
      :hint="placeholder.hint"
    />
    <el-timeline v-else-if="versions.length > 0">
      <el-timeline-item
        v-for="version in versions"
        :key="version.id"
        :timestamp="version.date"
        :type="version.isCurrent ? 'primary' : 'info'"
      >
        <div class="version-item">
          <div class="version-header">
            <span class="version-number">v{{ version.version }}</span>
            <el-tag v-if="version.isCurrent" type="success" size="small">当前版本</el-tag>
            <el-tag v-else type="info" size="small">历史版本</el-tag>
          </div>
          <div class="version-description">{{ version.description }}</div>
        </div>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-else description="暂无版本历史" />
  </el-dialog>
</template>

<script setup>
import FeaturePlaceholder from '@/components/common/FeaturePlaceholder.vue'

defineProps({
  visible: { type: Boolean, default: false },
  versions: { type: Array, default: () => [] },
  placeholder: { type: Object, default: null }
})

defineEmits(['update:visible'])
</script>

<style scoped>
.version-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.version-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.version-number {
  font-weight: 600;
}

.version-description {
  color: var(--text-secondary-ui);
}
</style>
