<template>
  <div class="dynamic-layout-renderer">
    <div v-for="col in layout.columns" :key="col.id || Math.random()" class="dynamic-column" :style="{ width: (col.width || 12) / 24 * 100 + '%' }">
      <template v-for="widget in col.widgets" :key="widget.id || Math.random()">
        <component
          v-if="hasPermission(widget.component)"
          :is="resolveComponent(widget.component)"
          v-bind="getProps(widget.component)"
          v-on="getListeners(widget.component)"
        />
      </template>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  layout: { type: Object, default: () => ({ columns: [] }) },
  registry: { type: Object, required: true },
  widgetProps: { type: Object, default: () => ({}) },
  widgetListeners: { type: Object, default: () => ({}) },
  permissions: { type: Object, default: () => ({}) }
})

const resolveComponent = (name) => props.registry[name]

const getProps = (name) => {
  return props.widgetProps[name] || {}
}

const getListeners = (name) => {
  return props.widgetListeners[name] || {}
}

const hasPermission = (name) => {
  if (!props.permissions) return true
  return props.permissions[name] !== false
}
</script>

<style scoped>
.dynamic-layout-renderer {
  display: flex;
  gap: 20px;
  width: 100%;
}
.dynamic-column {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
</style>
