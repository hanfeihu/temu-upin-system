<template>
  <div class="pro-layout">
    <a-layout class="layout">
      <a-layout-sider
        class="sider"
        :collapsed="collapsed"
        :collapsible="false"
        width="240"
      >
        <div class="brand" @click="go('/')">
          <div class="brand-mark">PS</div>
          <div v-if="!collapsed" class="brand-text">
            <div class="brand-title">Product Scene</div>
            <div class="brand-sub">Platform Console</div>
          </div>
        </div>

        <div class="sider-toolbar">
          <a-button type="text" class="collapse-btn" @click="collapsed = !collapsed">
            <template #icon>
              <menu-unfold-outlined v-if="collapsed" />
              <menu-fold-outlined v-else />
            </template>
            <span v-if="!collapsed">收起侧栏</span>
          </a-button>
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
            <a-menu-item key="/platform/sync-price-review">核价单管理</a-menu-item>
            <a-menu-item key="/platform/sync-price-adjust">调价单管理</a-menu-item>
            <a-menu-item key="/platform/sync-activity">活动报名</a-menu-item>
          </a-sub-menu>

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
            <a-menu-item key="/platform/config">平台配置</a-menu-item>
            <a-menu-item key="/platform/ai-channels">AI 渠道管理</a-menu-item>
            <a-menu-item key="/platform/post-import-logs">自动化日志</a-menu-item>
            <a-menu-item key="/platform/biz-logs">业务日志</a-menu-item>
          </a-sub-menu>
        </a-menu>

      </a-layout-sider>

      <a-layout>
        <a-layout-header class="header">
          <div class="header-left">
            <div class="page-title">{{ title }}</div>
          </div>
          <div class="header-right">
            <a-space>
              <a-button @click="go('/ai')">进入 AI 做图</a-button>
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

defineProps({
  title: { type: String, default: '平台端' }
})

const route = useRoute()
const router = useRouter()

const collapsed = ref(false)
const openKeys = ref([])

const activeGroupKey = computed(() => {
  const p = route.path || ''
  if (p.startsWith('/platform/product-drafts') || p.startsWith('/platform/publish-logs') || p.startsWith('/platform/publish-success-cases') || p.startsWith('/platform/temu-auto-publish-logs')) {
    return 'group-product'
  }
  if (p.startsWith('/platform/sync-')) {
    return 'group-sync'
  }
  if (p.startsWith('/platform/temu-apps') || p.startsWith('/platform/temu-shops') || p.startsWith('/platform/temu-attr-rules') || p.startsWith('/platform/spec-mappings/parent-spec-mappings')) {
    return 'group-temu'
  }
  if (p.startsWith('/platform/temu-attr-ai-fill') || p.startsWith('/platform/temu-title-optimizer') || p.startsWith('/platform/image-translate-records') || p.startsWith('/platform/ocr-tasks') || p.startsWith('/platform/ocr-filter-words') || p.startsWith('/platform/title-filter-words') || p.startsWith('/platform/parser-test')) {
    return 'group-ai-tools'
  }
  if (p.startsWith('/platform/config') || p.startsWith('/platform/ai-channels') || p.startsWith('/platform/post-import-logs') || p.startsWith('/platform/biz-logs')) {
    return 'group-system'
  }
  return null
})

const selectedKeys = computed(() => {
  const p = route.path || ''
  if (p.startsWith('/platform/product-collections')) return ['/platform/product-collections']
  if (p.startsWith('/platform/product-drafts')) return ['/platform/product-drafts']
  if (p.startsWith('/platform/config')) return ['/platform/config']
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
  background: radial-gradient(1200px 400px at 20% 0%, rgba(14, 165, 233, 0.14), transparent 60%),
    radial-gradient(900px 380px at 80% 15%, rgba(34, 197, 94, 0.12), transparent 55%),
    #f3f5f8;
}

.layout {
  min-height: 100vh;
}

.sider {
  background: linear-gradient(180deg, #0b1220 0%, #0f172a 55%, #0b1220 100%);
}

.brand {
  height: 64px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  user-select: none;
}

.brand-mark {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  font-weight: 800;
  letter-spacing: 0.4px;
  color: #0b1220;
  background: linear-gradient(135deg, #0ea5e9 0%, #22c55e 100%);
}

.brand-title {
  color: rgba(255, 255, 255, 0.92);
  font-weight: 700;
  line-height: 1.1;
}

.brand-sub {
  margin-top: 2px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
}

.menu {
  height: calc(100vh - 126px);
  overflow-y: auto;
  padding: 8px;
  background: transparent;
  border-right: 0;
}

.sider-toolbar {
  padding: 0 8px 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.collapse-btn {
  width: 100%;
  color: rgba(255, 255, 255, 0.78);
  text-align: left;
}

.header {
  height: 64px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.page-title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.content {
  padding: 18px;
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
