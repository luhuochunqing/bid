<template>
  <span class="animated-number">{{ displayValue }}</span>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'

const props = defineProps({
  value: { type: Number, default: 0 },
  duration: { type: Number, default: 1500 },
  prefix: { type: String, default: '' },
  suffix: { type: String, default: '' },
  decimals: { type: Number, default: 0 },
  format: { type: Boolean, default: true }
})

const emit = defineEmits(['finished'])

const displayValue = ref('0')
const currentRef = ref(0)

const formatNumber = (num) => {
  if (props.format) {
    return Math.round(num).toLocaleString()
  }
  return Math.round(num).toString()
}

const animate = (start, end, duration) => {
  const startTime = performance.now()

  const step = (currentTime) => {
    const elapsed = currentTime - startTime
    const progress = Math.min(elapsed / duration, 1)

    // Easing function (easeOutExpo)
    const easeOut = progress === 1 ? 1 : 1 - Math.pow(2, -10 * progress)

    currentRef.value = start + (end - start) * easeOut

    // Format display value
    let formattedValue
    if (props.decimals > 0) {
      formattedValue = currentRef.value.toFixed(props.decimals)
    } else {
      formattedValue = formatNumber(currentRef.value)
    }

    displayValue.value = `${props.prefix}${formattedValue}${props.suffix}`

    if (progress < 1) {
      requestAnimationFrame(step)
    } else {
      emit('finished')
    }
  }

  requestAnimationFrame(step)
}

watch(() => props.value, (newVal, oldVal) => {
  animate(oldVal || 0, newVal, props.duration)
}, { immediate: true })

onMounted(() => {
  animate(0, props.value, props.duration)
})
</script>

<style scoped>
.animated-number {
  font-variant-numeric: tabular-nums;
}
</style>
