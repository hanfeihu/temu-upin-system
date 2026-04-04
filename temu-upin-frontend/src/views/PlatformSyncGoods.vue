<template>
  <ProLayout title="TEMU 商品数据">
    <div class="page">
      <a-card class="toolbar" :bordered="false">
        <ShopTabs v-model="shopId" :shops="shops" :loading="shopsLoading" empty-text="暂无可用店铺，请先配置店铺" @change="onShopChange" />
        <a-form layout="inline">
          <a-form-item label="关键词">
            <a-input v-model:value="filters.keyword" placeholder="商品名称" style="width: 220px" allow-clear @pressEnter="reload" />
          </a-form-item>
          <a-form-item label="加站状态">
            <a-select v-model:value="filters.skcSiteStatus" placeholder="全部" allow-clear style="width: 140px">
              <a-select-option :value="1">已加站</a-select-option>
              <a-select-option :value="0">未加站</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="美国站供货价">
            <a-input-number v-model:value="filters.minSupplierPrice" :min="0" placeholder="最小(分)" style="width: 120px" />
          </a-form-item>
          <a-form-item>
            <a-input-number v-model:value="filters.maxSupplierPrice" :min="0" placeholder="最大(分)" style="width: 120px" />
          </a-form-item>
          <a-form-item>
            <a-space>
              <a-button type="primary" :loading="loading" @click="reload">查询</a-button>
              <a-button @click="reset">重置</a-button>
              <a-button :loading="exporting" :disabled="!shopId" @click="exportSkuRows">导出SKU</a-button>
              <a-button danger :loading="clearing" :disabled="!shopId" @click="clearShopSyncData">清空同步数据</a-button>
            </a-space>
          </a-form-item>
        </a-form>
      </a-card>

      <a-card v-if="shopId" class="table-card" :bordered="false">
        <a-table rowKey="id" :columns="columns" :dataSource="rows" :loading="loading" :pagination="pagination" :scroll="{ x: 1280 }" tableLayout="fixed" @change="onTableChange">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'mainImageUrl'">
              <a-image v-if="record.mainImageUrl" :src="record.mainImageUrl" :width="50" :height="50" style="object-fit: cover; border-radius: 4px" />
              <span v-else>-</span>
            </template>
            <template v-else-if="column.key === 'productName'">
              <a @click="openDetail(record)">{{ record.productName || '-' }}</a>
            </template>
            <template v-else-if="column.key === 'productIdentity'">
              <div class="goods-id-block">
                <div>商品ID：{{ record.productId || '-' }}</div>
                <div>SKC ID：{{ record.productSkcId || '-' }}</div>
                <div>外部编码：{{ record.extCode || '-' }}</div>
              </div>
            </template>
            <template v-else-if="column.key === 'site100SupplierPriceRange'">
              <span>{{ formatPriceRange(record.site100MinSupplierPrice, record.site100MaxSupplierPrice) }}</span>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
            </template>
            <template v-else-if="column.key === 'skcSiteStatus'">
              <a-tag :color="record.skcSiteStatus === 1 ? 'green' : 'default'">{{ record.skcSiteStatus === 1 ? '已加站' : '未加站' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'syncedAt'">
              <span class="mono">{{ formatDT(record.syncedAt) }}</span>
            </template>
          </template>
        </a-table>
      </a-card>

      <!-- 详情弹窗 -->
      <a-modal v-model:open="detailOpen" title="商品详情" :footer="null" width="960" :bodyStyle="{ maxHeight: '75vh', overflowY: 'auto' }">
        <template v-if="detail">
          <div class="detail-hero">
            <a-image v-if="detail.mainImageUrl" :src="detail.mainImageUrl" :width="120" />
            <div class="detail-hero__meta">
              <div class="detail-title">{{ detail.productName || '-' }}</div>
              <a-space wrap>
                <a-tag :color="detail.skcSiteStatus === 1 ? 'green' : 'default'">{{ detail.skcSiteStatus === 1 ? '已加站' : '未加站' }}</a-tag>
                <a-tag :color="detail.matchJitMode ? 'blue' : 'default'">JIT匹配: {{ detail.matchJitMode ? '是' : '否' }}</a-tag>
                <a-tag :color="detail.matchSkcJitMode ? 'blue' : 'default'">SKC JIT: {{ detail.matchSkcJitMode ? '是' : '否' }}</a-tag>
                <a-tag :color="detail.isSupportPersonalization ? 'purple' : 'default'">支持定制: {{ detail.isSupportPersonalization ? '是' : '否' }}</a-tag>
              </a-space>
            </div>
          </div>

          <a-descriptions bordered :column="2" size="small">
            <a-descriptions-item label="商品ID">{{ detail.productId }}</a-descriptions-item>
            <a-descriptions-item label="SKC ID">{{ detail.productSkcId }}</a-descriptions-item>
            <a-descriptions-item label="商品名称" :span="2">{{ detail.productName }}</a-descriptions-item>
            <a-descriptions-item label="外部编码">{{ detail.extCode || '-' }}</a-descriptions-item>
            <a-descriptions-item label="叶子类目">{{ detail.leafCatName || '-' }}</a-descriptions-item>
            <a-descriptions-item label="加站状态">{{ detail.skcSiteStatus === 1 ? '已加站' : '未加站' }}</a-descriptions-item>
            <a-descriptions-item label="支持定制">{{ detail.isSupportPersonalization ? '是' : '否' }}</a-descriptions-item>
            <a-descriptions-item label="选品状态">{{ detail.selectStatus ?? '-' }}</a-descriptions-item>
            <a-descriptions-item label="JIT匹配">{{ detail.matchJitMode ? '是' : '否' }}</a-descriptions-item>
            <a-descriptions-item label="SKC JIT匹配">{{ detail.matchSkcJitMode ? '是' : '否' }}</a-descriptions-item>
            <a-descriptions-item label="JIT申请状态">{{ detail.applyJitStatus ?? '-' }}</a-descriptions-item>
            <a-descriptions-item label="建议关闭JIT">{{ detail.suggestCloseJit == null ? '-' : (detail.suggestCloseJit ? '是' : '否') }}</a-descriptions-item>
            <a-descriptions-item label="运费模板">{{ detail.freightTemplateId || '-' }}</a-descriptions-item>
            <a-descriptions-item label="保证发货秒数">{{ detail.shipmentLimitSecond ?? '-' }}</a-descriptions-item>
            <a-descriptions-item label="TEMU创建时间">{{ formatTimestamp(detail.temuCreatedAt) }}</a-descriptions-item>
            <a-descriptions-item label="同步时间">{{ formatDT(detail.syncedAt) }}</a-descriptions-item>
            <a-descriptions-item label="类目路径" :span="2">{{ formatCategories(detail.categoriesJson) }}</a-descriptions-item>
          </a-descriptions>

          <a-divider>站点</a-divider>
          <a-space v-if="detail.siteList?.length" wrap>
            <a-tag v-for="s in detail.siteList" :key="s.siteId" color="blue">{{ s.siteName || s.siteId }}</a-tag>
          </a-space>
          <span v-else>-</span>

          <a-divider>属性</a-divider>
          <a-table :dataSource="detail.propertyList || []" rowKey="propertyRowKey" :pagination="false" bordered size="small">
            <a-table-column title="属性ID" dataIndex="pid" width="100" />
            <a-table-column title="属性名" dataIndex="propName" width="180" />
            <a-table-column title="值ID" dataIndex="vid" width="100" />
            <a-table-column title="属性值" dataIndex="propValue" />
            <a-table-column title="单位" dataIndex="valueUnit" width="100">
              <template #default="{ record }">{{ record.valueUnit || '-' }}</template>
            </a-table-column>
          </a-table>

          <a-divider>SKU 列表</a-divider>
          <a-table :dataSource="detail.skuList || []" rowKey="id" :pagination="false" bordered size="small" :scroll="{ x: 1200 }">
            <a-table-column title="SKU ID" dataIndex="productSkuId" width="120" />
            <a-table-column title="外部编码" dataIndex="extCode" width="140" />
            <a-table-column title="规格" key="specs" width="200">
              <template #default="{ record }">
                <div v-for="sp in (record.specList || [])" :key="sp.specId">{{ sp.parentSpecName }}: {{ sp.specName }}</div>
                <span v-if="!record.specList?.length">-</span>
              </template>
            </a-table-column>
            <a-table-column title="重量(mg)" dataIndex="weightMg" width="90" />
            <a-table-column title="长*宽*高(mm)" key="volume" width="140">
              <template #default="{ record }">{{ formatVolume(record) }}</template>
            </a-table-column>
            <a-table-column title="库存" dataIndex="virtualStock" width="80" />
            <a-table-column title="敏感货" key="isSensitive" width="80">
              <template #default="{ record }">{{ record.isSensitive ? '是' : '否' }}</template>
            </a-table-column>
            <a-table-column title="易碎" key="isFragile" width="80">
              <template #default="{ record }">{{ record.isFragile == null ? '-' : (record.isFragile ? '是' : '否') }}</template>
            </a-table-column>
            <a-table-column title="发货模式" dataIndex="shippingMode" width="90" />
            <a-table-column title="币种" key="currency" width="80">
              <template #default="{ record }">{{ record.price?.currencyType || '-' }}</template>
            </a-table-column>
            <a-table-column title="站点价格" key="sitePrices" width="240">
              <template #default="{ record }">
                <div v-if="record.price?.sitePrices?.length" class="site-price-list">
                  <div v-for="sitePrice in record.price.sitePrices" :key="`${record.id}-${sitePrice.siteId}`">
                    站点{{ sitePrice.siteId }}: {{ sitePrice.supplierPrice ?? '-' }} / 状态{{ sitePrice.priceReviewStatus ?? '-' }}
                  </div>
                </div>
                <span v-else>-</span>
              </template>
            </a-table-column>
          </a-table>
        </template>
      </a-modal>
    </div>
  </ProLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Modal, message } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import ShopTabs from '@/platform/components/ShopTabs.vue'
import { syncApi } from '@/platform/api/sync'
import { temuShopsApi } from '@/platform/api/temuShops'

const shopsLoading = ref(false)
const shops = ref([])
const shopId = ref(null)
const loading = ref(false)
const exporting = ref(false)
const clearing = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const filters = reactive({ keyword: '', skcSiteStatus: 1, minSupplierPrice: undefined, maxSupplierPrice: undefined })

const detailOpen = ref(false)
const detail = ref(null)

const columns = [
  { title: '图片', key: 'mainImageUrl', width: 70 },
  { title: '商品名称', key: 'productName' },
  { title: '商品信息', key: 'productIdentity', width: 220 },
  { title: '供货价', key: 'site100SupplierPriceRange', width: 160 },
  { title: '加站状态', key: 'skcSiteStatus', width: 100 },
  { title: '叶子类目', dataIndex: 'leafCatName', width: 120 },
  { title: '同步时间', key: 'syncedAt', width: 180 },
  { title: '操作', key: 'actions', width: 90, fixed: 'right' }
]

const pagination = computed(() => ({
  current: page.value, pageSize: pageSize.value, total: total.value,
  showSizeChanger: true, showTotal: (t) => `共 ${t} 条`, pageSizeOptions: ['10', '20', '50', '100']
}))

const pad2 = (n) => String(n ?? '').padStart(2, '0')
const formatDT = (v) => {
  if (!v) return '-'
  if (Array.isArray(v) && v.length >= 6) { const [y, mo, d, h, m, s] = v; return `${y}-${pad2(mo)}-${pad2(d)} ${pad2(h)}:${pad2(m)}:${pad2(s)}` }
  return typeof v === 'string' ? v : '-'
}

const formatTimestamp = (v) => {
  if (!v) return '-'
  const date = new Date(Number(v))
  return Number.isNaN(date.getTime()) ? String(v) : `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`
}

const formatCategories = (raw) => {
  if (!raw) return '-'
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    const names = []
    for (let index = 1; index <= 10; index += 1) {
      const cat = parsed?.[`cat${index}`]
      if (cat?.catName) names.push(cat.catName)
    }
    return names.length ? names.join(' / ') : '-'
  } catch {
    return typeof raw === 'string' ? raw : '-'
  }
}

const formatVolume = (record) => {
  const values = [record.lengthMm, record.widthMm, record.heightMm]
  return values.every((item) => item != null) ? values.join(' * ') : '-'
}

const formatPrice = (value) => {
  if (value === undefined || value === null || value === '') return '-'
  const numericValue = Number(value)
  if (Number.isNaN(numericValue)) return value
  return (numericValue / 100).toFixed(2)
}

const formatPriceRange = (minValue, maxValue) => {
  if (minValue === undefined || minValue === null || minValue === '') return '-'
  if (maxValue === undefined || maxValue === null || maxValue === '') return formatPrice(minValue)
  const minText = formatPrice(minValue)
  const maxText = formatPrice(maxValue)
  return minText === maxText ? minText : `${minText}～${maxText}`
}

const propertyRowKey = (record, index) => `${record.pid}-${record.vid ?? 'na'}-${index}`

const buildGoodsExportQuery = () => {
  const params = new URLSearchParams()
  params.set('shopId', shopId.value)
  if (filters.keyword) params.set('keyword', filters.keyword)
  if (filters.skcSiteStatus !== undefined) params.set('skcSiteStatus', String(filters.skcSiteStatus))
  if (filters.minSupplierPrice !== undefined && filters.minSupplierPrice !== null) params.set('minSupplierPrice', String(filters.minSupplierPrice))
  if (filters.maxSupplierPrice !== undefined && filters.maxSupplierPrice !== null) params.set('maxSupplierPrice', String(filters.maxSupplierPrice))
  return params.toString()
}

const parseFilename = (contentDisposition) => {
  if (!contentDisposition) return `temu-goods-sku-export-${Date.now()}.csv`
  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) return decodeURIComponent(utf8Match[1])
  const plainMatch = contentDisposition.match(/filename="?([^";]+)"?/i)
  if (plainMatch?.[1]) return plainMatch[1]
  return `temu-goods-sku-export-${Date.now()}.csv`
}

const downloadBlob = (blob, filename) => {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}

const loadShops = async () => {
  shopsLoading.value = true
  try {
    const r = await temuShopsApi.list({ enabled: true })
    shops.value = r?.success ? (r.data || []) : []
    const firstShopId = shops.value[0]?.shopId || null
    if (!firstShopId) {
      shopId.value = null
      rows.value = []
      total.value = 0
      return
    }
    if (!shopId.value || !shops.value.some((item) => item.shopId === shopId.value)) {
      shopId.value = firstShopId
      await reload()
    }
  }
  catch { message.error('店铺加载失败') }
  finally { shopsLoading.value = false }
}

const onShopChange = () => { if (shopId.value) reload() }

const fetchList = async () => {
  if (!shopId.value) return
  loading.value = true
  try {
    const res = await syncApi.getGoodsList({
      shopId: shopId.value,
      keyword: filters.keyword || undefined,
      skcSiteStatus: filters.skcSiteStatus,
      minSupplierPrice: filters.minSupplierPrice,
      maxSupplierPrice: filters.maxSupplierPrice,
      page: page.value,
      pageSize: pageSize.value
    })
    if (res?.success) { rows.value = res.data?.content || []; total.value = res.data?.totalElements || 0 }
    else message.error(res?.message || '加载失败')
  } catch (e) { message.error(e.message || '加载失败') }
  finally { loading.value = false }
}

const reload = () => { page.value = 1; fetchList() }
const reset = () => {
  filters.keyword = ''
  filters.skcSiteStatus = 1
  filters.minSupplierPrice = undefined
  filters.maxSupplierPrice = undefined
  reload()
}
const onTableChange = (p) => { page.value = p.current; pageSize.value = p.pageSize; fetchList() }

const exportSkuRows = async () => {
  if (!shopId.value) {
    message.warning('请先选择店铺')
    return
  }
  exporting.value = true
  try {
    const response = await fetch(`/api/sync/goods/export?${buildGoodsExportQuery()}`)
    const contentType = response.headers.get('content-type') || ''
    if (!response.ok || contentType.includes('application/json')) {
      let errorMessage = '导出失败'
      try {
        const payload = await response.json()
        errorMessage = payload?.message || payload?.error || errorMessage
      } catch {
      }
      throw new Error(errorMessage)
    }
    const blob = await response.blob()
    downloadBlob(blob, parseFilename(response.headers.get('content-disposition')))
    message.success('导出已开始')
  } catch (e) {
    message.error(e.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

const clearShopSyncData = () => {
  if (!shopId.value) {
    message.warning('请先选择店铺')
    return
  }
  Modal.confirm({
    title: '确认清空当前店铺同步数据',
    content: '会删除当前店铺已同步的商品、SKU、规格、条码、站点、属性、生命周期、供货价、核价单、调价单、活动报名、运费模板、发货仓库以及同步任务日志。该操作仅影响本系统本地数据，不会删除 TEMU 平台数据。若当前有同步任务或商品明细回填在执行，系统会拒绝清空。',
    okText: '确认清空',
    okButtonProps: { danger: true },
    width: 720,
    onOk: async () => {
      clearing.value = true
      try {
        const res = await syncApi.clearShopSyncData(shopId.value)
        if (res?.success) {
          detailOpen.value = false
          detail.value = null
          rows.value = []
          total.value = 0
          message.success(res.message || '同步数据已清空')
          await fetchList()
        } else {
          message.error(res?.message || '清空失败')
        }
      } catch (e) {
        message.error(e.message || '清空失败')
      } finally {
        clearing.value = false
      }
    }
  })
}

const openDetail = async (record) => {
  try {
    const res = await syncApi.getGoodsDetail(record.id)
    if (res?.success) { detail.value = res.data; detailOpen.value = true }
    else message.error(res?.message || '加载详情失败')
  } catch (e) { message.error(e.message || '加载详情失败') }
}

onMounted(() => { loadShops() })
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.toolbar { display: flex; flex-direction: column; gap: 16px; }
.mono { font-family: 'SF Mono', 'Consolas', monospace; font-size: 13px; }
.goods-id-block { display: flex; flex-direction: column; gap: 4px; line-height: 1.4; }
</style>
