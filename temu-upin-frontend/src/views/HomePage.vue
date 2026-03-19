<template>
  <div class="home-page">
    <van-nav-bar title="产品场景图生成" fixed />
    
    <div class="content">
      <div class="stats-card">
        <van-grid :column-num="4" :gutter="10">
          <van-grid-item>
            <div class="stat-item">
              <div class="stat-value">{{ stats.total }}</div>
              <div class="stat-label">全部</div>
            </div>
          </van-grid-item>
          <van-grid-item>
            <div class="stat-item">
              <div class="stat-value">{{ stats.draft }}</div>
              <div class="stat-label">草稿</div>
            </div>
          </van-grid-item>
          <van-grid-item>
            <div class="stat-item">
              <div class="stat-value">{{ stats.processing }}</div>
              <div class="stat-label">处理中</div>
            </div>
          </van-grid-item>
          <van-grid-item>
            <div class="stat-item">
              <div class="stat-value">{{ stats.completed }}</div>
              <div class="stat-label">已完成</div>
            </div>
          </van-grid-item>
        </van-grid>
      </div>
      
      <div class="action-bar">
        <van-button type="primary" size="large" round @click="goToCreate" icon="plus">
          新建产品
        </van-button>
        <van-button size="large" round @click="goToChannels" icon="setting" style="margin-top: 12px;">
          渠道管理
        </van-button>
      </div>
      
      <div class="product-list">
        <van-tabs v-model:active="activeTab" @change="onTabChange">
          <van-tab title="全部" name="all"></van-tab>
          <van-tab title="处理中" name="processing"></van-tab>
          <van-tab title="已完成" name="completed"></van-tab>
        </van-tabs>
        
        <div class="list-content">
          <van-loading v-if="loading" size="24px" class="loading-center" />
          <van-empty v-else-if="products.length === 0" description="暂无产品" />
          <van-cell-group v-else>
            <van-cell
              v-for="product in products"
              :key="product.id"
              is-link
              @click="goToDetail(product.id)"
            >
              <template #title>
                <div class="product-cell">
                  <div class="product-name">{{ product.name }}</div>
                  <div class="product-meta">
                    <van-tag :type="getStatusType(product.status)" size="small">
                      {{ getStatusText(product.status) }}
                    </van-tag>
                    <span class="sku-count">SKU: {{ product.skuCount }}</span>
                  </div>
                </div>
              </template>
              <template #label>
                <div class="product-images">
                  <van-image
                    v-if="product.coverImageUrl"
                    :src="product.coverImageUrl"
                    width="40"
                    height="40"
                    fit="cover"
                    round
                  />
                  <van-button
                    class="more-btn"
                    size="mini"
                    plain
                    icon="ellipsis"
                    @click.stop="openActions(product)"
                  />
                  <span class="image-count" v-if="product.thumbnailCount > 0">
                    {{ product.thumbnailCount + product.carouselCount + product.detailCount }}张
                  </span>
                </div>
              </template>
            </van-cell>
          </van-cell-group>
        </div>
      </div>

      <van-action-sheet
        v-model:show="showActionSheet"
        :actions="actionSheetActions"
        cancel-text="取消"
        close-on-click-action
        @select="onActionSelect"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showDialog, showToast, showFailToast } from 'vant'
import { productApi } from '@/api'

const router = useRouter()
const loading = ref(false)
const products = ref([])
const activeTab = ref('all')
const showActionSheet = ref(false)
const actionProduct = ref(null)

const stats = computed(() => {
  return {
    total: products.value.length,
    draft: products.value.filter(p => p.status === 'DRAFT').length,
    processing: products.value.filter(p => p.status === 'PROCESSING').length,
    completed: products.value.filter(p => p.status === 'COMPLETED').length
  }
})

const getStatusType = (status) => {
  const map = {
    DRAFT: 'default',
    PROCESSING: 'warning',
    COMPLETED: 'success',
    FAILED: 'danger'
  }
  return map[status] || 'default'
}

const getStatusText = (status) => {
  const map = {
    DRAFT: '草稿',
    PROCESSING: '处理中',
    COMPLETED: '已完成',
    FAILED: '失败'
  }
  return map[status] || status
}

const onTabChange = (name) => {
  loadProducts()
}

const loadProducts = async () => {
  loading.value = true
  try {
    const res = await productApi.getAll()
    if (res.success && res.data) {
      let list = res.data
      if (activeTab.value === 'processing') {
        list = list.filter(p => p.status === 'PROCESSING')
      } else if (activeTab.value === 'completed') {
        list = list.filter(p => p.status === 'COMPLETED')
      }
      products.value = list
    }
  } catch (error) {
    console.error('Failed to load products:', error)
  } finally {
    loading.value = false
  }
}

const goToCreate = () => {
  router.push('/ai/product/create')
}

const goToChannels = () => {
  router.push('/ai/channels')
}

const goToDetail = (id) => {
  router.push(`/ai/product/${id}`)
}

const openActions = (product) => {
  actionProduct.value = product
  showActionSheet.value = true
}

const actionSheetActions = computed(() => {
  const p = actionProduct.value
  const actions = []

  if (p?.coverSkuId) {
    actions.push({ name: '删除封面SKU', key: 'delete_cover_sku', color: '#ee0a24' })
  }
  if (p?.id) {
    actions.push({ name: '删除产品', key: 'delete_product', color: '#ee0a24' })
  }

  return actions
})

const onActionSelect = async (action) => {
  const p = actionProduct.value
  if (!p) return
  if (action.key === 'delete_cover_sku') {
    await deleteCoverSku(p)
  } else if (action.key === 'delete_product') {
    await deleteProduct(p)
  }
}

const deleteCoverSku = async (product) => {
  if (!product?.id || !product?.coverSkuId) {
    return
  }
  try {
    await showDialog({
      title: '确认删除SKU',
      message: '将删除该产品的封面SKU（当前首页显示的那一条）。删除后不可恢复，是否继续？'
    })
  } catch {
    return
  }

  try {
    const res = await productApi.deleteSku(product.id, product.coverSkuId)
    if (res.success) {
      showToast('SKU已删除')
      await loadProducts()
    }
  } catch (error) {
    showFailToast(error.message || '删除失败')
  }
}

const deleteProduct = async (product) => {
  if (!product?.id) {
    return
  }
  try {
    await showDialog({
      title: '确认删除产品',
      message: '将删除产品、SKU、生成结果，并同步删除 OSS 文件。删除后无法恢复，是否继续？'
    })
  } catch {
    return
  }

  try {
    const res = await productApi.delete(product.id)
    if (res.success) {
      showToast('产品已删除')
      await loadProducts()
    }
  } catch (error) {
    showFailToast(error.message || '删除失败')
  }
}

onMounted(() => {
  loadProducts()
})
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  background: #f5f5f5;
}

.content {
  padding-top: 46px;
  padding-bottom: 50px;
}

.stats-card {
  background: #fff;
  margin: 10px;
  border-radius: 8px;
  padding: 10px;
}

.stat-item {
  text-align: center;
}

.stat-value {
  font-size: 20px;
  font-weight: bold;
  color: #323233;
}

.stat-label {
  font-size: 12px;
  color: #969799;
  margin-top: 4px;
}

.action-bar {
  padding: 10px 16px;
}

.product-list {
  background: #fff;
  margin-top: 10px;
}

.list-content {
  min-height: 300px;
}

.loading-center {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}

.product-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.product-name {
  font-size: 15px;
  font-weight: 500;
}

.product-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sku-count {
  font-size: 12px;
  color: #969799;
}

.product-images {
  display: flex;
  align-items: center;
  gap: 8px;
}

.more-btn {
  height: 22px;
  width: 26px;
  padding: 0;
  border-radius: 999px;
  color: #646566;
  border-color: #ebedf0;
}

.image-count {
  font-size: 12px;
  color: #969799;
}
</style>
