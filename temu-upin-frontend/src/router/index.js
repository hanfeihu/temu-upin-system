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
    path: '/platform/publish-success-cases',
    name: 'PlatformPublishSuccessCases',
    component: () => import('@/views/PlatformPublishSuccessCases.vue')
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
    path: '/platform/ai-channels',
    name: 'PlatformAiChannels',
    component: () => import('@/views/PlatformAiChannels.vue')
  },
  {
    path: '/platform/temu-attr-ai-fill',
    name: 'PlatformTemuAttrAiFill',
    component: () => import('@/views/PlatformTemuAttrAiFill.vue')
  },
  {
    path: '/platform/temu-title-optimizer',
    name: 'PlatformTemuTitleOptimizer',
    component: () => import('@/views/PlatformTemuTitleOptimizer.vue')
  },
  {
    path: '/platform/temu-apps',
    name: 'PlatformTemuApps',
    component: () => import('@/views/PlatformTemuApps.vue')
  },
  {
    path: '/platform/temu-shops',
    name: 'PlatformTemuShops',
    component: () => import('@/views/PlatformTemuShops.vue')
  },
  {
    path: '/platform/spec-mappings/parent-spec-mappings',
    name: 'PlatformParentSpecMappings',
    component: () => import('@/views/PlatformParentSpecMappings.vue')
  },
  {
    path: '/platform/biz-logs',
    name: 'PlatformBizLogs',
    component: () => import('@/views/PlatformBizLogs.vue')
  },
  {
    path: '/platform/image-translate-records',
    name: 'PlatformImageTranslateRecords',
    component: () => import('@/views/PlatformImageTranslateRecords.vue')
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
    path: '/platform/parser-test',
    name: 'PlatformParserTest',
    component: () => import('@/views/PlatformParserTest.vue')
  },
  {
    path: '/platform/product-drafts',
    name: 'PlatformProductDrafts',
    component: () => import('@/views/PlatformProductDrafts.vue')
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
    path: '/platform/sync-config',
    name: 'PlatformSyncConfig',
    component: () => import('@/views/PlatformSyncConfig.vue')
  },
  {
    path: '/platform/sync-tasks',
    name: 'PlatformSyncTasks',
    component: () => import('@/views/PlatformSyncTasks.vue')
  },
  {
    path: '/platform/sync-goods',
    name: 'PlatformSyncGoods',
    component: () => import('@/views/PlatformSyncGoods.vue')
  },
  {
    path: '/platform/sync-price-review',
    name: 'PlatformSyncPriceReview',
    component: () => import('@/views/PlatformSyncPriceReview.vue')
  },
  {
    path: '/platform/sync-price-adjust',
    name: 'PlatformSyncPriceAdjust',
    component: () => import('@/views/PlatformSyncPriceAdjust.vue')
  },
  {
    path: '/platform/sync-activity',
    name: 'PlatformSyncActivity',
    component: () => import('@/views/PlatformSyncActivity.vue')
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
