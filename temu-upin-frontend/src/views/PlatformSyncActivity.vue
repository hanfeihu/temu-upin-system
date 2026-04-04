<template>
  <ProLayout title="活动报名">
    <div class="page">
      <a-card class="toolbar" :bordered="false">
        <ShopTabs v-model="shopId" :shops="shops" :loading="shopsLoading" empty-text="暂无可用店铺，请先配置店铺" @change="onShopChange" />
        <a-form layout="inline">
          <a-form-item label="活动类型">
            <a-select v-model:value="filters.activityType" style="width: 180px" allow-clear placeholder="全部">
              <a-select-option v-for="item in ACTIVITY_TYPE_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="报名状态">
            <a-select v-model:value="filters.enrollStatus" style="width: 180px" allow-clear placeholder="全部">
              <a-select-option v-for="item in ENROLL_STATUS_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item>
            <a-space>
              <a-button type="primary" :loading="loading || activitiesLoading" @click="reloadAll">查询</a-button>
              <a-button @click="reset">重置</a-button>
            </a-space>
          </a-form-item>
        </a-form>
      </a-card>

      <a-card v-if="shopId" :bordered="false">
        <a-tabs v-model:activeKey="activeTab">
          <a-tab-pane key="workbench" tab="批量报名">
            <div class="workbench-grid">
              <a-card title="1. 选择活动" size="small">
                <a-form layout="vertical">
                  <a-form-item label="活动">
                    <a-select v-model:value="workbench.selectedActivityId" placeholder="请选择活动" :options="activityOptions" @change="onSelectedActivityChange" />
                  </a-form-item>
                  <a-form-item v-if="selectedActivity?.thematicList?.length" label="主题">
                    <a-select v-model:value="workbench.selectedThematicId" allow-clear placeholder="请选择主题" :options="thematicOptions" />
                  </a-form-item>
                  <a-space>
                    <a-button type="primary" :disabled="!selectedActivity" :loading="workbench.detailLoading" @click="loadSelectedActivityDetail">读取活动规则</a-button>
                    <a-button v-if="selectedActivity" @click="jumpFromActivitySelection">继续选商品</a-button>
                  </a-space>
                </a-form>

                <div v-if="workbench.detail" class="activity-detail">
                  <div class="detail-head">
                    <div>
                      <div class="detail-title">{{ workbench.detail.activityInfo?.activityName || selectedActivity?.activityName }}</div>
                      <div class="detail-sub">{{ activityTypeText(selectedActivity?.activityType) }}<span v-if="selectedThematic"> / {{ selectedThematic.activityThematicName }}</span></div>
                    </div>
                    <a-tag :color="workbench.detail.canEnroll ? 'success' : 'error'">{{ workbench.detail.canEnroll ? '当前可报名' : '当前不可报名' }}</a-tag>
                  </div>
                  <div v-if="workbench.detail.activityInfo?.activityContent" class="detail-copy">{{ workbench.detail.activityInfo.activityContent }}</div>
                  <div class="detail-section" v-if="workbench.detail.requirements?.length">
                    <div class="section-title">商品要求</div>
                    <div class="tag-list">
                      <a-tag v-for="item in workbench.detail.requirements" :key="`req-${item.requirementCode}-${item.requirementType}`" :color="requirementColor(item.checkStatus)">
                        {{ item.requirementDesc || item.requirementType || item.requirementCode }}
                      </a-tag>
                    </div>
                  </div>
                  <div class="detail-section" v-if="workbench.detail.mallAptitude?.length">
                    <div class="section-title">店铺资质</div>
                    <div class="tag-list">
                      <a-tag v-for="item in workbench.detail.mallAptitude" :key="`apt-${item.requirementCode}-${item.requirementType}`" :color="requirementColor(item.checkStatus)">
                        {{ item.requirementDesc || item.requirementType || item.requirementCode }}
                      </a-tag>
                    </div>
                  </div>
                </div>
              </a-card>

              <a-card title="2. 选择本地商品" size="small">
                <div class="goods-toolbar">
                  <a-input v-model:value="workbench.goodsKeyword" allow-clear placeholder="按商品名称搜索" @pressEnter="reloadGoods" />
                  <a-button type="primary" :loading="workbench.goodsLoading" :disabled="!selectedActivity" @click="reloadGoods">查询商品</a-button>
                  <a-button :disabled="!selectedActivity || !selectedGoodsRowKeys.length" :loading="workbench.matching" @click="matchSelectedGoods">校验可报商品（{{ selectedGoodsRowKeys.length }}）</a-button>
                </div>
                <div class="goods-tip">当前仅展示已加站商品，避免选到不可报名的本地商品。</div>
                <a-table
                  rowKey="productId"
                  size="small"
                  :columns="goodsColumns"
                  :dataSource="workbench.goodsRows"
                  :loading="workbench.goodsLoading"
                  :pagination="goodsPagination"
                  :row-selection="goodsRowSelection"
                  :scroll="{ x: 980 }"
                  @change="onGoodsTableChange"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.key === 'mainImageUrl'">
                      <img v-if="safeImageUrl(record.mainImageUrl)" :src="safeImageUrl(record.mainImageUrl)" class="thumb" />
                      <span v-else>-</span>
                    </template>
                    <template v-else-if="column.key === 'productName'">
                      <div class="title-cell">
                        <div>{{ record.productName || '-' }}</div>
                        <div class="sub-line">商品ID：{{ record.productId || '-' }} / SKC：{{ record.productSkcId || '-' }}</div>
                      </div>
                    </template>
                    <template v-else-if="column.key === 'skcSiteStatus'">
                      <a-tag :color="record.skcSiteStatus === 1 ? 'success' : 'default'">{{ record.skcSiteStatus === 1 ? '已加站' : '未加站' }}</a-tag>
                    </template>
                  </template>
                </a-table>
              </a-card>
            </div>

            <a-card title="3. 填写活动价并提交" size="small">
              <template v-if="!workbench.matchedProducts.length">
                <a-empty description="先选择活动，再从本地商品中勾选商品并校验可报资格" />
              </template>
              <template v-else>
                <div class="matched-toolbar">
                  <div class="matched-meta">
                    已匹配 {{ workbench.matchedProducts.length }} 个可报名商品
                    <span v-if="workbench.sessionsLoaded">，场次已加载</span>
                  </div>
                  <a-space>
                    <a-button :loading="workbench.sessionLoading" @click="loadSessionsForMatched">刷新场次</a-button>
                    <a-button type="primary" :loading="workbench.submitting" @click="submitBatchEnroll">提交报名</a-button>
                  </a-space>
                </div>

                <div class="match-card-list">
                  <a-card v-for="product in workbench.matchedProducts" :key="product.productId" class="match-card" size="small">
                    <div class="match-head">
                      <div class="match-cover-wrap">
                        <img v-if="safeImageUrl(product.mainImageUrl)" :src="safeImageUrl(product.mainImageUrl)" class="match-cover" />
                        <div v-else class="match-cover placeholder">无图</div>
                      </div>
                      <div class="match-head-main">
                        <div class="match-title">{{ product.productName }}</div>
                        <div class="sub-line">商品ID：{{ product.productId }} <span v-if="product.extCode">/ 货号：{{ product.extCode }}</span></div>
                        <div class="match-tags">
                          <a-tag>{{ product.isApparel === 1 ? '服饰类' : '非服饰类' }}</a-tag>
                          <a-tag v-if="product.currency">{{ product.currency }}</a-tag>
                          <a-tag color="blue">建议库存 {{ product.suggestActivityStock ?? '-' }}</a-tag>
                          <a-tag color="gold">最低库存 {{ product.targetActivityStock ?? '-' }}</a-tag>
                        </div>
                      </div>
                      <div class="match-head-side">
                        <div class="field-label">活动库存</div>
                        <a-input-number v-model:value="product.activityStock" :min="1" :precision="0" style="width: 140px" />
                      </div>
                    </div>

                    <div v-if="product.availableSessions?.length" class="session-box">
                      <div class="section-title">可报名场次</div>
                      <a-select v-model:value="product.sessionIds" mode="multiple" allow-clear placeholder="请选择场次" style="width: 100%" :options="sessionOptions(product.availableSessions)" />
                    </div>

                    <div class="pricing-block">
                      <div v-for="skc in product.skcList" :key="skc.skcId" class="skc-box">
                        <div class="skc-head">
                          <div>
                            <div class="section-title">SKC {{ skc.skcId }}</div>
                            <div class="sub-line">日常价：{{ displayMoney(skc.dailyPrice) }} / 建议活动价：{{ displayMoney(skc.suggestActivityPrice) }}</div>
                          </div>
                          <div v-if="product.isApparel === 1" class="inline-field">
                            <span>SKC 活动价</span>
                            <a-input-number v-model:value="skc.editActivityPrice" :min="0" :precision="0" style="width: 150px" />
                          </div>
                        </div>

                        <div v-if="skc.sitePriceList?.length" class="site-price-grid">
                          <div v-for="sitePrice in skc.sitePriceList" :key="`skc-${skc.skcId}-${sitePrice.siteId}`" class="site-price-item">
                            <div>{{ sitePrice.siteName || `站点${sitePrice.siteId}` }}</div>
                            <div class="sub-line">日常价：{{ displayMoney(sitePrice.dailyPrice) }} / 建议：{{ displayMoney(sitePrice.suggestActivityPrice) }}</div>
                            <a-input-number v-model:value="sitePrice.editActivityPrice" :min="0" :precision="0" style="width: 100%" />
                          </div>
                        </div>

                        <div v-if="skc.skuList?.length" class="sku-list">
                          <div v-for="sku in skc.skuList" :key="sku.skuId" class="sku-item">
                            <div class="sku-meta">
                              <img v-if="safeImageUrl(sku.imageUrl)" :src="safeImageUrl(sku.imageUrl)" class="sku-thumb" />
                              <div class="sku-info">
                                <div>SKU {{ sku.skuId }} <span v-if="sku.extCode">/ {{ sku.extCode }}</span></div>
                                <div class="sub-line">{{ sku.specInfo || '暂无规格' }}</div>
                                <div class="sub-line">日常价：{{ displayMoney(sku.dailyPrice) }} / 建议活动价：{{ displayMoney(sku.suggestActivityPrice) }}</div>
                              </div>
                            </div>
                            <div v-if="product.isApparel !== 1" class="sku-edit-row">
                              <div class="inline-field">
                                <span>SKU 活动价</span>
                                <a-input-number v-model:value="sku.editActivityPrice" :min="0" :precision="0" style="width: 150px" />
                              </div>
                            </div>
                            <div v-if="sku.sitePriceList?.length" class="site-price-grid nested">
                              <div v-for="sitePrice in sku.sitePriceList" :key="`sku-${sku.skuId}-${sitePrice.siteId}`" class="site-price-item">
                                <div>{{ sitePrice.siteName || `站点${sitePrice.siteId}` }}</div>
                                <div class="sub-line">日常价：{{ displayMoney(sitePrice.dailyPrice) }} / 建议：{{ displayMoney(sitePrice.suggestActivityPrice) }}</div>
                                <a-input-number v-model:value="sitePrice.editActivityPrice" :min="0" :precision="0" style="width: 100%" />
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </a-card>
                </div>
              </template>
            </a-card>
          </a-tab-pane>

          <a-tab-pane key="activities" tab="可用活动">
            <a-spin :spinning="activitiesLoading">
              <a-empty v-if="activities.length === 0" description="暂无活动数据，请先同步活动列表" />
              <a-collapse v-else>
                <a-collapse-panel v-for="act in activities" :key="act.id" :header="`${act.activityName}（${activityTypeText(act.activityType)}）`">
                  <div class="panel-copy">{{ act.activityContent || '暂无活动文案' }}</div>
                  <div class="panel-actions">
                    <a-button type="primary" size="small" @click="pickActivity(act)">去报名</a-button>
                  </div>
                  <a-table v-if="act.thematicList?.length" :dataSource="act.thematicList" rowKey="id" :pagination="false" bordered size="small">
                    <a-table-column title="主题名称" dataIndex="activityThematicName" />
                    <a-table-column title="报名来源" width="120">
                      <template #default="{ record }">{{ record.enrollSource === 1 ? '自主报名' : '邀约报名' }}</template>
                    </a-table-column>
                    <a-table-column title="报名开始" width="180">
                      <template #default="{ record }">{{ formatTs(record.enrollStartAt) }}</template>
                    </a-table-column>
                    <a-table-column title="报名结束" width="180">
                      <template #default="{ record }">{{ formatTs(record.enrollDeadLine) }}</template>
                    </a-table-column>
                    <a-table-column title="操作" width="100">
                      <template #default="{ record }">
                        <a @click="pickActivity(act, record)">选择</a>
                      </template>
                    </a-table-column>
                  </a-table>
                </a-collapse-panel>
              </a-collapse>
            </a-spin>
          </a-tab-pane>

          <a-tab-pane key="enrollments" tab="报名记录">
            <a-table rowKey="id" :columns="enrollColumns" :dataSource="enrollRows" :loading="loading" :pagination="pagination" :scroll="{ x: 1400 }" tableLayout="fixed" @change="onTableChange">
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'enrollStatus'">
                  <a-tag :color="enrollStatusColor(record.enrollStatus)">{{ enrollStatusText(record.enrollStatus) }}</a-tag>
                </template>
                <template v-else-if="column.key === 'actions'">
                  <a @click="openEnrollDetail(record)">详情</a>
                </template>
                <template v-else-if="column.key === 'activityType'">
                  {{ record.activityTypeName || activityTypeText(record.activityType) }}
                </template>
                <template v-else-if="column.key === 'syncedAt'">
                  <span class="mono">{{ formatDT(record.syncedAt) }}</span>
                </template>
              </template>
            </a-table>
          </a-tab-pane>
        </a-tabs>
      </a-card>

      <a-modal v-model:open="detailOpen" title="报名详情" :footer="null" width="960" :bodyStyle="{ maxHeight: '75vh', overflowY: 'auto' }">
        <template v-if="detailData">
          <a-descriptions bordered :column="2" size="small">
            <a-descriptions-item label="报名ID">{{ detailData.enrollId }}</a-descriptions-item>
            <a-descriptions-item label="商品ID">{{ detailData.productId }}</a-descriptions-item>
            <a-descriptions-item label="活动类型">{{ detailData.activityTypeName || activityTypeText(detailData.activityType) }}</a-descriptions-item>
            <a-descriptions-item label="主题名称">{{ detailData.activityThematicName || '-' }}</a-descriptions-item>
            <a-descriptions-item label="报名状态">
              <a-tag :color="enrollStatusColor(detailData.enrollStatus)">{{ enrollStatusText(detailData.enrollStatus) }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="活动库存">{{ detailData.activityStock ?? '-' }}</a-descriptions-item>
            <a-descriptions-item label="剩余库存">{{ detailData.remainingActivityStock ?? '-' }}</a-descriptions-item>
            <a-descriptions-item label="币种">{{ detailData.currency || '-' }}</a-descriptions-item>
          </a-descriptions>

          <a-divider>价格明细</a-divider>
          <a-table :dataSource="detailData.priceList || []" rowKey="id" :pagination="false" bordered size="small" :scroll="{ x: 800 }">
            <a-table-column title="层级" dataIndex="level" width="80" />
            <a-table-column title="SKC ID" dataIndex="skcId" width="140" />
            <a-table-column title="SKU ID" dataIndex="skuId" width="140" />
            <a-table-column title="站点" dataIndex="siteName" width="100" />
            <a-table-column title="日常价(分)" dataIndex="dailyPrice" width="110" />
            <a-table-column title="活动价(分)" dataIndex="activityPrice" width="110" />
            <a-table-column title="活动折扣" dataIndex="activityDiscount" width="100" />
          </a-table>
        </template>
      </a-modal>
    </div>
  </ProLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import ShopTabs from '@/platform/components/ShopTabs.vue'
import { syncApi } from '@/platform/api/sync'
import { temuShopsApi } from '@/platform/api/temuShops'

const ACTIVITY_TYPE_OPTIONS = [
  { value: 1, label: '限时秒杀' },
  { value: 5, label: '大促活动' },
  { value: 13, label: '大促进阶' },
  { value: 27, label: '清仓甩卖' },
  { value: 101, label: '秒杀进阶' }
]

const ENROLL_STATUS_OPTIONS = [
  { value: 1, label: '报名中' },
  { value: 2, label: '报名失败' },
  { value: 3, label: '报名成功待分配场次' },
  { value: 4, label: '报名成功已分配场次' },
  { value: 5, label: '报名活动已结束' },
  { value: 6, label: '报名活动已下线' }
]

const shopsLoading = ref(false)
const shops = ref([])
const shopId = ref(null)
const loading = ref(false)
const activitiesLoading = ref(false)
const activeTab = ref('workbench')
const activities = ref([])
const enrollRows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const filters = reactive({ activityType: undefined, enrollStatus: undefined })
const detailOpen = ref(false)
const detailData = ref(null)

const workbench = reactive({
  selectedActivityId: undefined,
  selectedThematicId: undefined,
  detailLoading: false,
  detail: null,
  goodsKeyword: '',
  goodsLoading: false,
  goodsRows: [],
  goodsPage: 1,
  goodsPageSize: 10,
  goodsTotal: 0,
  matching: false,
  matchedProducts: [],
  sessionLoading: false,
  sessionsLoaded: false,
  submitting: false
})

const selectedGoodsRowKeys = ref([])

const selectedActivity = computed(() => activities.value.find((item) => item.id === workbench.selectedActivityId) || null)
const selectedThematic = computed(() => selectedActivity.value?.thematicList?.find((item) => item.activityThematicId === workbench.selectedThematicId) || null)
const activityOptions = computed(() => activities.value.map((item) => ({ value: item.id, label: `${item.activityName}（${activityTypeText(item.activityType)}）` })))
const thematicOptions = computed(() => (selectedActivity.value?.thematicList || []).map((item) => ({ value: item.activityThematicId, label: item.activityThematicName })))

const enrollColumns = [
  { title: '报名ID', dataIndex: 'enrollId', width: 140 },
  { title: '商品ID', dataIndex: 'productId', width: 140 },
  { title: '活动类型', key: 'activityType', width: 140 },
  { title: '主题', dataIndex: 'activityThematicName', width: 180, ellipsis: true },
  { title: '状态', key: 'enrollStatus', width: 180 },
  { title: '活动库存', dataIndex: 'activityStock', width: 100 },
  { title: '同步时间', key: 'syncedAt', width: 180 },
  { title: '操作', key: 'actions', width: 80, fixed: 'right' }
]

const goodsColumns = [
  { title: '主图', key: 'mainImageUrl', width: 80 },
  { title: '商品', key: 'productName', width: 420 },
  { title: '货号', dataIndex: 'extCode', width: 180 },
  { title: '加站状态', key: 'skcSiteStatus', width: 110 },
  { title: '叶子类目', dataIndex: 'leafCatName', width: 180 }
]

const pagination = computed(() => ({
  current: page.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t) => `共 ${t} 条`,
  pageSizeOptions: ['10', '20', '50', '100']
}))

const goodsPagination = computed(() => ({
  current: workbench.goodsPage,
  pageSize: workbench.goodsPageSize,
  total: workbench.goodsTotal,
  showSizeChanger: true,
  showTotal: (t) => `共 ${t} 条`
}))

const goodsRowSelection = computed(() => ({
  selectedRowKeys: selectedGoodsRowKeys.value,
  preserveSelectedRowKeys: true,
  onChange: onGoodsSelectChange
}))

const pad2 = (n) => String(n ?? '').padStart(2, '0')
const formatDT = (v) => {
  if (!v) return '-'
  if (Array.isArray(v) && v.length >= 6) {
    const [y, mo, d, h, m, s] = v
    return `${y}-${pad2(mo)}-${pad2(d)} ${pad2(h)}:${pad2(m)}:${pad2(s)}`
  }
  return typeof v === 'string' ? v : '-'
}
const formatTs = (ts) => {
  if (!ts) return '-'
  const d = new Date(ts * 1000)
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
}

const activityTypeText = (value) => ACTIVITY_TYPE_OPTIONS.find((item) => item.value === value)?.label || (value ?? '-')
const enrollStatusText = (value) => ({
  1: '报名中',
  2: '报名失败',
  3: '报名成功待分配场次',
  4: '报名成功已分配场次',
  5: '报名活动已结束',
  6: '报名活动已下线'
}[value] || '未知')
const enrollStatusColor = (value) => ({ 1: 'processing', 2: 'error', 3: 'warning', 4: 'success', 5: 'default', 6: 'default' }[value] || 'default')
const requirementColor = (value) => ({ 1: 'success', 2: 'processing', 3: 'blue', 0: 'error' }[value] || 'default')
const sessionStatusText = (value) => ({ 1: '未开始', 2: '进行中', 3: '已结束', 4: '报名失败', 5: '已售罄', 6: '已下线' }[value] || '未知')
const displayMoney = (value) => value === undefined || value === null || value === '' ? '-' : `${value}`
const safeImageUrl = (value) => {
  if (!value) return ''
  const text = String(value).trim()
  if (!text) return ''

  try {
    return text
      .replace(/\\/g, '/')
      .replace(/%(?![0-9A-Fa-f]{2})/g, '%25')
  } catch {
    return ''
  }
}

const loadShops = async () => {
  shopsLoading.value = true
  try {
    const r = await temuShopsApi.list({ enabled: true })
    shops.value = r?.success ? (r.data || []) : []
    const firstShopId = shops.value[0]?.shopId || null
    if (!firstShopId) {
      shopId.value = null
      activities.value = []
      enrollRows.value = []
      total.value = 0
      resetWorkbench()
      return
    }
    if (!shopId.value || !shops.value.some((item) => item.shopId === shopId.value)) {
      shopId.value = firstShopId
      await reloadAll()
    }
  } catch {
    message.error('店铺加载失败')
  } finally {
    shopsLoading.value = false
  }
}

const resetWorkbench = () => {
  workbench.selectedActivityId = undefined
  workbench.selectedThematicId = undefined
  workbench.detail = null
  workbench.goodsKeyword = ''
  workbench.goodsRows = []
  workbench.goodsPage = 1
  workbench.goodsTotal = 0
  workbench.matchedProducts = []
  workbench.sessionsLoaded = false
  selectedGoodsRowKeys.value = []
}

const onShopChange = async () => {
  resetWorkbench()
  if (!shopId.value) return
  await reloadAll()
}

const loadActivities = async () => {
  if (!shopId.value) return
  activitiesLoading.value = true
  try {
    const res = await syncApi.getActivityList({ shopId: shopId.value, activityType: filters.activityType })
    if (res?.success) {
      activities.value = res.data || []
      if (workbench.selectedActivityId && !activities.value.some((item) => item.id === workbench.selectedActivityId)) {
        workbench.selectedActivityId = undefined
        workbench.selectedThematicId = undefined
        workbench.detail = null
      }
    } else {
      message.error(res?.message || '加载活动失败')
    }
  } catch (e) {
    message.error(e.message || '加载活动失败')
  } finally {
    activitiesLoading.value = false
  }
}

const fetchList = async () => {
  if (!shopId.value) return
  loading.value = true
  try {
    const res = await syncApi.getEnrollmentList({
      shopId: shopId.value,
      activityType: filters.activityType,
      enrollStatus: filters.enrollStatus,
      page: page.value,
      pageSize: pageSize.value
    })
    if (res?.success) {
      enrollRows.value = res.data?.content || []
      total.value = res.data?.totalElements || 0
    } else {
      message.error(res?.message || '加载报名记录失败')
    }
  } catch (e) {
    message.error(e.message || '加载报名记录失败')
  } finally {
    loading.value = false
  }
}

const fetchGoods = async () => {
  if (!shopId.value || !selectedActivity.value) return
  workbench.goodsLoading = true
  try {
    const res = await syncApi.getGoodsList({
      shopId: shopId.value,
      keyword: (workbench.goodsKeyword || '').trim() || undefined,
      skcSiteStatus: 1,
      page: workbench.goodsPage,
      pageSize: workbench.goodsPageSize
    })
    if (res?.success) {
      workbench.goodsRows = res.data?.content || []
      workbench.goodsTotal = res.data?.totalElements || 0
    } else {
      message.error(res?.message || '加载本地商品失败')
    }
  } catch (e) {
    message.error(e.message || '加载本地商品失败')
  } finally {
    workbench.goodsLoading = false
  }
}

const reloadAll = async () => {
  page.value = 1
  await Promise.all([loadActivities(), fetchList()])
}

const reloadGoods = async () => {
  workbench.goodsPage = 1
  await fetchGoods()
}

const reset = async () => {
  filters.activityType = undefined
  filters.enrollStatus = undefined
  resetWorkbench()
  if (shopId.value) {
    await reloadAll()
  }
}

const onTableChange = (p) => {
  page.value = p.current
  pageSize.value = p.pageSize
  fetchList()
}

const onGoodsTableChange = (p) => {
  workbench.goodsPage = p.current
  workbench.goodsPageSize = p.pageSize
  fetchGoods()
}

const onGoodsSelectChange = (keys) => {
  selectedGoodsRowKeys.value = keys
}

const onSelectedActivityChange = async () => {
  workbench.selectedThematicId = undefined
  workbench.detail = null
  workbench.matchedProducts = []
  workbench.sessionsLoaded = false
  selectedGoodsRowKeys.value = []
  workbench.goodsRows = []
  workbench.goodsPage = 1
  workbench.goodsTotal = 0
  if (selectedActivity.value) {
    await fetchGoods()
  }
}

const ensureActivitySelection = () => {
  if (!shopId.value) {
    throw new Error('请先选择店铺')
  }
  if (!selectedActivity.value) {
    throw new Error('请先选择活动')
  }
  if (selectedActivity.value.thematicList?.length && !workbench.selectedThematicId) {
    throw new Error('该活动需要先选择主题')
  }
}

const loadSelectedActivityDetail = async () => {
  try {
    ensureActivitySelection()
  } catch (e) {
    message.warning(e.message)
    return
  }
  workbench.detailLoading = true
  try {
    const res = await syncApi.getActivityDetail({
      shopId: shopId.value,
      activityType: selectedActivity.value.activityType,
      activityThematicId: workbench.selectedThematicId || undefined
    })
    if (res?.success) {
      workbench.detail = res.data
    } else {
      message.error(res?.message || '加载活动规则失败')
    }
  } catch (e) {
    message.error(e.message || '加载活动规则失败')
  } finally {
    workbench.detailLoading = false
  }
}

const normalizeMatchedProducts = (items = []) => items.map((product) => ({
  ...product,
  activityStock: product.targetActivityStock ?? product.suggestActivityStock ?? 1,
  sessionIds: Array.isArray(product.enrollSessionIdList) ? [...product.enrollSessionIdList] : [],
  availableSessions: [],
  skcList: (product.skcList || []).map((skc) => ({
    ...skc,
    editActivityPrice: skc.activityPrice ?? skc.suggestActivityPrice,
    sitePriceList: (skc.sitePriceList || []).map((sitePrice) => ({
      ...sitePrice,
      editActivityPrice: sitePrice.activityPrice ?? sitePrice.suggestActivityPrice
    })),
    skuList: (skc.skuList || []).map((sku) => ({
      ...sku,
      editActivityPrice: sku.activityPrice ?? sku.suggestActivityPrice,
      sitePriceList: (sku.sitePriceList || []).map((sitePrice) => ({
        ...sitePrice,
        editActivityPrice: sitePrice.activityPrice ?? sitePrice.suggestActivityPrice
      }))
    }))
  }))
}))

const matchSelectedGoods = async () => {
  try {
    ensureActivitySelection()
    if (!selectedGoodsRowKeys.value.length) {
      throw new Error('请先勾选本地商品')
    }
    if (!workbench.detail) {
      await loadSelectedActivityDetail()
    }
    workbench.matching = true
    const res = await syncApi.matchActivityProducts({
      shopId: shopId.value,
      activityType: selectedActivity.value.activityType,
      activityThematicId: workbench.selectedThematicId || undefined,
      productIds: selectedGoodsRowKeys.value,
      rowCount: Math.max(selectedGoodsRowKeys.value.length, 20)
    })
    if (!res?.success) {
      throw new Error(res?.message || '匹配可报名商品失败')
    }
    workbench.matchedProducts = normalizeMatchedProducts(res.data?.matchList || [])
    if (!workbench.matchedProducts.length) {
      message.warning('TEMU 未返回可报名商品，请检查活动条件')
      return
    }
    await loadSessionsForMatched()
    message.success(`已匹配 ${workbench.matchedProducts.length} 个可报名商品`)
  } catch (e) {
    message.error(e.message || '匹配可报名商品失败')
  } finally {
    workbench.matching = false
  }
}

const loadSessionsForMatched = async () => {
  if (!workbench.matchedProducts.length) return
  workbench.sessionLoading = true
  try {
    const res = await syncApi.queryActivitySessions({
      shopId: shopId.value,
      activityType: selectedActivity.value.activityType,
      activityThematicId: workbench.selectedThematicId || undefined,
      productIds: workbench.matchedProducts.map((item) => item.productId)
    })
    if (!res?.success) {
      throw new Error(res?.message || '加载活动场次失败')
    }
    const sessionMap = res.data?.productCanEnrollSessionMap || {}
    workbench.matchedProducts = workbench.matchedProducts.map((product) => {
      const availableSessions = sessionMap[String(product.productId)] || res.data?.list || []
      const defaultSessionIds = product.sessionIds?.length ? product.sessionIds : (product.enrollSessionIdList || [])
      return {
        ...product,
        availableSessions,
        sessionIds: defaultSessionIds.filter((sessionId) => availableSessions.some((item) => item.sessionId === sessionId))
      }
    })
    workbench.sessionsLoaded = true
  } catch (e) {
    message.error(e.message || '加载活动场次失败')
  } finally {
    workbench.sessionLoading = false
  }
}

const filterSitePricePayload = (sitePriceList = []) => sitePriceList
  .filter((item) => item.siteId !== undefined && item.siteId !== null && item.editActivityPrice !== undefined && item.editActivityPrice !== null && `${item.editActivityPrice}` !== '')
  .map((item) => ({ siteId: item.siteId, activityPrice: Number(item.editActivityPrice) }))

const buildEnrollPayload = () => {
  const payload = {
    shopId: shopId.value,
    activityType: selectedActivity.value.activityType,
    activityThematicId: workbench.selectedThematicId || undefined,
    productList: workbench.matchedProducts.map((product) => {
      if (product.activityStock === undefined || product.activityStock === null || `${product.activityStock}` === '') {
        throw new Error(`商品 ${product.productId} 还没有填写活动库存`)
      }
      return {
        productId: product.productId,
        activityStock: Number(product.activityStock),
        sessionIds: Array.isArray(product.sessionIds) && product.sessionIds.length ? product.sessionIds : undefined,
        skcList: (product.skcList || []).map((skc) => {
          const skcPayload = {
            skcId: skc.skcId,
            activityPrice: skc.editActivityPrice !== undefined && skc.editActivityPrice !== null && `${skc.editActivityPrice}` !== '' ? Number(skc.editActivityPrice) : undefined,
            siteActivityPriceList: filterSitePricePayload(skc.sitePriceList),
            skuList: (skc.skuList || []).map((sku) => ({
              skuId: sku.skuId,
              activityPrice: sku.editActivityPrice !== undefined && sku.editActivityPrice !== null && `${sku.editActivityPrice}` !== '' ? Number(sku.editActivityPrice) : undefined,
              siteActivityPriceList: filterSitePricePayload(sku.sitePriceList)
            }))
          }

          const hasSkcPrice = skcPayload.activityPrice !== undefined && skcPayload.activityPrice !== null

          if (product.isApparel === 1 && !hasSkcPrice && !skcPayload.siteActivityPriceList.length) {
            throw new Error(`服饰类商品 ${product.productId} 的 SKC ${skc.skcId} 还没有填写活动价`)
          }

          if (product.isApparel !== 1) {
            const hasAnySkuPrice = skcPayload.skuList.some((sku) => (sku.activityPrice !== undefined && sku.activityPrice !== null) || (sku.siteActivityPriceList && sku.siteActivityPriceList.length))
            if (!hasAnySkuPrice) {
              throw new Error(`非服饰类商品 ${product.productId} 的 SKC ${skc.skcId} 还没有填写 SKU 活动价`)
            }
          }

          return skcPayload
        })
      }
    })
  }
  return payload
}

const submitBatchEnroll = async () => {
  try {
    ensureActivitySelection()
    if (!workbench.matchedProducts.length) {
      throw new Error('请先匹配可报名商品')
    }
    workbench.submitting = true
    const res = await syncApi.batchEnroll(buildEnrollPayload())
    if (!res?.success) {
      throw new Error(res?.message || '批量报名失败')
    }
    if (res.data?.refreshWarning) {
      message.warning(`报名已提交，但报名记录刷新失败：${res.data.refreshWarning}`)
    } else {
      message.success(`报名提交完成，成功 ${res.data?.successCount ?? 0} 个，失败 ${res.data?.failCount ?? 0} 个`)
    }
    activeTab.value = 'enrollments'
    await fetchList()
  } catch (e) {
    message.error(e.message || '批量报名失败')
  } finally {
    workbench.submitting = false
  }
}

const sessionOptions = (sessions = []) => sessions.map((item) => ({
  value: item.sessionId,
  label: `${item.sessionName || `场次${item.sessionId}`} / ${item.siteName || '-'} / ${sessionStatusText(item.sessionStatus)}`
}))

const pickActivity = async (activity, thematic = null) => {
  workbench.selectedActivityId = activity.id
  activeTab.value = 'workbench'
  await onSelectedActivityChange()
  workbench.selectedThematicId = thematic?.activityThematicId
  await loadSelectedActivityDetail()
}

const jumpFromActivitySelection = () => {
  activeTab.value = 'workbench'
}

const openEnrollDetail = async (record) => {
  try {
    const res = await syncApi.getEnrollmentDetail(record.id)
    if (res?.success) {
      detailData.value = res.data
      detailOpen.value = true
    } else {
      message.error(res?.message || '加载详情失败')
    }
  } catch (e) {
    message.error(e.message || '加载详情失败')
  }
}

onMounted(() => {
  loadShops()
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.workbench-grid {
  display: grid;
  grid-template-columns: 1fr 1.4fr;
  gap: 16px;
  margin-bottom: 16px;
}

.goods-toolbar,
.matched-toolbar,
.panel-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.goods-toolbar :deep(.ant-input) {
  flex: 1;
}

.matched-toolbar {
  justify-content: space-between;
  flex-wrap: wrap;
}

.matched-meta,
.goods-tip,
.detail-sub,
.sub-line,
.panel-copy,
.detail-copy {
  color: #6b7280;
  font-size: 12px;
}

.goods-tip {
  margin-bottom: 12px;
}

.detail-head,
.match-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.detail-title,
.match-title,
.section-title {
  font-weight: 600;
}

.detail-copy,
.panel-copy {
  margin-top: 10px;
}

.detail-section {
  margin-top: 12px;
}

.tag-list,
.match-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.thumb,
.match-cover,
.sku-thumb {
  width: 48px;
  height: 48px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
}

.match-cover {
  width: 72px;
  height: 72px;
}

.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  color: #999;
}

.title-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.match-card-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.match-head-side {
  min-width: 140px;
}

.field-label {
  margin-bottom: 8px;
  color: #666;
  font-size: 12px;
}

.session-box,
.pricing-block {
  margin-top: 16px;
}

.skc-box {
  padding: 12px;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  background: #fafafa;
  margin-top: 12px;
}

.skc-head,
.sku-edit-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.inline-field {
  display: flex;
  align-items: center;
  gap: 8px;
}

.site-price-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.site-price-item,
.sku-item {
  padding: 12px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #f0f0f0;
}

.sku-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
}

.sku-meta {
  display: flex;
  gap: 12px;
}

.sku-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nested {
  margin-top: 10px;
}

.mono {
  font-family: 'SF Mono', 'Consolas', monospace;
  font-size: 13px;
}

@media (max-width: 1280px) {
  .workbench-grid {
    grid-template-columns: 1fr;
  }
}
</style>
