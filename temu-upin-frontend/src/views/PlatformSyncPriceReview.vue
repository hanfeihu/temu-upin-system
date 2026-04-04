<template>
  <ProLayout title="核价单管理">
    <div class="page">
      <a-card class="toolbar" :bordered="false">
        <ShopTabs v-model="shopId" :shops="shops" :loading="shopsLoading" empty-text="暂无可用店铺，请先配置店铺" @change="onShopChange" />
        <a-form layout="inline">
          <a-form-item label="订单状态">
            <a-select v-model:value="filters.orderStatus" style="width: 140px" allow-clear placeholder="全部">
              <a-select-option v-for="item in orderStatusOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="本地审核">
            <a-select v-model:value="filters.reviewAction" style="width: 140px" allow-clear placeholder="全部">
              <a-select-option value="PENDING">待处理</a-select-option>
              <a-select-option value="APPROVE">已同意</a-select-option>
              <a-select-option value="REJECT">已拒绝</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item>
            <a-space>
              <a-button type="primary" :loading="loading" @click="reload">查询</a-button>
              <a-button @click="reset">重置</a-button>
            </a-space>
          </a-form-item>
        </a-form>
      </a-card>

      <a-card v-if="shopId" class="table-card" :bordered="false">
        <div style="margin-bottom: 12px">
          <a-space>
            <a-button @click="selectSuggestPriceAbove30">自动勾选建议价大于30元</a-button>
            <a-button type="primary" :disabled="!selectedIds.length" :loading="reviewing" @click="startBatchReview('APPROVE')">
              批量同意 ({{ selectedIds.length }})
            </a-button>
            <a-button danger :disabled="!selectedIds.length" :loading="reviewing" @click="startBatchReview('REJECT')">
              批量拒绝 ({{ selectedIds.length }})
            </a-button>
          </a-space>
        </div>

        <a-table rowKey="id" :columns="columns" :dataSource="rows" :loading="loading" :pagination="pagination"
          :row-selection="{ selectedRowKeys: selectedIds, onChange: onSelectChange }"
          :scroll="{ x: 1720 }" tableLayout="fixed" @change="onTableChange">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'orderStatus'">
              <a-tag :color="orderStatusColor(record.orderStatus)">{{ orderStatusText(record.orderStatus) }}</a-tag>
            </template>
            <template v-else-if="column.key === 'reviewAction'">
              <a-tag :color="reviewColor(record.reviewAction)">{{ reviewText(record.reviewAction) }}</a-tag>
            </template>
            <template v-else-if="column.key === 'supplyPrice'">
              {{ formatPrice(record.supplyPrice) }}
            </template>
            <template v-else-if="column.key === 'suggestSupplyPrice'">
              {{ formatPrice(record.suggestSupplyPrice) }}
            </template>
            <template v-else-if="column.key === 'siteNames'">
              <span>{{ formatJsonList(record.siteNamesJson) }}</span>
            </template>
            <template v-else-if="column.key === 'skuInfo'">
              <div v-if="record.skuList?.length" class="sku-summary-list">
                <div v-for="sku in record.skuList" :key="sku.id || sku.productSkuId" class="sku-summary-item">
                  <div class="sku-summary-row">
                    <a-image
                      v-if="sku.imageUrl"
                      :src="sku.imageUrl"
                      :width="48"
                      :height="48"
                      class="sku-summary-thumb"
                    />
                    <div class="sku-summary-body">
                      <div class="sku-summary-head">
                        <span class="mono">SKU {{ sku.productSkuId || '-' }}</span>
                        <span v-if="sku.extCode">外部编码：{{ sku.extCode }}</span>
                      </div>
                      <div class="sku-summary-spec">{{ sku.specInfo || '-' }}</div>
                      <div class="sku-summary-price">
                        当前供货价：{{ formatPrice(sku.currentSupplyPrice) }}
                        <span v-if="sku.newPrice != null"> / 新申报价：{{ formatPrice(sku.newPrice) }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <span v-else>-</span>
            </template>
            <template v-else-if="column.key === 'canBargain'">
              {{ record.canBargain ? '是' : '否' }}
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a @click="openDetail(record)">详情</a>
                <a @click="startSingleReview(record, 'APPROVE')">同意</a>
                <a @click="startSingleReview(record, 'REJECT')">拒绝</a>
              </a-space>
            </template>
            <template v-else-if="column.key === 'syncedAt'">
              <span class="mono">{{ formatDT(record.syncedAt) }}</span>
            </template>
          </template>
        </a-table>
      </a-card>

      <!-- 详情弹窗 -->
      <a-modal v-model:open="detailOpen" title="核价单详情" :footer="null" width="960" :bodyStyle="{ maxHeight: '75vh', overflowY: 'auto' }">
        <template v-if="detailData">
          <a-descriptions bordered :column="2" size="small">
            <a-descriptions-item label="订单ID">{{ detailData.orderId }}</a-descriptions-item>
            <a-descriptions-item label="订单状态">
              <a-tag :color="orderStatusColor(detailData.orderStatus)">{{ orderStatusText(detailData.orderStatus) }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="供货价(分)">{{ detailData.supplyPrice ?? '-' }}</a-descriptions-item>
            <a-descriptions-item label="建议价(分)">{{ detailData.suggestSupplyPrice ?? '-' }}</a-descriptions-item>
            <a-descriptions-item label="币种">{{ detailData.priceCurrency || '-' }}</a-descriptions-item>
            <a-descriptions-item label="建议价币种">{{ detailData.suggestPriceCurrency || '-' }}</a-descriptions-item>
            <a-descriptions-item label="可议价">{{ detailData.canBargain ? '是' : '否' }}</a-descriptions-item>
            <a-descriptions-item label="站点" :span="2">{{ formatJsonList(detailData.siteNamesJson) || '-' }}</a-descriptions-item>
            <a-descriptions-item label="本地审核" :span="2">
              <a-tag :color="reviewColor(detailData.reviewAction)">{{ reviewText(detailData.reviewAction) }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="同步时间" :span="2"><span class="mono">{{ formatDT(detailData.syncedAt) }}</span></a-descriptions-item>
          </a-descriptions>

          <a-divider>SKU 明细</a-divider>
          <a-table :dataSource="detailData.skuList || []" rowKey="id" :pagination="false" bordered size="small" :scroll="{ x: 700 }">
            <a-table-column title="SKU 图" width="90">
              <template #default="{ record }">
                <a-image v-if="record.imageUrl" :src="record.imageUrl" :width="44" :height="44" class="sku-inline-thumb" />
                <span v-else>-</span>
              </template>
            </a-table-column>
            <a-table-column title="SKU ID" dataIndex="productSkuId" width="140" />
            <a-table-column title="外部编码" dataIndex="extCode" width="140" />
            <a-table-column title="规格信息" dataIndex="specInfo" />
            <a-table-column title="当前供货价(分)" dataIndex="currentSupplyPrice" width="140" />
            <a-table-column title="新价格(分)" dataIndex="newPrice" width="120" />
          </a-table>
        </template>
      </a-modal>

      <a-modal
        v-model:open="reviewModalOpen"
        :title="reviewAction === 'APPROVE' ? '核价单同意' : '核价单拒绝'"
        :confirmLoading="reviewing"
        width="1080"
        @ok="submitReview"
      >
        <template v-if="reviewAction === 'APPROVE'">
          <p>将对以下 {{ reviewTargets.length }} 条核价单执行同意操作：</p>
          <a-table :dataSource="reviewTargets" :pagination="false" rowKey="id" size="small">
            <a-table-column title="订单ID" dataIndex="orderId" width="140" />
            <a-table-column title="状态" width="140">
              <template #default="{ record }">
                <a-tag :color="orderStatusColor(record.orderStatus)">{{ orderStatusText(record.orderStatus) }}</a-tag>
              </template>
            </a-table-column>
            <a-table-column title="建议价" width="140">
              <template #default="{ record }">{{ formatPrice(record.suggestSupplyPrice) }}</template>
            </a-table-column>
            <a-table-column title="站点">
              <template #default="{ record }">{{ formatJsonList(record.siteNamesJson) || '-' }}</template>
            </a-table-column>
          </a-table>
        </template>
        <template v-else>
          <a-alert type="info" show-icon style="margin-bottom: 16px" message="可填写统一拒绝原因、外部链接，并为每个 SKU 录入新的申报价。" />

          <div class="review-block">
            <div class="review-block-title">拒绝原因</div>
            <a-space direction="vertical" style="width: 100%" :size="12">
              <div v-for="(item, index) in reviewForm.reasonComponents" :key="index" class="reason-row">
                <a-select v-model:value="item.type" style="width: 180px" placeholder="原因类型">
                  <a-select-option v-for="option in rejectReasonTypeOptions" :key="option.value" :value="option.value">{{ option.label }}</a-select-option>
                </a-select>
                <a-input v-model:value="item.reason" placeholder="具体原因" />
                <a-button danger @click="removeReason(index)">删除</a-button>
              </div>
            </a-space>
            <a-button style="margin-top: 12px" @click="addReason">新增原因</a-button>
          </div>

          <div class="review-block">
            <div class="review-block-title">外部链接</div>
            <a-textarea v-model:value="reviewForm.externalLinksText" :rows="3" placeholder="每行一个链接，可选" />
          </div>

          <div class="review-block">
            <div class="review-block-title">改单价格</div>
            <div v-for="detail in reviewDetails" :key="detail.id" class="order-block">
              <div class="order-block-title">订单 {{ detail.orderId }}</div>
              <a-table :dataSource="detail.skuList || []" :pagination="false" rowKey="id" size="small" bordered>
                <a-table-column title="SKU 图" width="90">
                  <template #default="{ record }">
                    <a-image v-if="record.imageUrl" :src="record.imageUrl" :width="44" :height="44" class="sku-inline-thumb" />
                    <span v-else>-</span>
                  </template>
                </a-table-column>
                <a-table-column title="SKU ID" dataIndex="productSkuId" width="140" />
                <a-table-column title="外部编码" dataIndex="extCode" width="140" />
                <a-table-column title="规格" dataIndex="specInfo" />
                <a-table-column title="当前供货价" width="120">
                  <template #default="{ record }">{{ formatPrice(record.currentSupplyPrice) }}</template>
                </a-table-column>
                <a-table-column title="新申报价(分)" width="180">
                  <template #default="{ record }">
                    <a-input-number v-model:value="record.editNewPrice" :min="0" :precision="0" style="width: 140px" />
                  </template>
                </a-table-column>
              </a-table>
            </div>
          </div>
        </template>
      </a-modal>
    </div>
  </ProLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import ShopTabs from '@/platform/components/ShopTabs.vue'
import { syncApi } from '@/platform/api/sync'
import { temuShopsApi } from '@/platform/api/temuShops'

const shopsLoading = ref(false)
const shops = ref([])
const shopId = ref(null)
const loading = ref(false)
const reviewing = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const selectedIds = ref([])
const filters = reactive({ orderStatus: undefined, reviewAction: undefined })

const detailOpen = ref(false)
const detailData = ref(null)
const reviewModalOpen = ref(false)
const reviewAction = ref('APPROVE')
const reviewTargets = ref([])
const reviewDetails = ref([])
const reviewForm = reactive({
  reasonComponents: [{ type: undefined, reason: '' }],
  externalLinksText: ''
})

const orderStatusOptions = [
  { value: 0, label: '待核价' },
  { value: 1, label: '待供应商确认' },
  { value: 2, label: '核价通过' },
  { value: 3, label: '核价驳回' },
  { value: 4, label: '废弃' },
  { value: 5, label: '价格同步中' }
]

const rejectReasonTypeOptions = [
  { value: 0, label: '材质' },
  { value: 1, label: '功能' },
  { value: 2, label: '其他' },
  { value: 3, label: '品类' },
  { value: 4, label: '外观' },
  { value: 5, label: '版型' },
  { value: 6, label: '图案' },
  { value: 7, label: '规格尺寸' },
  { value: 8, label: '品牌' }
]

const columns = [
  { title: '订单ID', dataIndex: 'orderId', key: 'orderId', width: 140 },
  { title: '状态', key: 'orderStatus', width: 100 },
  { title: '供货价', key: 'supplyPrice', width: 110 },
  { title: '建议价', key: 'suggestSupplyPrice', width: 110 },
  { title: 'SKU 信息', key: 'skuInfo', width: 420 },
  { title: '币种', dataIndex: 'priceCurrency', key: 'priceCurrency', width: 80 },
  { title: '站点', key: 'siteNames', width: 220, ellipsis: true },
  { title: '可议价', key: 'canBargain', width: 90 },
  { title: '本地审核', key: 'reviewAction', width: 100 },
  { title: '同步时间', key: 'syncedAt', width: 180 },
  { title: '操作', key: 'actions', width: 190, fixed: 'right' }
]

const pagination = computed(() => ({
  current: page.value, pageSize: pageSize.value, total: total.value,
  showSizeChanger: true, showTotal: (t) => `共 ${t} 条`, pageSizeOptions: ['10', '20', '50', '100']
}))

const orderStatusText = (s) => ({ 0: '待核价', 1: '待供应商确认', 2: '核价通过', 3: '核价驳回', 4: '废弃', 5: '价格同步中' }[s] || '未知')
const orderStatusColor = (s) => ({ 0: 'processing', 1: 'gold', 2: 'success', 3: 'error', 4: 'default', 5: 'cyan' }[s] || 'default')
const reviewColor = (a) => ({ APPROVE: 'success', APPROVED: 'success', REJECT: 'error', REJECTED: 'error', PENDING: 'warning' }[a] || 'warning')
const reviewText = (a) => ({ APPROVE: '已同意', APPROVED: '已同意', REJECT: '已拒绝', REJECTED: '已拒绝', PENDING: '待处理' }[a || 'PENDING'] || '待处理')
const formatPrice = (v) => v != null ? `¥${(v / 100).toFixed(2)}` : '-'

const pad2 = (n) => String(n ?? '').padStart(2, '0')
const formatDT = (v) => {
  if (!v) return '-'
  if (Array.isArray(v) && v.length >= 6) { const [y, mo, d, h, m, s] = v; return `${y}-${pad2(mo)}-${pad2(d)} ${pad2(h)}:${pad2(m)}:${pad2(s)}` }
  return typeof v === 'string' ? v : '-'
}
const parseJsonList = (v) => {
  if (!v) return []
  if (Array.isArray(v)) return v
  if (typeof v !== 'string') return []
  try {
    const parsed = JSON.parse(v)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}
const formatJsonList = (v) => parseJsonList(v).join(' / ')
const resetReviewForm = () => {
  reviewForm.reasonComponents = [{ type: undefined, reason: '' }]
  reviewForm.externalLinksText = ''
}
const addReason = () => { reviewForm.reasonComponents.push({ type: undefined, reason: '' }) }
const removeReason = (index) => {
  if (reviewForm.reasonComponents.length === 1) {
    reviewForm.reasonComponents[0] = { type: undefined, reason: '' }
    return
  }
  reviewForm.reasonComponents.splice(index, 1)
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
      selectedIds.value = []
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
const onSelectChange = (keys) => { selectedIds.value = keys }

const selectSuggestPriceAbove30 = () => {
  if (!rows.value.length) {
    message.warning('当前没有可勾选的核价单')
    return
  }

  const matchedIds = rows.value
    .filter((row) => Number(row.suggestSupplyPrice) > 3000)
    .map((row) => row.id)

  selectedIds.value = matchedIds

  if (!matchedIds.length) {
    message.info('当前页没有建议价大于30元的核价单')
    return
  }

  message.success(`已自动勾选 ${matchedIds.length} 条建议价大于30元的核价单`)
}

const fetchList = async () => {
  if (!shopId.value) return
  loading.value = true
  try {
    const res = await syncApi.getPriceReviewList({
      shopId: shopId.value, orderStatus: filters.orderStatus, reviewAction: filters.reviewAction,
      page: page.value, pageSize: pageSize.value
    })
    if (res?.success) { rows.value = res.data?.content || []; total.value = res.data?.totalElements || 0 }
    else message.error(res?.message || '加载失败')
  } catch (e) { message.error(e.message || '加载失败') }
  finally { loading.value = false }
}

const reload = () => { page.value = 1; selectedIds.value = []; fetchList() }
const reset = () => { filters.orderStatus = undefined; filters.reviewAction = undefined; reload() }
const onTableChange = (p) => { page.value = p.current; pageSize.value = p.pageSize; fetchList() }

const openDetail = async (record) => {
  try {
    const res = await syncApi.getPriceReviewDetail(record.id)
    if (res?.success) { detailData.value = res.data; detailOpen.value = true }
    else message.error(res?.message || '加载详情失败')
  } catch (e) { message.error(e.message || '加载详情失败') }
}

const loadReviewDetails = async (records) => {
  const responses = await Promise.all(records.map((record) => syncApi.getPriceReviewDetail(record.id)))
  const details = responses.filter((res) => res?.success).map((res) => res.data || {})
  details.forEach((detail) => {
    ;(detail.skuList || []).forEach((sku) => {
      sku.editNewPrice = sku.newPrice ?? sku.currentSupplyPrice ?? undefined
    })
  })
  return details
}

const startSingleReview = async (record, action) => {
  reviewAction.value = action
  reviewTargets.value = [record]
  resetReviewForm()
  if (action === 'REJECT') {
    try {
      reviewDetails.value = await loadReviewDetails([record])
      reviewModalOpen.value = true
    } catch (e) {
      message.error(e.message || '加载核价详情失败')
    }
    return
  }
  reviewDetails.value = []
  reviewModalOpen.value = true
}

const startBatchReview = async (action) => {
  const records = rows.value.filter((row) => selectedIds.value.includes(row.id))
  if (!records.length) {
    message.warning('请先选择核价单')
    return
  }
  reviewAction.value = action
  reviewTargets.value = records
  resetReviewForm()
  if (action === 'REJECT') {
    try {
      reviewDetails.value = await loadReviewDetails(records)
      reviewModalOpen.value = true
    } catch (e) {
      message.error(e.message || '加载核价详情失败')
    }
    return
  }
  reviewDetails.value = []
  reviewModalOpen.value = true
}

const buildReviewPayload = () => {
  const payload = {
    shopId: shopId.value,
    orderIds: reviewTargets.value.map((item) => item.id),
    action: reviewAction.value
  }

  if (reviewAction.value === 'REJECT') {
    const componentList = reviewForm.reasonComponents
      .filter((item) => item.reason && item.reason.trim() && item.type !== undefined)
      .map((item) => ({ reason: item.reason.trim(), type: item.type }))
    const externalLinkList = reviewForm.externalLinksText
      .split('\n')
      .map((item) => item.trim())
      .filter(Boolean)
    const rejectPrices = []
    reviewDetails.value.forEach((detail) => {
      ;(detail.skuList || []).forEach((sku) => {
        if (sku.editNewPrice !== undefined && sku.editNewPrice !== null && `${sku.editNewPrice}` !== '') {
          rejectPrices.push({
            orderId: detail.id,
            productSkuId: sku.productSkuId,
            newPrice: Number(sku.editNewPrice)
          })
        }
      })
    })

    if (!componentList.length && !rejectPrices.length && !externalLinkList.length) {
      throw new Error('拒绝时请至少填写一个原因、一个外部链接或一个新的申报价')
    }

    if (componentList.length || externalLinkList.length) {
      payload.bargainReasonList = [{ componentList, externalLinkList }]
    }
    payload.rejectPrices = rejectPrices
  }

  return payload
}

const submitReview = async () => {
  const label = reviewAction.value === 'APPROVE' ? '同意' : '拒绝'
  reviewing.value = true
  try {
    const res = await syncApi.batchReviewPrice(buildReviewPayload())
    if (res?.success) {
      message.success(res.message || `${label}完成`)
      reviewModalOpen.value = false
      selectedIds.value = []
      reviewTargets.value = []
      reviewDetails.value = []
      resetReviewForm()
      fetchList()
    } else {
      message.error(res?.message || `${label}失败`)
    }
  } catch (e) {
    message.error(e.message || `${label}失败`)
  } finally {
    reviewing.value = false
  }
}

onMounted(() => { loadShops() })
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.toolbar { display: flex; flex-direction: column; gap: 16px; }
.mono { font-family: 'SF Mono', 'Consolas', monospace; font-size: 13px; }
.review-block { margin-bottom: 16px; }
.review-block-title { margin-bottom: 8px; font-weight: 600; }
.reason-row { display: grid; grid-template-columns: 180px 1fr 72px; gap: 12px; }
.order-block { margin-top: 12px; }
.order-block-title { margin-bottom: 8px; font-weight: 600; }
.sku-summary-list { display: flex; flex-direction: column; gap: 8px; }
.sku-summary-item { padding: 8px 10px; border-radius: 8px; background: #fafafa; border: 1px solid #f0f0f0; }
.sku-summary-row { display: flex; gap: 12px; align-items: flex-start; }
.sku-summary-body { min-width: 0; flex: 1; }
.sku-summary-head { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 4px; font-weight: 500; }
.sku-summary-spec { color: #595959; line-height: 1.5; }
.sku-summary-price { margin-top: 4px; color: #262626; }
.sku-summary-thumb, .sku-inline-thumb { object-fit: cover; border-radius: 6px; overflow: hidden; flex: none; }
</style>
