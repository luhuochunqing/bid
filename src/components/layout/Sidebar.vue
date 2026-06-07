<template>
  <!-- 移动端抽屉菜单 -->
  <el-drawer
    v-model="drawerVisible"
    direction="ltr"
    :size="260"
    :with-header="false"
    class="mobile-drawer"
    v-if="isMobile"
  >
    <div class="drawer-sidebar">
      <div class="sidebar-logo">
        <span class="logo-icon"><img src="/favicon.ico" alt="" aria-hidden="true">西域MRO</span>
        <CommonIcon name="close" class="close-icon" @click="drawerVisible = false" />
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="false"
        class="sidebar-menu"
        background-color="var(--sidebar-bg)"
        text-color="var(--sidebar-text)"
        active-text-color="var(--brand-xiyu-logo)"
        router
        @select="handleMenuSelect"
      >
        <!-- 直接按配置顺序渲染菜单项 -->
        <template v-for="item in filteredMenus" :key="item.path">
          <!-- 单级菜单 -->
          <el-menu-item
            v-if="!item.children || item.children.length === 0"
            :index="item.path"
          >
            <CommonIcon :name="item.meta?.icon" size="md" />
            <template #title>{{ item.meta?.title }}</template>
          </el-menu-item>

          <!-- 多级菜单 -->
          <el-sub-menu v-else :index="item.path">
            <template #title>
              <CommonIcon :name="item.meta?.icon" size="md" />
              <span>{{ item.meta?.title }}</span>
            </template>
            <el-menu-item
              v-for="child in item.children"
              :key="child.path"
              :index="child.path"
              class="sub-menu-item"
            >
              <template #title>{{ child.meta?.title }}</template>
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </div>
  </el-drawer>

  <!-- PC端侧边栏 -->
  <div class="sidebar-container" v-else>
    <div class="sidebar-logo">
      <span class="logo-icon" v-if="!collapse"><img src="/favicon.ico" alt="" aria-hidden="true">西域MRO</span>
      <span class="logo-icon-small" v-else><img src="/favicon.ico" alt="西域"></span>
    </div>

    <el-menu
      :default-active="activeMenu"
      :collapse="collapse"
      :collapse-transition="false"
      class="sidebar-menu"
      background-color="var(--sidebar-bg)"
      text-color="var(--sidebar-text)"
      active-text-color="var(--brand-xiyu-logo)"
      router
      @select="handleMenuSelect"
    >
      <!-- 直接按配置顺序渲染菜单项 -->
      <template v-for="item in filteredMenus" :key="item.path">
        <!-- 单级菜单 -->
        <el-menu-item
          v-if="!item.children || item.children.length === 0"
          :index="item.path"
        >
          <CommonIcon :name="item.meta?.icon" size="md" />
          <template #title>{{ item.meta?.title }}</template>
        </el-menu-item>

        <!-- 多级菜单 -->
        <el-sub-menu v-else :index="item.path">
          <template #title>
            <CommonIcon :name="item.meta?.icon" size="md" />
            <span>{{ item.meta?.title }}</span>
          </template>
          <el-menu-item
            v-for="child in item.children"
            :key="child.path"
            :index="child.path"
            class="sub-menu-item"
          >
            <template #title>{{ child.meta?.title }}</template>
          </el-menu-item>
        </el-sub-menu>
      </template>
    </el-menu>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonIcon from '@/components/common/CommonIcon.vue'
import { useUserStore } from '@/stores/user'
import { hasMenuAccessForRole } from '@/api/modules/settings'
import { hiddenApiMenuNames, sidebarMenuConfig } from '@/config/sidebar-menu'
import { hasAnyPermission } from '@/utils/permission'

const props = defineProps({
  collapse: {
    type: Boolean,
    default: false
  },
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

// 移动端检测
const isMobile = ref(false)
const drawerVisible = ref(false)

// 监听父组件传入的 modelValue
watch(() => props.modelValue, (val) => {
  drawerVisible.value = val
})

// 监听抽屉状态变化
watch(drawerVisible, (val) => {
  emit('update:modelValue', val)
})

// 检测是否为移动端
const checkMobile = () => {
  isMobile.value = window.innerWidth < 768
}

const route = useRoute()
const router = useRouter()

// 菜单选择后关闭抽屉
const handleMenuSelect = (index) => {
  // 手动导航到 /ai-center
  if (index === '/ai-center' || index.includes('ai-center')) {
    router.push('/ai-center')
  }

  if (isMobile.value) {
    drawerVisible.value = false
  }
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})
const userStore = useUserStore()
const isApiDeliveryMode = computed(() => true)

const hasPermissionAccess = (permissionKeys) => {
  const decision = hasMenuAccessForRole(userStore.userRole, permissionKeys)
  if (decision !== null) {
    return decision
  }
  return hasAnyPermission(userStore.menuPermissions, permissionKeys)
}

const activeMenu = computed(() => {
  const { meta, path } = route
  if (meta?.activeMenu) {
    return meta.activeMenu
  }
  return path
})

// 根据角色过滤菜单
const filteredMenus = computed(() => {
  return sidebarMenuConfig
    .map(menu => {
      if (isApiDeliveryMode.value && hiddenApiMenuNames.has(menu.name)) {
        return null
      }
      if (!hasPermissionAccess(menu.meta?.permissionKeys)) {
        return null
      }

      if (menu.children) {
        const visibleChildren = menu.children.filter(
          child => (
            (!isApiDeliveryMode.value || !hiddenApiMenuNames.has(child.name)) &&
            hasPermissionAccess(child.meta?.permissionKeys)
          )
        )

        if (visibleChildren.length === 0) {
          return null
        }

        return {
          ...menu,
          children: visibleChildren
        }
      }

      return menu
    })
    .filter(Boolean)
})
</script>

<style scoped>
.sidebar-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--sidebar-bg, #FFFFFF);
}

/* ========== Logo 区域 ========== */
.sidebar-logo {
  height: var(--header-height);
  min-height: var(--header-height);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-card); border-bottom: 1px solid rgba(46, 118, 89, 0.12);
}

.logo-icon { font-size: 20px; font-weight: 700; color: var(--brand-xiyu-logo); letter-spacing: 2px; white-space: nowrap; display: inline-flex; align-items: center; gap: 8px; }

.logo-icon-small { display: inline-flex; align-items: center; justify-content: center; }

.logo-icon img,
.logo-icon-small img { width: 24px; height: 24px; object-fit: contain; flex-shrink: 0; }

/* ========== 菜单区域 ========== */
.sidebar-menu {
  flex: 1;
  border-right: none;
  overflow-y: auto;
  overflow-x: hidden;
}

.sidebar-menu:not(.el-menu--collapse) {
  width: var(--sidebar-width);
}

/* 滚动条样式 */
.sidebar-menu::-webkit-scrollbar {
  width: 6px;
}

.sidebar-menu::-webkit-scrollbar-thumb {
  background: rgba(46, 118, 89, 0.18);
  border-radius: 3px;
}

.sidebar-menu::-webkit-scrollbar-thumb:hover {
  background: rgba(46, 118, 89, 0.3);
}

/* ========== 菜单项对齐优化 ========== */
/* 统一菜单项高度 - 使用 flexbox 确保内容居中 */
:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  height: var(--menu-item-height);
  display: flex;
  align-items: center;
  padding: 7px 6px;
}

/* 图标和文字完美对齐 - 使用 inline-flex */
:deep(.el-menu-item .el-menu-tooltip__trigger),
:deep(.el-sub-menu__title .el-sub-menu__title-arrow),
:deep(.el-menu-item span),
:deep(.el-sub-menu__title span) {
  display: inline-flex;
  align-items: center;
}

/* 图标尺寸和对齐 */
:deep(.el-menu-item .el-icon),
:deep(.el-sub-menu__title .el-icon) {
  font-size: 16px;
  width: 16px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

/* ========== 子菜单缩进统一 ========== */
:deep(.el-sub-menu .el-menu-item) {
  padding-left: var(--space-lg) !important; /* 24px */
  min-height: var(--menu-item-height);
  display: flex;
  align-items: center;
}

/* ========== 菜单交互状态 ========== */
:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
}

:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  background: rgba(46, 118, 89, 0.08) !important;
}

:deep(.el-menu-item:hover)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 2px;
  height: 20px;
  background: var(--brand-xiyu-logo);
  border-radius: 0 2px 2px 0;
}

:deep(.el-menu-item:focus-visible) {
  outline: 2px solid var(--brand-xiyu-logo-focus, rgba(46, 118, 89, 0.32));
  outline-offset: -2px;
}

:deep(.el-menu-item.is-active) {
  background: rgba(46, 118, 89, 0.12) !important;
  border-right: 2px solid var(--brand-xiyu-logo);
  color: var(--brand-xiyu-logo) !important;
  box-shadow: none;
}

:deep(.el-menu-item.is-active)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 2px;
  height: 24px;
  background: var(--brand-xiyu-logo);
  border-radius: 0 2px 2px 0;
  box-shadow: 0 0 8px rgba(46, 118, 89, 0.2);
}

:deep(.el-sub-menu .el-menu-item.is-active) {
  background: rgba(46, 118, 89, 0.1) !important;
  color: var(--brand-xiyu-logo) !important;
}

:deep(.el-sub-menu .el-menu-item.is-active)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 2px;
  height: 16px;
  background: var(--brand-xiyu-logo);
  border-radius: 0 2px 2px 0;
}

/* Icon animation on hover */
:deep(.el-menu-item:hover .el-icon),
:deep(.el-sub-menu__title:hover .el-icon) {
  transform: scale(1.08);
}

:deep(.el-menu-item .el-icon),
:deep(.el-sub-menu__title .el-icon) {
  transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

/* ========== 子菜单箭头动画 ========== */
:deep(.el-sub-menu__title-arrow) {
  transition: transform 300ms cubic-bezier(0.4, 0, 0.2, 1);
}

:deep(.el-sub-menu.is-opened > .el-sub-menu__title .el-sub-menu__title-arrow) {
  transform: rotate(180deg);
}

/* 子菜单箭头靠文字 — 改为静态流式布局，箭头紧跟文字 */
:deep(.el-sub-menu__title .el-sub-menu__icon-arrow) {
  position: static;
  margin-top: 0;
  margin-left: 2px;
}

/* ========== 子菜单展开动画 ========== */
:deep(.el-menu--inline) {
  animation: slideDown 200ms cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ========== 折叠状态 ========== */
:deep(.el-menu--collapse .el-menu-item) {
  padding: 0;
  justify-content: center;
}

:deep(.el-menu--collapse .el-sub-menu__title) {
  padding: 0;
  justify-content: center;
}

:deep(.el-menu--collapse) .el-menu-item span,
:deep(.el-menu--collapse) .el-sub-menu__title span {
  display: none;
}

:deep(.el-menu--collapse) .el-menu-item .el-icon,
:deep(.el-menu--collapse) .el-sub-menu__title .el-icon {
  margin-right: 0;
}

/* ========== 移动端抽屉 ========== */
.mobile-drawer :deep(.el-drawer__body) {
  padding: 0;
  background: var(--sidebar-bg, #FFFFFF);
}

.drawer-sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--sidebar-bg, #FFFFFF);
}

.drawer-sidebar .sidebar-logo {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 var(--space-md);
}

.close-icon {
  font-size: 20px;
  color: var(--brand-xiyu-logo);
  cursor: pointer;
  transition: all 200ms cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md, 8px);
}

.close-icon:hover {
  color: var(--brand-xiyu-logo-active);
  background: rgba(46, 118, 89, 0.08);
}

.close-icon:active {
  transform: scale(0.95);
}

.close-icon :deep(.el-icon) {
  font-size: 20px;
}

/* ========== 移动端抽屉菜单触摸目标优化 ========== */
.mobile-drawer :deep(.el-menu-item),
.mobile-drawer :deep(.el-sub-menu__title) {
  min-height: 48px;
  padding: 12px 20px;
  display: flex;
  align-items: center;
}

.mobile-drawer :deep(.el-sub-menu .el-menu-item) {
  min-height: 44px;
  padding: 10px 20px 10px 32px;
}

/* 移动端菜单项触摸反馈 */
.mobile-drawer :deep(.el-menu-item):active,
.mobile-drawer :deep(.el-sub-menu__title):active {
  background: rgba(46, 118, 89, 0.28) !important;
  transition: background-color 0.1s ease;
}

/* ========== 移动端响应式 ========== */
@media (max-width: 768px) {
  .sidebar-container {
    display: none;
  }
}
</style>
