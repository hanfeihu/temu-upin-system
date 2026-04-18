<template>
  <div class="pro-layout">
    <a-layout class="layout">
      <a-layout-sider
        class="sider"
        :collapsed="collapsed"
        :collapsible="false"
        width="200"
        :collapsedWidth="80"
      >
        <div class="brand" :class="{ 'brand--collapsed': collapsed }" @click="go('/')">
          <img src="/system-logo.png" alt="TMINOS" class="brand-logo" />
          <span v-if="!collapsed" class="brand-title">TMINOS</span>
        </div>

        <a-menu
          class="menu"
          theme="dark"
          mode="inline"
          :selectedKeys="selectedKeys"
          :openKeys="openKeys"
          @click="onMenuClick"
          @openChange="onOpenChange"
        >
          <a-menu-item key="/platform/product-collections">
            <template #icon>
              <appstore-outlined />
            </template>
            采集商品库
          </a-menu-item>

          <a-sub-menu key="group-product">
            <template #icon>
              <appstore-outlined />
            </template>
            <template #title>商品中心</template>
            <a-menu-item key="/platform/product-drafts">商品草稿库</a-menu-item>
            <a-menu-item key="/platform/publish-logs">发布日志</a-menu-item>
            <a-menu-item key="/platform/publish-success-cases">发布成功案例</a-menu-item>
            <a-menu-item key="/platform/temu-auto-publish-logs">自动发布日志</a-menu-item>
          </a-sub-menu>

          <a-sub-menu key="group-sync">
            <template #icon>
              <file-text-outlined />
            </template>
            <template #title>数据同步</template>
            <a-menu-item key="/platform/sync-config">同步配置</a-menu-item>
            <a-menu-item key="/platform/sync-tasks">同步任务</a-menu-item>
            <a-menu-item key="/platform/sync-goods">TEMU 商品数据</a-menu-item>
            <a-menu-item key="/platform/sync-activity">活动报名</a-menu-item>
          </a-sub-menu>

          <a-menu-item key="/platform/sync-price-review">
            <template #icon>
              <file-text-outlined />
            </template>
            核价单管理
          </a-menu-item>

          <a-menu-item key="/platform/sync-price-adjust">
            <template #icon>
              <file-text-outlined />
            </template>
            调价单管理
          </a-menu-item>

          <a-sub-menu key="group-temu">
            <template #icon>
              <setting-outlined />
            </template>
            <template #title>TEMU 配置</template>
            <a-menu-item key="/platform/temu-apps">TEMU 应用管理</a-menu-item>
            <a-menu-item key="/platform/temu-shops">TEMU 店铺管理</a-menu-item>
            <a-menu-item key="/platform/temu-attr-rules">货品属性规则</a-menu-item>
            <a-menu-item key="/platform/spec-mappings/parent-spec-mappings">父规格映射表</a-menu-item>
          </a-sub-menu>

          <a-sub-menu key="group-ai-tools">
            <template #icon>
              <file-text-outlined />
            </template>
            <template #title>AI 与内容</template>
            <a-menu-item key="/platform/temu-attr-ai-fill">类目属性AI填写</a-menu-item>
            <a-menu-item key="/platform/temu-title-optimizer">TEMU 标题优化</a-menu-item>
            <a-menu-item key="/platform/image-translate-records">翻译记录</a-menu-item>
            <a-menu-item key="/platform/ocr-tasks">OCR任务</a-menu-item>
            <a-menu-item key="/platform/ocr-filter-words">图片过滤词</a-menu-item>
            <a-menu-item key="/platform/title-filter-words">标题过滤词</a-menu-item>
            <a-menu-item key="/platform/parser-test">解析器测试</a-menu-item>
          </a-sub-menu>

          <a-sub-menu key="group-system">
            <template #icon>
              <setting-outlined />
            </template>
            <template #title>系统与日志</template>
            <a-menu-item key="/platform/ai-channels">AI 渠道管理</a-menu-item>
            <a-menu-item key="/platform/post-import-logs">自动化日志</a-menu-item>
            <a-menu-item key="/platform/biz-logs">业务日志</a-menu-item>
          </a-sub-menu>
        </a-menu>

      </a-layout-sider>

      <a-layout>
        <a-layout-header class="header">
          <div class="header-left">
            <a-button type="text" class="header-collapse-btn" @click="collapsed = !collapsed">
              <template #icon>
                <menu-unfold-outlined v-if="collapsed" />
                <menu-fold-outlined v-else />
              </template>
            </a-button>
            <div class="page-title">{{ title }}</div>
          </div>
          <div class="header-right">
            <a-space>
              <span class="header-user">{{ authUser?.displayName || authUser?.username || '未登录' }}</span>
              <a-button @click="go('/ai')">进入 AI 做图</a-button>
              <a-button @click="logout">退出登录</a-button>
            </a-space>
          </div>
        </a-layout-header>

        <a-layout-content class="content">
          <slot />
        </a-layout-content>
      </a-layout>
    </a-layout>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { AppstoreOutlined, MenuFoldOutlined, MenuUnfoldOutlined, SettingOutlined, FileTextOutlined } from '@ant-design/icons-vue'
import { clearPlatformAuth, getPlatformAuthUser } from '@/utils/platformAuth'

defineProps({
  title: { type: String, default: '平台端' }
})

const route = useRoute()
const router = useRouter()

const collapsed = ref(false)
const openKeys = ref([])
const authUser = computed(() => getPlatformAuthUser())

const activeGroupKey = computed(() => {
  const p = route.path || ''
  if (p.startsWith('/platform/product-drafts') || p.startsWith('/platform/publish-logs') || p.startsWith('/platform/publish-success-cases') || p.startsWith('/platform/temu-auto-publish-logs')) {
    return 'group-product'
  }
  if (p.startsWith('/platform/sync-config') || p.startsWith('/platform/sync-tasks') || p.startsWith('/platform/sync-goods') || p.startsWith('/platform/sync-activity')) {
    return 'group-sync'
  }
  if (p.startsWith('/platform/temu-apps') || p.startsWith('/platform/temu-shops') || p.startsWith('/platform/temu-attr-rules') || p.startsWith('/platform/spec-mappings/parent-spec-mappings')) {
    return 'group-temu'
  }
  if (p.startsWith('/platform/temu-attr-ai-fill') || p.startsWith('/platform/temu-title-optimizer') || p.startsWith('/platform/image-translate-records') || p.startsWith('/platform/ocr-tasks') || p.startsWith('/platform/ocr-filter-words') || p.startsWith('/platform/title-filter-words') || p.startsWith('/platform/parser-test')) {
    return 'group-ai-tools'
  }
  if (p.startsWith('/platform/ai-channels') || p.startsWith('/platform/post-import-logs') || p.startsWith('/platform/biz-logs')) {
    return 'group-system'
  }
  return null
})

const selectedKeys = computed(() => {
  const p = route.path || ''
  if (p.startsWith('/platform/product-collections')) return ['/platform/product-collections']
  if (p.startsWith('/platform/product-drafts')) return ['/platform/product-drafts']
  if (p.startsWith('/platform/publish-logs')) return ['/platform/publish-logs']
  if (p.startsWith('/platform/publish-success-cases')) return ['/platform/publish-success-cases']
  if (p.startsWith('/platform/temu-auto-publish-logs')) return ['/platform/temu-auto-publish-logs']
  if (p.startsWith('/platform/post-import-logs')) return ['/platform/post-import-logs']
  if (p.startsWith('/platform/biz-logs')) return ['/platform/biz-logs']
  if (p.startsWith('/platform/image-translate-records')) return ['/platform/image-translate-records']
  if (p.startsWith('/platform/temu-attr-rules')) return ['/platform/temu-attr-rules']
  if (p.startsWith('/platform/temu-attr-ai-fill')) return ['/platform/temu-attr-ai-fill']
  if (p.startsWith('/platform/temu-title-optimizer')) return ['/platform/temu-title-optimizer']
  if (p.startsWith('/platform/temu-apps')) return ['/platform/temu-apps']
  if (p.startsWith('/platform/temu-shops')) return ['/platform/temu-shops']
  if (p.startsWith('/platform/spec-mappings/parent-spec-mappings')) return ['/platform/spec-mappings/parent-spec-mappings']
  if (p.startsWith('/platform/ocr-tasks')) return ['/platform/ocr-tasks']
  if (p.startsWith('/platform/ocr-filter-words')) return ['/platform/ocr-filter-words']
  if (p.startsWith('/platform/title-filter-words')) return ['/platform/title-filter-words']
  if (p.startsWith('/platform/parser-test')) return ['/platform/parser-test']
  if (p.startsWith('/platform/ai-channels')) return ['/platform/ai-channels']
  if (p.startsWith('/platform/sync-config')) return ['/platform/sync-config']
  if (p.startsWith('/platform/sync-tasks')) return ['/platform/sync-tasks']
  if (p.startsWith('/platform/sync-goods')) return ['/platform/sync-goods']
  if (p.startsWith('/platform/sync-price-review')) return ['/platform/sync-price-review']
  if (p.startsWith('/platform/sync-price-adjust')) return ['/platform/sync-price-adjust']
  if (p.startsWith('/platform/sync-activity')) return ['/platform/sync-activity']
  return []
})

watch(
  () => [collapsed.value, activeGroupKey.value],
  ([isCollapsed, groupKey]) => {
    if (isCollapsed) {
      openKeys.value = []
      return
    }
    if (groupKey && !openKeys.value.includes(groupKey)) {
      openKeys.value = [groupKey]
    }
  },
  { immediate: true }
)

const go = (path) => router.push(path)
const logout = () => {
  clearPlatformAuth()
  router.replace('/login')
}
const onMenuClick = ({ key }) => {
  if (key) router.push(String(key))
}
const onOpenChange = (keys) => {
  openKeys.value = collapsed.value ? [] : keys
}
</script>

<style scoped>
.pro-layout {
  min-height: 100vh;
  background: #f5f7fa;
}

.layout {
  min-height: 100vh;
}

.sider {
  background: #001529;
}

.brand {
  height: 64px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}

.brand--collapsed {
  justify-content: center;
  padding: 0 8px;
}

.brand-logo {
  display: block;
  height: 24px;
  width: auto;
  max-width: 56px;
  object-fit: contain;
  border-radius: 4px;
}

.brand--collapsed .brand-logo {
  height: 24px;
  max-width: 52px;
}

.brand-title {
  color: rgba(255, 255, 255, 0.96);
  font-size: 16px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: 0;
  white-space: nowrap;
}

.menu {
  height: calc(100vh - 64px);
  overflow-y: auto;
  padding: 8px 4px;
  background: transparent;
  border-right: 0;
}

:deep(.menu.ant-menu-dark) {
  background: #001529;
  font-size: 15px;
}

:deep(.menu.ant-menu-root .ant-menu-item),
:deep(.menu.ant-menu-root .ant-menu-submenu-title) {
  width: calc(100% - 8px);
  height: 40px;
  line-height: 40px;
  margin: 4px;
  border-radius: 6px;
  color: rgba(255, 255, 255, 0.65);
  font-size: 15px;
  font-weight: 500;
}

:deep(.menu .ant-menu-title-content) {
  font-size: 15px;
  font-weight: 500;
}

:deep(.menu.ant-menu-dark .ant-menu-item:hover),
:deep(.menu.ant-menu-dark .ant-menu-submenu-title:hover) {
  color: #ffffff;
  background: transparent;
}

:deep(.menu.ant-menu-dark .ant-menu-item-selected) {
  color: #ffffff;
  background: #1677ff;
}

:deep(.menu.ant-menu-dark .ant-menu-item-selected:hover) {
  background: #1677ff;
}

:deep(.menu.ant-menu-dark .ant-menu-sub.ant-menu-inline) {
  background: #000c17;
}

:deep(.menu .ant-menu-item .ant-menu-item-icon),
:deep(.menu .ant-menu-submenu-title .ant-menu-item-icon),
:deep(.menu .ant-menu-submenu-title .ant-menu-submenu-arrow) {
  color: inherit;
}

:deep(.menu .ant-menu-item-icon) {
  font-size: 16px;
}

.header {
  height: 64px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  border-bottom: 1px solid #f0f0f0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-collapse-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  color: rgba(0, 0, 0, 0.88);
  font-size: 18px;
}

.header-collapse-btn:hover {
  background: #f5f5f5;
}

.page-title {
  font-size: 16px;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.88);
}

.header-user {
  color: rgba(0, 0, 0, 0.65);
  font-size: 13px;
  font-weight: 600;
}

.content {
  padding: 24px 16px;
  min-height: calc(100vh - 64px);
  min-width: 0;
  overflow-x: hidden;
}

@media (max-width: 900px) {
  .content {
    padding: 12px;
  }
}
</style>
