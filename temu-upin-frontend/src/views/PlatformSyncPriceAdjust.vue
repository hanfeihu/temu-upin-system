<template>
  <ProLayout title="调价单管理">
    <div class="page">
      <a-card class="toolbar" :bordered="false">
        <ShopTabs v-model="shopId" :shops="shops" :loading="shopsLoading" empty-text="暂无可用店铺，请先配置店铺" @change="onShopChange" />
        <a-form layout="inline">
          <a-form-item label="同步状态">
            <a-select v-model:value="filters.status" style="width: 140px" allow-clear placeholder="全部">
              <a-select-option :value="0">待核价</a-select-option>
              <a-select-option :value="1">待供应商确认</a-select-option>
              <a-select-option :value="2">调价成功</a-select-option>
              <a-select-option :value="3">调价失败</a-select-option>
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
            <a-button :disabled="!rows.length" @click="selectPriceAboveThirty">
              选中新供货价 &gt; 30 元
            </a-button>
            <a-button type="primary" :disabled="!selectedIds.length" :loading="reviewing" @click="batchReview()">
              批量审核通过 ({{ selectedIds.length }})
            </a-button>
            <a-button danger :disabled="!selectedIds.length" :loading="reviewing" @click="openRejectModalBySelection">
              批量拒绝 ({{ selectedIds.length }})
            </a-button>
            <a-button danger :loading="clearing" @click="clearLocalData">
              删除本地数据
            </a-button>
          </a-space>
        </div>

        <a-table rowKey="id" :columns="columns" :dataSource="rows" :loading="loading" :pagination="pagination"
          :row-selection="{ selectedRowKeys: selectedIds, onChange: onSelectChange }"
          :scroll="{ x: 2500 }" tableLayout="fixed" @change="onTableChange">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag>
            </template>
            <template v-else-if="column.key === 'reviewAction'">
              <a-tag :color="reviewColor(record.reviewAction)">{{ reviewText(record.reviewAction) }}</a-tag>
            </template>
            <template v-else-if="column.key === 'priceType'">
              <span>{{ priceTypeText(record.priceType) }}</span>
            </template>
            <template v-else-if="column.key === 'source'">
              <span>{{ sourceText(record.source) }}</span>
            </template>
            <template v-else-if="column.key === 'newSupplyPrice'">
              <span>{{ formatPrice(record.newSupplyPrice) }}</span>
            </template>
            <template v-else-if="column.key === 'priceCurrency'">
              <span>{{ record.priceCurrency || '-' }}</span>
            </template>
            <template v-else-if="column.key === 'skuInfo'">
              <div v-if="extractSkuList(record).length" class="sku-summary-list">
                <div v-for="sku in extractSkuList(record)" :key="sku.id || sku.productSkuId" class="sku-summary-item">
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
                      <div class="sku-summary-spec">{{ sku.specInfo || sku.spec || '-' }}</div>
                      <div class="sku-summary-price">
                        当前供货价：{{ formatPrice(displaySkuSupplyPrice(sku)) }}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <span v-else>-</span>
            </template>
            <template v-else-if="column.key === 'siteNameList'">
              <span :title="siteNameText(record)">{{ siteNameText(record) }}</span>
            </template>
            <template v-else-if="column.key === 'trafficLowExpose'">
              <a-tag :color="record.trafficLowExpose ? 'orange' : 'default'">{{ record.trafficLowExpose ? '是' : '否' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a @click="openDetail(record)">详情</a>
                <a-button type="link" size="small" :disabled="!canApprove(record)" :loading="singleReviewingId === record.id" @click="approveSingle(record)">
                  审核通过
                </a-button>
                <a-button type="link" danger size="small" :disabled="!canApprove(record)" :loading="singleReviewingId === record.id" @click="openRejectModal(record)">
                  审核拒绝
                </a-button>
              </a-space>
            </template>
            <template v-else-if="column.key === 'syncedAt'">
              <span class="mono">{{ formatDT(record.syncedAt) }}</span>
            </template>
          </template>
        </a-table>
      </a-card>

      <!-- 详情弹窗 -->
      <a-modal v-model:open="detailOpen" title="调价单详情" width="960" :bodyStyle="{ maxHeight: '75vh', overflowY: 'auto' }">
        <template v-if="detailData">
          <a-descriptions bordered :column="2" size="small">
            <a-descriptions-item label="订单号">{{ detailData.priceOrderSn }}</a-descriptions-item>
            <a-descriptions-item label="状态">
              <a-tag :color="statusColor(detailData.status)">{{ statusText(detailData.status) }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="商品名称" :span="2">{{ detailData.productName || '-' }}</a-descriptions-item>
            <a-descriptions-item label="SKC ID">{{ detailData.skcId }}</a-descriptions-item>
            <a-descriptions-item label="调价类型">{{ priceTypeText(detailData.priceType) }}</a-descriptions-item>
            <a-descriptions-item label="来源">{{ sourceText(detailData.source) }}</a-descriptions-item>
            <a-descriptions-item label="流量曝光不足">{{ detailData.trafficLowExpose ? '是' : '否' }}</a-descriptions-item>
            <a-descriptions-item label="站点">{{ siteNameText(detailData) }}</a-descriptions-item>
            <a-descriptions-item label="调价原因" :span="2">{{ detailData.adjustReason || '-' }}</a-descriptions-item>
            <a-descriptions-item label="新供货价">{{ formatPrice(detailData.newSupplyPrice) }}</a-descriptions-item>
            <a-descriptions-item label="币种">{{ detailData.priceCurrency || '-' }}</a-descriptions-item>
            <template v-if="detailData.rejectReason">
              <a-descriptions-item label="拒绝原因" :span="2">{{ detailData.rejectReason }}</a-descriptions-item>
            </template>
          </a-descriptions>

          <a-divider>SKU 明细</a-divider>
          <a-table :dataSource="extractSkuList(detailData)" rowKey="id" :pagination="false" bordered size="small" :scroll="{ x: 860 }">
            <a-table-column title="SKU 图" width="90">
              <template #default="{ record }">
                <a-image v-if="record.imageUrl" :src="record.imageUrl" :width="44" :height="44" class="sku-inline-thumb" />
                <span v-else>-</span>
              </template>
            </a-table-column>
            <a-table-column title="SKU ID" dataIndex="productSkuId" width="140" />
            <a-table-column title="外部编码" dataIndex="extCode" width="140" />
            <a-table-column title="规格" width="220">
              <template #default="{ record }">{{ record.specInfo || record.spec || '-' }}</template>
            </a-table-column>
            <a-table-column title="当前供货价" width="140">
              <template #default="{ record }">{{ formatPrice(displaySkuSupplyPrice(record)) }}</template>
            </a-table-column>
          </a-table>
        </template>
        <template #footer>
          <a-space>
            <a-button @click="detailOpen = false">关闭</a-button>
            <a-button danger :disabled="!canApprove(detailData)" :loading="singleReviewingId === detailData?.id" @click="openRejectModal(detailData)">
              审核拒绝
            </a-button>
            <a-button type="primary" :disabled="!canApprove(detailData)" :loading="singleReviewingId === detailData?.id" @click="approveSingle(detailData)">
              审核通过
            </a-button>
          </a-space>
        </template>
      </a-modal>

      <a-modal
        v-model:open="rejectModalOpen"
        title="调价单拒绝"
        :confirmLoading="reviewing"
        @ok="submitReject"
      >
        <p style="margin-bottom: 12px">将对以下 {{ rejectTargets.length }} 条调价单执行拒绝操作：</p>
        <a-list size="small" bordered :data-source="rejectTargets">
          <template #renderItem="{ item }">
            <a-list-item>
              {{ item.priceOrderSn }}
            </a-list-item>
          </template>
        </a-list>
        <a-form layout="vertical" style="margin-top: 16px">
          <a-form-item label="拒绝原因">
            <a-textarea v-model:value="rejectReason" :rows="4" placeholder="可选；如填写，将按文档映射到 rejectReasons，key 为调价单号" />
          </a-form-item>
        </a-form>
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
const clearing = ref(false)
const singleReviewingId = ref(null)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const selectedIds = ref([])
const defaultFilters = Object.freeze({ status: 1, reviewAction: 'PENDING' })
const filters = reactive({ ...defaultFilters })

const detailOpen = ref(false)
const detailData = ref(null)
const rejectModalOpen = ref(false)
const rejectTargets = ref([])
const rejectReason = ref('')

const columns = [
  { title: '订单号', dataIndex: 'priceOrderSn', key: 'priceOrderSn', width: 180 },
  { title: 'SKC ID', dataIndex: 'skcId', key: 'skcId', width: 140 },
  { title: '商品名称', dataIndex: 'productName', key: 'productName', width: 220, ellipsis: true },
  { title: '调价类型', key: 'priceType', width: 110 },
  { title: '来源', key: 'source', width: 110 },
  { title: 'SKU 信息', key: 'skuInfo', width: 420 },
  { title: '新供货价', key: 'newSupplyPrice', width: 140 },
  { title: '币种', key: 'priceCurrency', width: 100 },
  { title: '站点', key: 'siteNameList', width: 200, ellipsis: true },
  { title: '低曝光', key: 'trafficLowExpose', width: 100 },
  { title: '状态', key: 'status', width: 100 },
  { title: '本地审核', key: 'reviewAction', width: 100 },
  { title: '调价原因', dataIndex: 'adjustReason', key: 'adjustReason', width: 180, ellipsis: true },
  { title: '拒绝原因', dataIndex: 'rejectReason', key: 'rejectReason', width: 180, ellipsis: true },
  { title: '同步时间', key: 'syncedAt', width: 180 },
  { title: '操作', key: 'actions', width: 180, fixed: 'right' }
]

const pagination = computed(() => ({
  current: page.value, pageSize: pageSize.value, total: total.value,
  showSizeChanger: true, showTotal: (t) => `共 ${t} 条`, pageSizeOptions: ['10', '20', '50', '100']
}))

const statusText = (s) => ({ 0: '待核价', 1: '待供应商确认', 2: '调价成功', 3: '调价失败' }[s] || '未知')
const statusColor = (s) => ({ 0: 'processing', 1: 'gold', 2: 'success', 3: 'error' }[s] || 'default')
const reviewColor = (a) => ({ APPROVED: 'success', APPROVE: 'success', REJECTED: 'error', REJECT: 'error', PENDING: 'warning' }[a || 'PENDING'] || 'warning')
const reviewText = (a) => ({ APPROVED: '已同意', APPROVE: '已同意', REJECTED: '已拒绝', REJECT: '已拒绝', PENDING: '待处理' }[a || 'PENDING'] || (a || '待处理'))
const priceTypeText = (v) => ({ 0: '日常价', 1: '活动价' }[Number(v)] || (v ?? '-'))
const sourceText = (v) => ({ 1: '运营', 2: '供应商', '1': '运营', '2': '供应商' }[v] || v || '-')
const extractSkuList = (record) => {
  if (Array.isArray(record?.skuInfoList) && record.skuInfoList.length) return record.skuInfoList
  if (Array.isArray(record?.skuList) && record.skuList.length) return record.skuList
  return []
}
const extractSiteNameList = (record) => {
  if (Array.isArray(record?.siteNameList) && record.siteNameList.length) return record.siteNameList
  if (typeof record?.siteNamesJson === 'string' && record.siteNamesJson) {
    try {
      const parsed = JSON.parse(record.siteNamesJson)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }
  return []
}
const siteNameText = (record) => {
  const siteNames = extractSiteNameList(record)
  return siteNames.length ? siteNames.join(' / ') : '-'
}
const displaySkuSupplyPrice = (sku) => {
  if (sku?.price !== undefined && sku?.price !== null && sku?.price !== '') return sku.price
  return sku?.currentSupplyPrice
}
const formatPrice = (value) => {
  if (value === undefined || value === null || value === '') return '-'
  const numericValue = Number(value)
  if (Number.isNaN(numericValue)) return value
  return (numericValue / 100).toFixed(2)
}

const pad2 = (n) => String(n ?? '').padStart(2, '0')
const formatDT = (v) => {
  if (!v) return '-'
  if (Array.isArray(v) && v.length >= 6) { const [y, mo, d, h, m, s] = v; return `${y}-${pad2(mo)}-${pad2(d)} ${pad2(h)}:${pad2(m)}:${pad2(s)}` }
  return typeof v === 'string' ? v : '-'
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
const canApprove = (record) => !!record && record.status === 1 && !['APPROVE', 'APPROVED', 'REJECT', 'REJECTED'].includes(record.reviewAction)
const isPriceAbove = (value, amountYuan) => {
  if (value === undefined || value === null || value === '') return false
  const numericValue = Number(value)
  if (Number.isNaN(numericValue)) return false
  return numericValue > amountYuan * 100
}

const selectPriceAboveThirty = () => {
  const matchedIds = rows.value
    .filter((row) => canApprove(row) && isPriceAbove(row.newSupplyPrice, 30))
    .map((row) => row.id)

  selectedIds.value = matchedIds
  if (!matchedIds.length) {
    message.warning('当前页没有新供货价大于 30 元且可审核的调价单')
    return
  }
  message.success(`已选中 ${matchedIds.length} 条新供货价大于 30 元的调价单`)
}

const fetchList = async () => {
  if (!shopId.value) return
  loading.value = true
  try {
    const res = await syncApi.getPriceAdjustList({
      shopId: shopId.value, status: filters.status, reviewAction: filters.reviewAction,
      page: page.value, pageSize: pageSize.value
    })
    if (res?.success) { rows.value = res.data?.content || []; total.value = res.data?.totalElements || 0 }
    else message.error(res?.message || '加载失败')
  } catch (e) { message.error(e.message || '加载失败') }
  finally { loading.value = false }
}

const reload = () => { page.value = 1; selectedIds.value = []; fetchList() }
const reset = () => { filters.status = defaultFilters.status; filters.reviewAction = defaultFilters.reviewAction; reload() }
const onTableChange = (p) => { page.value = p.current; pageSize.value = p.pageSize; fetchList() }

const openDetail = async (record) => {
  try {
    const res = await syncApi.getPriceAdjustDetail(record.id)
    if (res?.success) { detailData.value = res.data; detailOpen.value = true }
    else message.error(res?.message || '加载详情失败')
  } catch (e) { message.error(e.message || '加载详情失败') }
}

const executeReview = async ({ orderIds, action, successMessage, rejectReasonText }) => {
  const payload = { shopId: shopId.value, orderIds, action }
  if (action === 'REJECT' && rejectReasonText && rejectReasonText.trim()) {
    payload.rejectReason = rejectReasonText.trim()
  }
  const res = await syncApi.batchReviewAdjust(payload)
  if (res?.success) {
    message.success(res.message || successMessage)
    selectedIds.value = []
    await fetchList()
    if (detailData.value?.id && orderIds.includes(detailData.value.id)) {
      const detailRes = await syncApi.getPriceAdjustDetail(detailData.value.id)
      if (detailRes?.success) detailData.value = detailRes.data
    }
  } else {
    message.error(res?.message || '审核失败')
  }
}

const resetRejectState = () => {
  rejectTargets.value = []
  rejectReason.value = ''
}

const openRejectModal = (record) => {
  if (!canApprove(record)) return
  rejectTargets.value = [record]
  rejectReason.value = record?.rejectReason || ''
  rejectModalOpen.value = true
}

const openRejectModalBySelection = () => {
  const validRows = rows.value.filter((row) => selectedIds.value.includes(row.id) && canApprove(row))
  if (!validRows.length) {
    message.warning('请选择待供应商确认且未审核的调价单')
    return
  }
  rejectTargets.value = validRows
  rejectReason.value = ''
  rejectModalOpen.value = true
}

const submitReject = async () => {
  if (!rejectTargets.value.length) {
    message.warning('请选择待供应商确认且未审核的调价单')
    return
  }
  reviewing.value = true
  singleReviewingId.value = rejectTargets.value.length === 1 ? rejectTargets.value[0].id : null
  try {
    await executeReview({
      orderIds: rejectTargets.value.map((item) => item.id),
      action: 'REJECT',
      successMessage: '审核拒绝完成',
      rejectReasonText: rejectReason.value
    })
    rejectModalOpen.value = false
    resetRejectState()
  } catch (e) {
    message.error(e.message || '审核失败')
  } finally {
    reviewing.value = false
    singleReviewingId.value = null
  }
}

const clearLocalData = () => {
  if (!shopId.value) {
    message.warning('请先选择店铺')
    return
  }
  Modal.confirm({
    title: '确认删除本地数据',
    content: '将清空当前店铺已同步的调价单本地数据和 SKU 明细，仅影响本系统，不会删除 TEMU 平台数据。',
    okButtonProps: { danger: true },
    onOk: async () => {
      clearing.value = true
      try {
        const res = await syncApi.clearPriceAdjustLocalData(shopId.value)
        if (res?.success) {
          message.success(res.message || '本地数据已清空')
          rows.value = []
          total.value = 0
          selectedIds.value = []
          detailOpen.value = false
          detailData.value = null
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

const approveSingle = (record) => {
  if (!canApprove(record)) return
  Modal.confirm({
    title: '确认审核通过',
    content: `确认通过调价单 ${record.priceOrderSn} 吗？`,
    onOk: async () => {
      singleReviewingId.value = record.id
      try {
        await executeReview({ orderIds: [record.id], action: 'APPROVE', successMessage: '审核通过' })
      } catch (e) {
        message.error(e.message || '审核失败')
      } finally {
        singleReviewingId.value = null
      }
    }
  })
}

const batchReview = () => {
  const validRows = rows.value.filter((row) => selectedIds.value.includes(row.id) && canApprove(row))
  if (!validRows.length) {
    message.warning('请选择待供应商确认且未审核的调价单')
    return
  }
  Modal.confirm({
    title: '确认批量审核通过',
    content: `将对选中的 ${validRows.length} 条调价单执行审核通过`,
    onOk: async () => {
      reviewing.value = true
      try {
        await executeReview({ orderIds: validRows.map((item) => item.id), action: 'APPROVE', successMessage: '批量审核通过完成' })
      } catch (e) { message.error(e.message || '审核失败') }
      finally { reviewing.value = false }
    }
  })
}

onMounted(() => { loadShops() })
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.toolbar { display: flex; flex-direction: column; gap: 16px; }
.mono { font-family: 'SF Mono', 'Consolas', monospace; font-size: 13px; }
.sku-summary-list { display: flex; flex-direction: column; gap: 8px; }
.sku-summary-item { padding: 8px 10px; border-radius: 8px; background: #fafafa; border: 1px solid #f0f0f0; }
.sku-summary-row { display: flex; gap: 12px; align-items: flex-start; }
.sku-summary-body { min-width: 0; flex: 1; }
.sku-summary-head { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 4px; font-weight: 500; }
.sku-summary-spec { color: #595959; line-height: 1.5; }
.sku-summary-price { margin-top: 4px; color: #262626; }
.sku-summary-thumb, .sku-inline-thumb { object-fit: cover; border-radius: 6px; overflow: hidden; flex: none; }
</style>
