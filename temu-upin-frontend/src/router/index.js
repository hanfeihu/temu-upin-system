import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'PlatformHome',
    component: () => import('@/views/PlatformHome.vue')
  },
  {
    path: '/platform/product-collections',
    name: 'PlatformCollections',
    component: () => import('@/views/PlatformCollections.vue')
  },
  {
    path: '/platform/config',
    name: 'PlatformConfig',
    component: () => import('@/views/PlatformConfig.vue')
  },
  {
    path: '/platform/publish-logs',
    name: 'PlatformPublishLogs',
    component: () => import('@/views/PlatformPublishLogs.vue')
  },
  {
    path: '/platform/temu-auto-publish-logs',
    name: 'PlatformTemuAutoPublishLogs',
    component: () => import('@/views/PlatformTemuAutoPublishLogs.vue')
  },
  {
    path: '/platform/post-import-logs',
    name: 'PlatformPostImportLogs',
    component: () => import('@/views/PlatformPostImportLogs.vue')
  },
  {
    path: '/platform/biz-logs',
    name: 'PlatformBizLogs',
    component: () => import('@/views/PlatformBizLogs.vue')
  },
  {
    path: '/platform/biz-logs',
    name: 'PlatformBizLogs',
    component: () => import('@/views/PlatformBizLogs.vue')
  },
  {
    path: '/platform/temu-attr-rules',
    name: 'PlatformTemuAttrRules',
    component: () => import('@/views/PlatformTemuAttrRules.vue')
  },
  {
    path: '/platform/ocr-tasks',
    name: 'PlatformOcrTasks',
    component: () => import('@/views/PlatformOcrTasks.vue')
  },
  {
    path: '/platform/ocr-filter-words',
    name: 'PlatformOcrFilterWords',
    component: () => import('@/views/PlatformOcrFilterWords.vue')
  },
  {
    path: '/platform/title-filter-words',
    name: 'PlatformTitleFilterWords',
    component: () => import('@/views/PlatformTitleFilterWords.vue')
  },
  {
    path: '/platform/product-collections/:id',
    name: 'PlatformCollectionDetail',
    component: () => import('@/views/PlatformCollectionDetail.vue')
  },
  {
    path: '/goods/:id',
    name: 'ProductCollectionPublicDetail',
    component: () => import('@/views/PlatformCollectionDetail.vue')
  },
  {
    path: '/ai',
    name: 'AIHome',
    component: () => import('@/views/HomePage.vue')
  },
  {
    path: '/ai/product/create',
    name: 'CreateProduct',
    component: () => import('@/views/CreateProduct.vue')
  },
  {
    path: '/ai/product/:id',
    name: 'ProductDetail',
    component: () => import('@/views/ProductDetail.vue')
  },
  {
    path: '/ai/channels',
    name: 'ChannelManagement',
    component: () => import('@/views/ChannelManagement.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
