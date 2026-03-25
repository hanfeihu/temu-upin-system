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

        <a-menu
          class="menu"
          theme="dark"
          mode="inline"
          :selectedKeys="selectedKeys"
          @click="onMenuClick"
        >
          <a-menu-item key="/platform/product-collections">
            <template #icon>
              <appstore-outlined />
            </template>
            采集商品库
          </a-menu-item>

          <a-menu-item key="/platform/config">
            <template #icon>
              <setting-outlined />
            </template>
            平台配置
          </a-menu-item>

          <a-menu-item key="/platform/publish-logs">
            <template #icon>
              <file-text-outlined />
            </template>
            发布日志
          </a-menu-item>

          <a-menu-item key="/platform/publish-success-cases">
            <template #icon>
              <file-text-outlined />
            </template>
            发布成功案例
          </a-menu-item>

          <a-menu-item key="/platform/temu-auto-publish-logs">
            <template #icon>
              <file-text-outlined />
            </template>
            自动发布日志
          </a-menu-item>

           <a-menu-item key="/platform/post-import-logs">
             <template #icon>
               <file-text-outlined />
             </template>
             自动化日志
           </a-menu-item>

           <a-menu-item key="/platform/biz-logs">
             <template #icon>
               <file-text-outlined />
             </template>
             业务日志
           </a-menu-item>

           <a-menu-item key="/platform/temu-attr-rules">
              <template #icon>
                <setting-outlined />
              </template>
              货品属性规则
            </a-menu-item>

           <a-menu-item key="/platform/temu-main-sale-spec-inference">
              <template #icon>
                <file-text-outlined />
              </template>
              主销售属性推理
            </a-menu-item>

            <a-menu-item key="/platform/temu-attr-ai-fill">
              <template #icon>
                <file-text-outlined />
              </template>
              类目属性AI填写
            </a-menu-item>

            <a-menu-item key="/platform/temu-apps">
              <template #icon>
                <setting-outlined />
              </template>
              TEMU 应用管理
            </a-menu-item>

            <a-menu-item key="/platform/temu-shops">
              <template #icon>
                <setting-outlined />
              </template>
              TEMU 店铺管理
            </a-menu-item>

           <a-menu-item key="/platform/ocr-tasks">
             <template #icon>
               <file-text-outlined />
             </template>
             OCR任务
           </a-menu-item>

           <a-menu-item key="/platform/ocr-filter-words">
             <template #icon>
               <setting-outlined />
             </template>
             图片过滤词
           </a-menu-item>

           <a-menu-item key="/platform/title-filter-words">
             <template #icon>
               <setting-outlined />
             </template>
             标题过滤词
           </a-menu-item>

           <a-menu-item key="/platform/ai-channels">
             <template #icon>
               <setting-outlined />
             </template>
             AI 渠道管理
           </a-menu-item>
        </a-menu>

        <div class="sider-footer">
          <a-button type="text" class="collapse-btn" @click="collapsed = !collapsed">
            <template #icon>
              <menu-unfold-outlined v-if="collapsed" />
              <menu-fold-outlined v-else />
            </template>
            <span v-if="!collapsed">收起</span>
          </a-button>
        </div>
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
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { AppstoreOutlined, MenuFoldOutlined, MenuUnfoldOutlined, SettingOutlined, FileTextOutlined } from '@ant-design/icons-vue'

defineProps({
  title: { type: String, default: '平台端' }
})

const route = useRoute()
const router = useRouter()

const collapsed = ref(false)

const selectedKeys = computed(() => {
  const p = route.path || ''
  if (p.startsWith('/platform/product-collections')) return ['/platform/product-collections']
  if (p.startsWith('/platform/config')) return ['/platform/config']
  if (p.startsWith('/platform/publish-logs')) return ['/platform/publish-logs']
  if (p.startsWith('/platform/publish-success-cases')) return ['/platform/publish-success-cases']
  if (p.startsWith('/platform/temu-auto-publish-logs')) return ['/platform/temu-auto-publish-logs']
  if (p.startsWith('/platform/post-import-logs')) return ['/platform/post-import-logs']
  if (p.startsWith('/platform/biz-logs')) return ['/platform/biz-logs']
  if (p.startsWith('/platform/temu-attr-rules')) return ['/platform/temu-attr-rules']
  if (p.startsWith('/platform/temu-main-sale-spec-inference')) return ['/platform/temu-main-sale-spec-inference']
  if (p.startsWith('/platform/temu-attr-ai-fill')) return ['/platform/temu-attr-ai-fill']
  if (p.startsWith('/platform/temu-apps')) return ['/platform/temu-apps']
  if (p.startsWith('/platform/temu-shops')) return ['/platform/temu-shops']
  if (p.startsWith('/platform/ocr-tasks')) return ['/platform/ocr-tasks']
  if (p.startsWith('/platform/ocr-filter-words')) return ['/platform/ocr-filter-words']
  if (p.startsWith('/platform/title-filter-words')) return ['/platform/title-filter-words']
  if (p.startsWith('/platform/ai-channels')) return ['/platform/ai-channels']
  return []
})

const go = (path) => router.push(path)
const onMenuClick = ({ key }) => {
  if (key) router.push(String(key))
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
  padding: 8px;
  background: transparent;
  border-right: 0;
}

.sider-footer {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 10px 8px;
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
