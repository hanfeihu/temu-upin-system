<template>
  <ProLayout title="平台首页">
    <div class="home">
      <a-row :gutter="16">
        <a-col :xs="24" :md="14">
          <a-card class="hero" :bordered="false">
            <div class="hero-kicker">Data Ops Console</div>
            <div class="hero-title">数据运营平台</div>
            <div class="hero-sub">管理采集商品库，快速跳转到 AI 做图工作台。</div>
            <div class="hero-actions">
              <a-space>
                <a-button type="primary" size="large" @click="go('/platform/product-collections')">进入采集商品库</a-button>
                <a-button size="large" @click="go('/ai')">进入 AI 做图</a-button>
              </a-space>
            </div>
          </a-card>
        </a-col>

        <a-col :xs="24" :md="10">
          <a-card class="panel" :bordered="false" title="快捷入口">
            <a-list :data-source="quickLinks" item-layout="horizontal">
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta :title="item.title" :description="item.desc">
                    <template #avatar>
                      <div class="icon-badge" :style="{ background: item.bg }">
                        <component :is="item.icon" />
                      </div>
                    </template>
                  </a-list-item-meta>
                  <template #actions>
                    <a-button type="link" @click="go(item.to)">打开</a-button>
                  </template>
                </a-list-item>
              </template>
            </a-list>
          </a-card>
        </a-col>
      </a-row>
    </div>
  </ProLayout>
</template>

<script setup>
import { useRouter } from 'vue-router'
import ProLayout from '@/platform/components/ProLayout.vue'
import { AppstoreOutlined, FileSearchOutlined, PictureOutlined } from '@ant-design/icons-vue'

const router = useRouter()

const go = (path) => {
  router.push(path)
}

const quickLinks = [
  {
    title: '采集商品库',
    desc: 'product_collection 数据管理（改、删、查）',
    to: '/platform/product-collections',
    icon: AppstoreOutlined,
    bg: 'linear-gradient(135deg, rgba(14,165,233,0.95), rgba(34,197,94,0.95))'
  },
  {
    title: 'AI做图工作台',
    desc: '进入图片生成首页（移动端风格）',
    to: '/ai',
    icon: PictureOutlined,
    bg: 'linear-gradient(135deg, rgba(245,158,11,0.95), rgba(239,68,68,0.95))'
  },
  {
    title: 'TEMU 标题优化',
    desc: '按 spu 生成中英文标题与类目关键词',
    to: '/platform/temu-title-optimizer',
    icon: FileSearchOutlined,
    bg: 'linear-gradient(135deg, rgba(59,130,246,0.95), rgba(16,185,129,0.95))'
  }
]
</script>

<style scoped>
.home {
  max-width: 1240px;
  margin: 0 auto;
}

.hero {
  border-radius: 16px;
  min-height: 240px;
  background: radial-gradient(900px 260px at 12% 20%, rgba(14, 165, 233, 0.28), transparent 60%),
    radial-gradient(800px 300px at 85% 35%, rgba(34, 197, 94, 0.22), transparent 55%),
    linear-gradient(135deg, rgba(15, 23, 42, 0.92), rgba(15, 23, 42, 0.74));
  color: rgba(255, 255, 255, 0.92);
}

.hero-kicker {
  letter-spacing: 0.12em;
  text-transform: uppercase;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.72);
}

.hero-title {
  margin-top: 10px;
  font-size: 28px;
  font-weight: 800;
  line-height: 1.15;
}

.hero-sub {
  margin-top: 10px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.72);
  line-height: 1.6;
}

.hero-actions {
  margin-top: 18px;
}

.panel {
  border-radius: 16px;
}

.icon-badge {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: rgba(255, 255, 255, 0.92);
}
</style>
