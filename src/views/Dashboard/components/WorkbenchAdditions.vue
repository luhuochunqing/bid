<template>
  <QuickEntrySection
    :can-create-project="canCreateProject"
    :can-view-tenders="canViewTenders"
    @handle-todos="$emit('handle-todos')"
  />
  <DeadlineMetricCards
    :metrics="deadlineMetrics"
    :loading="deadlineMetricsLoading"
    :error="deadlineMetricsError"
    @retry="$emit('retry-deadline')"
  />
  <AiPredictionPlaceholder />
</template>

<script setup>
import QuickEntrySection from './QuickEntrySection.vue'
import DeadlineMetricCards from './DeadlineMetricCards.vue'
import AiPredictionPlaceholder from './AiPredictionPlaceholder.vue'

defineProps({
  canCreateProject: Boolean,
  canViewTenders: Boolean,
  deadlineMetrics: { type: Array, default: () => [] },
  deadlineMetricsLoading: Boolean,
  deadlineMetricsError: { type: String, default: '' },
})

defineEmits(['handle-todos', 'retry-deadline'])
</script>
