<template>
  <ProLayout title="主销售属性推理任务">
    <div class="page">
      <a-card :bordered="false" class="card">
        <div class="head">
          <div class="title">推理任务列表</div>
          <a-space>
            <a-input v-model:value="q" placeholder="按商品标题搜索" style="width: 240px" allow-clear @pressEnter="reload" />
            <a-select v-model:value="status" style="width: 160px" allow-clear placeholder="状态">
              <a-select-option :value="0">待开始</a-select-option>
              <a-select-option :value="1">推理中</a-select-option>
              <a-select-option :value="2">已完成</a-select-option>
            </a-select>
            <a-button type="primary" @click="openCreate">添加任务</a-button>
            <a-button @click="reload" :loading="loading">刷新</a-button>
          </a-space>
        </div>

        <a-table
          rowKey="id"
          :columns="columns"
          :dataSource="rows"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 1100 }"
          size="middle"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'img'">
              <a-image v-if="record.productMainImage" :src="record.productMainImage" :width="54" :height="54" :preview="true" />
              <span v-else>-</span>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag>
            </template>
            <template v-else-if="column.key === 'time'">
              <div class="time">
                <div>start: <span class="mono">{{ fmtTime(record.startedAt) || '-' }}</span></div>
                <div>end: <span class="mono">{{ fmtTime(record.finishedAt) || '-' }}</span></div>
              </div>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a-button size="small" @click="openDetail(record)">详情</a-button>
                <a-button v-if="record.status === 2" size="small" @click="openSku(record)">查看SKU</a-button>
                <a-button size="small" type="primary" ghost @click="runNow(record)" :loading="runningId === record.id">立即执行</a-button>
                <a-popconfirm title="确定删除该任务？" ok-text="删除" cancel-text="取消" @confirm="removeTask(record)">
                  <a-button size="small" danger>删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>

        <div class="pager">
          <a-pagination
            :current="page + 1"
            :pageSize="size"
            :total="total"
            show-size-changer
            :pageSizeOptions="['10','20','50']"
            @change="onPageChange"
            @showSizeChange="onPageSizeChange"
          />
        </div>
      </a-card>

      <a-modal v-model:open="createOpen" title="添加任务" :confirm-loading="creating" @ok="create" width="760">
        <a-form layout="vertical">
          <a-form-item label="搜索商品（按标题）">
            <a-input v-model:value="productQ" placeholder="输入商品标题关键词" allow-clear @pressEnter="searchProducts" />
          </a-form-item>
          <a-form-item>
            <a-button @click="searchProducts" :loading="productLoading">搜索</a-button>
          </a-form-item>

          <a-table
            rowKey="id"
            :columns="productColumns"
            :dataSource="productRows"
            :loading="productLoading"
            :pagination="false"
            size="small"
            :rowSelection="rowSelection"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'img'">
                <a-image v-if="record.productMainImage" :src="record.productMainImage" :width="48" :height="48" :preview="true" />
                <span v-else>-</span>
              </template>
            </template>
          </a-table>
        </a-form>
      </a-modal>

      <a-drawer v-model:open="detailOpen" :title="detailTitle" width="960" :destroyOnClose="true">
        <div v-if="detail" class="detail">
          <a-descriptions bordered size="small" :column="1">
            <a-descriptions-item label="spuId">{{ detail.spuId }}</a-descriptions-item>
            <a-descriptions-item label="商品标题">{{ detail.productName }}</a-descriptions-item>
            <a-descriptions-item label="状态">
              <a-tag :color="statusColor(detail.status)">{{ statusText(detail.status) }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="推理结果">{{ detail.resultSummary || '-' }}</a-descriptions-item>
            <a-descriptions-item label="leafCatId">{{ detail.leafCatId || '-' }}</a-descriptions-item>
            <a-descriptions-item label="start">{{ fmtTime(detail.startedAt) || '-' }}</a-descriptions-item>
            <a-descriptions-item label="end">{{ fmtTime(detail.finishedAt) || '-' }}</a-descriptions-item>
          </a-descriptions>

          <a-divider />

          <a-tabs v-model:activeKey="activeTab">
            <a-tab-pane key="prompt" tab="提示词全文">
              <pre class="pre">{{ pretty(detail.promptText) }}</pre>
            </a-tab-pane>
            <a-tab-pane key="skuPreview" tab="SKU预览">
              <div v-if="skuPreviewGroups.length" class="sku-preview">
                <div class="spec-library">
                  <div class="section-title">规格值总览</div>
                  <div class="spec-chip-list">
                    <div v-for="item in specLibrary" :key="`${item.parentSpecId}-${item.specId}-${item.propValue}`" class="spec-chip">
                      <div class="spec-chip-label">{{ item.parentSpecName || `规格${item.parentSpecId}` }}</div>
                      <div class="spec-chip-value">{{ item.propValue || '-' }}</div>
                    </div>
                  </div>
                </div>

                <div class="skc-list">
                  <div v-for="group in skuPreviewGroups" :key="group.key" class="skc-card">
                    <div class="skc-head">
                      <div>
                        <div class="skc-title">{{ group.title }}</div>
                        <div class="skc-subtitle">{{ group.mainSpecText }}</div>
                      </div>
                      <a-tag color="blue">{{ group.skuCount }} 个SKU</a-tag>
                    </div>

                    <div class="main-spec-list">
                      <div v-for="spec in group.mainSpecs" :key="`${group.key}-${spec.parentSpecId}-${spec.specId}`" class="main-spec-item">
                        <span class="main-spec-name">{{ spec.parentSpecName || `规格${spec.parentSpecId}` }}</span>
                        <span class="main-spec-value">{{ spec.propValue || `specId:${spec.specId}` }}</span>
                      </div>
                    </div>

                    <div class="sku-grid">
                      <div v-for="sku in group.skus" :key="sku.key" class="sku-tile">
                        <a-image v-if="sku.thumbUrl" :src="sku.thumbUrl" :width="88" :height="88" class="sku-image" />
                        <div v-else class="sku-image sku-image-empty">无图</div>
                        <div class="sku-name">{{ sku.specText }}</div>
                        <div class="sku-meta-row">
                          <span>编码</span>
                          <strong>{{ sku.extCode || '-' }}</strong>
                        </div>
                        <div class="sku-meta-row">
                          <span>供货价</span>
                          <strong>{{ sku.priceText }}</strong>
                        </div>
                        <div class="sku-meta-row">
                          <span>库存</span>
                          <strong>{{ sku.stockText }}</strong>
                        </div>
                        <div class="sku-meta-row">
                          <span>重量</span>
                          <strong>{{ sku.weightText }}</strong>
                        </div>
                        <div class="sku-meta-row">
                          <span>尺寸</span>
                          <strong>{{ sku.volumeText }}</strong>
                        </div>
                        <div class="sku-spec-tags">
                          <span v-for="spec in sku.specs" :key="`${sku.key}-${spec.parentSpecId}-${spec.specId}`" class="sku-spec-tag">
                            {{ spec.parentSpecName || `规格${spec.parentSpecId}` }}: {{ spec.propValue || `specId:${spec.specId}` }}
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <a-empty v-else description="暂无可预览的 SKU 草案" />
            </a-tab-pane>
            <a-tab-pane key="raw" tab="返回原始值">
              <pre class="pre">{{ pretty(detail.responseRaw || detail.responseContent) }}</pre>
            </a-tab-pane>
            <a-tab-pane key="parsed" tab="解析后的JSON">
              <pre class="pre">{{ pretty(detail.parsedJson) }}</pre>
            </a-tab-pane>
            <a-tab-pane key="mainProductSkuSpecReqs" tab="mainProductSkuSpecReqs">
              <pre class="pre">{{ pretty(detail.mainProductSkuSpecReqs) }}</pre>
            </a-tab-pane>
            <a-tab-pane key="productSpecPropertyReqs" tab="productSpecPropertyReqs">
              <pre class="pre">{{ pretty(detail.productSpecPropertyReqs) }}</pre>
            </a-tab-pane>
            <a-tab-pane key="productSkuReqs" tab="productSkuReqs">
              <pre class="pre">{{ pretty(detail.productSkuReqs) }}</pre>
            </a-tab-pane>
            <a-tab-pane key="result" tab="TEMU格式JSON">
              <pre class="pre">{{ pretty(detail.resultJson) }}</pre>
            </a-tab-pane>
            <a-tab-pane key="err" tab="错误" v-if="detail.errorMsg">
              <pre class="pre">{{ detail.errorMsg }}</pre>
            </a-tab-pane>
          </a-tabs>
        </div>
      </a-drawer>
    </div>
  </ProLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import { mainSaleSpecInferenceApi } from '@/platform/api/mainSaleSpecInference'
import { productCollectionApi } from '@/platform/api/productCollections'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(0)
const size = ref(20)
const q = ref('')
const status = ref(undefined)

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: 'spuId', dataIndex: 'spuId', key: 'spuId', width: 90 },
  { title: '主图', key: 'img', width: 80 },
  { title: '商品标题', dataIndex: 'productName', key: 'productName' },
  { title: '状态', key: 'status', width: 110 },
  { title: '推理结果', dataIndex: 'resultSummary', key: 'resultSummary', width: 240 },
  { title: '时间', key: 'time', width: 260 },
  { title: '操作', key: 'actions', width: 260, fixed: 'right' }
]

const statusText = (s) => {
  if (s === 0) return '待开始'
  if (s === 1) return '推理中'
  if (s === 2) return '已完成'
  return '-'
}

const statusColor = (s) => {
  if (s === 2) return 'green'
  if (s === 1) return 'blue'
  if (s === 0) return 'default'
  return 'default'
}

const reload = async () => {
  loading.value = true
  try {
    const res = await mainSaleSpecInferenceApi.listTasks({
      q: (q.value || '').trim() || undefined,
      status: status.value,
      page: page.value,
      size: size.value
    })
    if (res?.success) {
      const p = res.data
      rows.value = Array.isArray(p?.content) ? p.content : []
      total.value = Number(p?.totalElements || 0)
      return
    }
    message.error(res?.message || '加载失败')
  } catch (e) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const onPageChange = (p) => {
  page.value = Math.max(0, Number(p || 1) - 1)
  reload()
}

const onPageSizeChange = (_p, ps) => {
  size.value = Number(ps || 20)
  page.value = 0
  reload()
}

const runningId = ref(null)
const runNow = async (r) => {
  if (!r?.id) return
  runningId.value = r.id
  try {
    const res = await mainSaleSpecInferenceApi.runTask(r.id)
    if (res?.success) {
      message.success(r.status === 2 ? '已重新执行' : '已触发执行')
      await reload()
      return
    }
    message.error(res?.message || '执行失败')
  } catch (e) {
    message.error(e.message || '执行失败')
  } finally {
    runningId.value = null
  }
}

const removeTask = async (r) => {
  if (!r?.id) return
  try {
    const res = await mainSaleSpecInferenceApi.deleteTask(r.id)
    if (res?.success) {
      message.success('删除成功')
      if (detail.value?.id === r.id) {
        detailOpen.value = false
        detail.value = null
      }
      await reload()
      return
    }
    message.error(res?.message || '删除失败')
  } catch (e) {
    message.error(e.message || '删除失败')
  }
}

const createOpen = ref(false)
const creating = ref(false)
const productQ = ref('')
const productLoading = ref(false)
const productRows = ref([])
const selectedProductId = ref(null)

const productColumns = [
  { title: '主图', key: 'img', width: 70 },
  { title: 'spuId', dataIndex: 'id', key: 'id', width: 90 },
  { title: '商品标题', dataIndex: 'productName', key: 'productName' }
]

const rowSelection = computed(() => ({
  type: 'radio',
  selectedRowKeys: selectedProductId.value ? [selectedProductId.value] : [],
  onChange: (keys) => {
    selectedProductId.value = Array.isArray(keys) && keys.length ? keys[0] : null
  }
}))

const openCreate = async () => {
  createOpen.value = true
  selectedProductId.value = null
  productRows.value = []
  productQ.value = ''
  await searchProducts(true)
}

const searchProducts = async (initial = false) => {
  productLoading.value = true
  try {
    const qq = initial ? '' : (productQ.value || '').trim()
    const res = await productCollectionApi.list({ q: qq, page: 0, size: 10 })
    if (res?.success) {
      const p = res.data
      productRows.value = Array.isArray(p?.content) ? p.content : []
      return
    }
    message.error(res?.message || '搜索失败')
  } catch (e) {
    message.error(e.message || '搜索失败')
  } finally {
    productLoading.value = false
  }
}

const create = async () => {
  if (!selectedProductId.value) return message.error('请选择一个商品')
  creating.value = true
  try {
    const res = await mainSaleSpecInferenceApi.createTask(selectedProductId.value)
    if (res?.success) {
      message.success(res?.message === 'Already exists' ? '该 spuId 已存在任务，已直接定位到原任务' : '已创建任务')
      createOpen.value = false
      await reload()
      return
    }
    message.error(res?.message || '创建失败')
  } catch (e) {
    message.error(e.message || '创建失败')
  } finally {
    creating.value = false
  }
}

const detailOpen = ref(false)
const detail = ref(null)
const activeTab = ref('skuPreview')

const detailTitle = computed(() => {
  if (!detail.value) return '任务详情'
  return `任务详情 id=${detail.value.id} spuId=${detail.value.spuId}`
})

const openDetail = async (r) => {
  activeTab.value = 'skuPreview'
  await loadDetail(r?.id)
}

const openSku = async (r) => {
  activeTab.value = 'skuPreview'
  await loadDetail(r?.id)
}

const loadDetail = async (id) => {
  if (!id) return
  detailOpen.value = true
  detail.value = null
  try {
    const res = await mainSaleSpecInferenceApi.getTask(id)
    if (res?.success) {
      detail.value = res.data
      return
    }
    message.error(res?.message || '加载失败')
  } catch (e) {
    message.error(e.message || '加载失败')
  }
}

const parseJsonMaybe = (raw, fallback = []) => {
  if (!raw) return fallback
  if (Array.isArray(raw) || (raw && typeof raw === 'object')) return raw
  try {
    return JSON.parse(String(raw))
  } catch {
    return fallback
  }
}

const specLibrary = computed(() => {
  const list = parseJsonMaybe(detail.value?.productSpecPropertyReqs, [])
  return Array.isArray(list) ? list : []
})

const specLookup = computed(() => {
  const map = new Map()
  specLibrary.value.forEach((item) => {
    if (!item) return
    map.set(`${item.parentSpecId}_${item.specId}`, item)
  })
  return map
})

const skuPreviewGroups = computed(() => {
  const mainGroups = parseJsonMaybe(detail.value?.mainProductSkuSpecReqs, [])
  const skuGroups = parseJsonMaybe(detail.value?.productSkuReqs, [])
  const total = Math.max(Array.isArray(mainGroups) ? mainGroups.length : 0, Array.isArray(skuGroups) ? skuGroups.length : 0)
  const out = []
  for (let index = 0; index < total; index += 1) {
    const rawMainSpecs = Array.isArray(mainGroups?.[index]) ? mainGroups[index] : []
    const rawSkus = Array.isArray(skuGroups?.[index]) ? skuGroups[index] : []
    const mainSpecs = rawMainSpecs.map(normalizeSpecItem).filter(Boolean)
    const skus = rawSkus.map((item, skuIndex) => normalizeSkuItem(item, skuIndex)).filter(Boolean)
    out.push({
      key: `skc-${index}`,
      title: `销售组 ${index + 1}`,
      skuCount: skus.length,
      mainSpecs,
      mainSpecText: mainSpecs.map((spec) => `${spec.parentSpecName || `规格${spec.parentSpecId}`}: ${spec.propValue || `specId:${spec.specId}`}`).join(' / ') || '未识别主销售属性',
      skus
    })
  }
  return out.filter((item) => item.mainSpecs.length || item.skus.length)
})

const normalizeSpecItem = (item) => {
  if (!item) return null
  const base = specLookup.value.get(`${item.parentSpecId}_${item.specId}`) || {}
  return {
    parentSpecId: item.parentSpecId,
    specId: item.specId,
    parentSpecName: base.parentSpecName || base.parent_spec_name || '',
    propValue: base.propValue || base.prop_value || ''
  }
}

const normalizeSkuItem = (item, index) => {
  if (!item || typeof item !== 'object') return null
  const specs = Array.isArray(item.productSkuSpecReqs) ? item.productSkuSpecReqs.map(normalizeSpecItem).filter(Boolean) : []
  return {
    key: item.extCode || `sku-${index}`,
    thumbUrl: item.thumbUrl || '',
    extCode: item.extCode || '',
    priceText: formatPrice(item.siteSupplierPrices),
    stockText: formatStock(item.productSkuStockQuantityReq),
    weightText: formatWeight(item.productSkuWhExtAttrReq?.productSkuWeightReq),
    volumeText: formatVolume(item.productSkuWhExtAttrReq?.productSkuVolumeReq),
    specText: specs.map((spec) => spec.propValue || `specId:${spec.specId}`).join(' / ') || '未配置规格值',
    specs
  }
}

const formatPrice = (prices) => {
  const first = Array.isArray(prices) && prices.length ? prices[0] : null
  const cents = Number(first?.supplierPrice || 0)
  if (!Number.isFinite(cents) || cents <= 0) return '-'
  return `CNY ${(cents / 100).toFixed(2)}`
}

const formatStock = (stockReq) => {
  const rows = Array.isArray(stockReq?.warehouseStockQuantityReqs) ? stockReq.warehouseStockQuantityReqs : []
  if (!rows.length) return '-'
  return rows.map((item) => `${item.warehouseId || 'WH'}:${item.targetStockAvailable ?? 0}`).join(' / ')
}

const formatWeight = (weightReq) => {
  const value = Number(weightReq?.value || 0)
  if (!Number.isFinite(value) || value <= 0) return '-'
  return `${(value / 1000).toFixed(1)} g`
}

const formatVolume = (volumeReq) => {
  const len = Number(volumeReq?.len || 0)
  const width = Number(volumeReq?.width || 0)
  const height = Number(volumeReq?.height || 0)
  if (![len, width, height].some((item) => Number.isFinite(item) && item > 0)) return '-'
  return `${len} x ${width} x ${height} mm`
}

const pretty = (s) => {
  if (!s) return ''
  const txt = String(s)
  try {
    const obj = JSON.parse(txt)
    return JSON.stringify(obj, null, 2)
  } catch {
    return txt
  }
}

const fmtTime = (v) => {
  if (!v) return ''
  if (Array.isArray(v)) {
    const [y, m, d, hh, mm, ss, ns] = v
    const pad = (n, w = 2) => String(n || 0).padStart(w, '0')
    const ms = ns == null ? 0 : Math.floor(Number(ns) / 1e6)
    return `${y}-${pad(m)}-${pad(d)} ${pad(hh)}:${pad(mm)}:${pad(ss)}.${pad(ms, 3)}`
  }
  return String(v)
}

onMounted(() => {
  reload()
})
</script>

<style scoped>
.page {
  display: grid;
  gap: 12px;
}

.card {
  border-radius: 16px;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 12px;
  flex-wrap: wrap;
}

.title {
  font-weight: 800;
  color: #0f172a;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}

.time {
  font-size: 12px;
  color: rgba(15, 23, 42, 0.78);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.pre {
  background: rgba(2, 6, 23, 0.92);
  color: rgba(255, 255, 255, 0.92);
  padding: 12px;
  border-radius: 12px;
  overflow: auto;
  max-height: 60vh;
  white-space: pre-wrap;
}

.sku-preview {
  display: grid;
  gap: 16px;
}

.section-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 10px;
}

.spec-library {
  padding: 16px;
  border-radius: 18px;
  background: linear-gradient(135deg, #fff8eb 0%, #fff 100%);
  border: 1px solid rgba(245, 158, 11, 0.18);
}

.spec-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.spec-chip {
  min-width: 120px;
  padding: 10px 12px;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
}

.spec-chip-label {
  font-size: 12px;
  color: rgba(15, 23, 42, 0.56);
}

.spec-chip-value {
  margin-top: 4px;
  font-weight: 700;
  color: #0f172a;
}

.skc-list {
  display: grid;
  gap: 14px;
}

.skc-card {
  padding: 18px;
  border-radius: 20px;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
  border: 1px solid rgba(59, 130, 246, 0.14);
}

.skc-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.skc-title {
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.skc-subtitle {
  margin-top: 4px;
  color: rgba(15, 23, 42, 0.64);
}

.main-spec-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.main-spec-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.08);
}

.main-spec-name {
  color: rgba(15, 23, 42, 0.6);
}

.main-spec-value {
  font-weight: 700;
  color: #1d4ed8;
}

.sku-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.sku-tile {
  padding: 14px;
  border-radius: 18px;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.06);
}

.sku-image {
  border-radius: 14px;
  overflow: hidden;
}

.sku-image-empty {
  width: 88px;
  height: 88px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(148, 163, 184, 0.12);
  color: rgba(15, 23, 42, 0.48);
}

.sku-name {
  margin-top: 12px;
  font-weight: 700;
  color: #0f172a;
}

.sku-meta-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-top: 8px;
  color: rgba(15, 23, 42, 0.68);
}

.sku-meta-row strong {
  color: #0f172a;
}

.sku-spec-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.sku-spec-tag {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(14, 165, 233, 0.08);
  color: #075985;
  font-size: 12px;
}
</style>
