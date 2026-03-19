<template>
  <ProLayout title="采集商品库">
    <div class="page">
        <a-card class="toolbar" :bordered="false">
          <a-form layout="inline" :model="filters" @submit.prevent>
          <a-form-item label="关键词">
            <a-input
              v-model:value="filters.q"
              placeholder="商品名 / product_id"
              style="width: 260px"
              allow-clear
              @pressEnter="reload()"
            />
          </a-form-item>

          <a-form-item label="平台">
            <a-input
              v-model:value="filters.sourcePlatform"
              placeholder="如 temu / 1688"
              style="width: 160px"
              allow-clear
              @pressEnter="reload()"
            />
          </a-form-item>

            <a-form-item label="状态">
              <a-select
                v-model:value="filters.collectionStatus"
                :options="statusOptions"
                allow-clear
                placeholder="全部"
                style="width: 140px"
              />
            </a-form-item>

            <a-form-item label="类目">
              <a-select
                v-model:value="filters.temuCatid"
                :options="temuCategoryOptions"
                :loading="loadingTemuCategories"
                allow-clear
                show-search
                :filter-option="filterTemuCategoryOption"
                placeholder="请选择"
                style="width: 240px"
              />
            </a-form-item>

            <a-form-item label="起批量">
              <a-input-number
                v-model:value="filters.moqMin"
                :min="0"
                placeholder="最小"
                style="width: 110px"
              />
              <span class="range-sep">-</span>
              <a-input-number
                v-model:value="filters.moqMax"
                :min="0"
                placeholder="最大"
                style="width: 110px"
              />
            </a-form-item>

            <a-form-item label="轮播图数">
              <a-input-number v-model:value="filters.carouselImageCountMin" :min="0" placeholder="最小" style="width: 110px" />
              <span class="range-sep">-</span>
              <a-input-number v-model:value="filters.carouselImageCountMax" :min="0" placeholder="最大" style="width: 110px" />
            </a-form-item>

            <a-form-item label="详情图数">
              <a-input-number v-model:value="filters.detailImageCountMin" :min="0" placeholder="最小" style="width: 110px" />
              <span class="range-sep">-</span>
              <a-input-number v-model:value="filters.detailImageCountMax" :min="0" placeholder="最大" style="width: 110px" />
            </a-form-item>

            <a-form-item label="SKU数">
              <a-input-number v-model:value="filters.skuCountMin" :min="0" placeholder="最小" style="width: 110px" />
              <span class="range-sep">-</span>
              <a-input-number v-model:value="filters.skuCountMax" :min="0" placeholder="最大" style="width: 110px" />
            </a-form-item>

          <a-form-item>
            <a-checkbox v-model:checked="filters.showDeleted">显示已删除</a-checkbox>
          </a-form-item>

          <a-form-item>
            <a-space>
              <a-button type="primary" :loading="loading" @click="reload()">查询</a-button>
              <a-button @click="reset()">重置</a-button>
            </a-space>
          </a-form-item>
        </a-form>
      </a-card>

      <a-card class="table-card" :bordered="false">
        <a-table
          rowKey="id"
          :columns="columns"
          :dataSource="rows"
          :loading="loading"
          :pagination="pagination"
          :scroll="{ x: 1520 }"
          tableLayout="fixed"
          @change="onTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'image'">
              <a-image
                v-if="record.productMainImage"
                :src="record.productMainImage"
                :width="64"
                :height="64"
                style="border-radius: 8px; object-fit: cover"
              />
              <div v-else class="img-empty">-</div>
            </template>

            <template v-else-if="column.key === 'name'">
              <div class="name-cell">
                <div class="name-title" :title="record.productName || ''">{{ record.productName }}</div>
                <div class="name-sub">
                  <span class="pill mono">#{{ record.id }}</span>
                  <span class="pill mono">{{ record.productId || '-' }}</span>
                </div>
              </div>
            </template>

            <template v-else-if="column.key === 'ocr'">
              <a-tag :color="ocrColor(record.ocrStatus)">{{ ocrLabel(record.ocrStatus) }}</a-tag>
            </template>

            <template v-else-if="column.key === 'exec'">
              <a-tooltip v-if="record.execStatus === 3 && record.execResult" :title="record.execResult" placement="top">
                <a-tag color="red">失败</a-tag>
              </a-tooltip>
              <a-tag v-else-if="record.execStatus === 2" color="green">成功</a-tag>
              <a-tag v-else-if="record.execStatus === 1" color="processing">执行中</a-tag>
              <a-tag v-else color="default">待执行</a-tag>
            </template>

            <template v-else-if="column.key === 'weight'">
              <span class="mono">
                {{ record.packagingWeight ?? record.netWeight ?? '-' }}
              </span>
            </template>

            <template v-else-if="column.key === 'temuCatname'">
              <span class="temu-cat" :title="record.temuCatname || ''">
                {{ record.temuCatname || '-' }}
              </span>
            </template>

            <template v-else-if="column.key === 'moq'">
              <span class="mono">
                {{ extractMoq(record.moqText || record.moq) || '-' }}
              </span>
            </template>

             <template v-else-if="column.key === 'status'">
               <a-tag v-if="record.collectionStatus === 1" color="processing">发布中</a-tag>
              <a-tooltip
                v-else-if="record.collectionStatus === 2 && record.lastPublishRunId"
                placement="top"
                @visibleChange="(v) => onPublishFailTooltipVisibleChange(v, record.lastPublishRunId)"
              >
                <template #title>
                  <div class="publish-fail-tip">
                    <div class="tip-line mono">runId={{ record.lastPublishRunId }}</div>
                    <div v-if="publishFailTipState[String(record.lastPublishRunId)]?.loading" class="tip-line">加载错误详情中...</div>
                    <template v-else>
                      <div
                        v-for="(line, idx) in (publishFailTipState[String(record.lastPublishRunId)]?.lines || [])"
                        :key="idx"
                        class="tip-line"
                      >
                        {{ line }}
                      </div>
                      <div v-if="publishFailTipState[String(record.lastPublishRunId)]?.error" class="tip-line">
                        {{ publishFailTipState[String(record.lastPublishRunId)]?.error }}
                      </div>
                      <div
                        v-if="!(publishFailTipState[String(record.lastPublishRunId)]?.lines || []).length && !publishFailTipState[String(record.lastPublishRunId)]?.error"
                        class="tip-line"
                      >
                        暂无错误日志
                      </div>
                    </template>
                  </div>
                </template>
                <a-tag color="red">发布失败</a-tag>
              </a-tooltip>
              <a-tag v-else-if="record.collectionStatus === 2" color="red">发布失败</a-tag>
              <a-tooltip v-else-if="record.temuPublished" :title="record.temuGoodsId ? `goodsId=${record.temuGoodsId}` : '已发布'" placement="top">
                <a-tag color="green">已发布</a-tag>
              </a-tooltip>
              <a-tag v-else color="default">未发布</a-tag>
            </template>

            <template v-else-if="column.key === 'actions'">
              <div class="actions-cell">
                <a-button size="small" type="primary" class="btn-solid" @click="openDetail(record)">详情</a-button>
                <a-button
                  size="small"
                  type="primary"
                  ghost
                  class="btn-publish"
                  :disabled="!canPublish(record)"
                  @click="publish(record)"
                >
                  发布
                </a-button>

                <a-dropdown trigger="click">
                  <a-button size="small" class="btn-more">
                    更多
                    <span class="more-caret">▾</span>
                  </a-button>
                  <template #overlay>
                    <a-menu>
                      <a-menu-item @click="openTemuMatcher(record)">
                        匹配TEMU类目
                      </a-menu-item>
                      <a-menu-item @click="openTemuAttributes(record)">
                        TEMU属性
                      </a-menu-item>
                      <a-menu-item @click="openSkuConvert(record)">
                        SKU 转换
                      </a-menu-item>
                      <a-menu-item v-if="[1, 3].includes(record.execStatus)" @click="confirmRequeue(record)">
                        <span class="danger-text">退回待执行</span>
                      </a-menu-item>
                      <a-menu-divider />
                      <a-menu-item @click="openEdit(record)">
                        编辑标题
                      </a-menu-item>
                      <a-menu-item @click="confirmDelete(record)">
                        <span class="danger-text">删除</span>
                      </a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
              </div>
            </template>
          </template>
        </a-table>
      </a-card>

      <a-modal
        v-model:open="temuModalOpen"
        title="匹配 TEMU 类目"
        :ok-button-props="{ disabled: !selectedTemuOption }"
        ok-text="保存"
        cancel-text="取消"
        :confirm-loading="savingTemu"
        @ok="saveTemuCategory"
      >
        <div class="temu-modal">
          <div class="temu-desc">
            <div class="temu-title">商品标题</div>
            <div class="temu-value">{{ temuTitle }}</div>
          </div>

          <div v-if="temuOptions.length" class="temu-options">
            <a-radio-group v-model:value="selectedTemuKey" style="width: 100%">
              <a-space direction="vertical" style="width: 100%" :size="10">
                <a-radio v-for="opt in temuOptions" :key="opt.leafId" :value="opt.leafId">
                  <div class="temu-path">{{ opt.pathNames || opt.pathText }}</div>
                  <div class="temu-leaf">leaf: {{ opt.leafName }} ({{ opt.leafId }})</div>
                </a-radio>
              </a-space>
            </a-radio-group>
          </div>
          <div v-else class="temu-empty">
            未返回可选类目
          </div>
        </div>
      </a-modal>

      <a-modal
        v-model:open="temuAttrOpen"
        title="填写 TEMU 商品属性"
        width="920"
        :confirm-loading="savingTemuAttr"
        :mask-closable="!savingTemuAttr && !aiFillingTemuAttr"
        :keyboard="!savingTemuAttr && !aiFillingTemuAttr"
      >
        <template #footer>
          <a-space>
            <a-button :disabled="savingTemuAttr || aiFillingTemuAttr" @click="temuAttrOpen = false">取消</a-button>
            <a-button :loading="aiFillingTemuAttr" :disabled="savingTemuAttr" @click="aiFillTemuAttr">AI 填写</a-button>
            <a-button type="primary" :loading="savingTemuAttr" :disabled="aiFillingTemuAttr" @click="saveTemuAttributes">保存</a-button>
          </a-space>
        </template>

        <div class="temu-attr">
          <div class="temu-attr-head">
            <div class="temu-title">类目</div>
            <div class="temu-value">{{ temuRecord?.temuCatname || '-' }}</div>
          </div>

          <div v-if="temuAttrGroups.length" class="temu-attr-body">
            <div class="temu-attr-grid">
              <div class="temu-attr-item" v-for="p in temuAttrGroups" :key="attrKey(p)" v-show="shouldShowTemuAttrItem(p)">
                <div class="temu-attr-name" :title="p.name">
                  <span v-if="p.required" class="req-dot" title="必填"></span>
                  <span class="name-text">{{ p.name }}</span>
                </div>

                <div class="temu-attr-input">
                  <a-select
                    v-if="p.values && p.values.length"
                    size="small"
                    v-model:value="temuAttrValues[attrKey(p)]"
                    :options="visibleAttrOptions(p)"
                    :mode="p.chooseMaxNum && p.chooseMaxNum > 1 ? 'multiple' : undefined"
                    allow-clear
                    :placeholder="(p.__rule && (p.__rule.fillMode === 'FORCE_EMPTY' || p.__rule.fillMode === 'FIXED_VALUE'))
                      ? '已锁定'
                      : (isChildAttr(p) && !childAllowedInfo(p).hasParentSelected ? (childAllowedInfo(p).parentHint || '请先选择父属性') : '请选择')"
                    style="width: 100%"
                    :disabled="p.__rule && (p.__rule.fillMode === 'FORCE_EMPTY' || p.__rule.fillMode === 'FIXED_VALUE')"
                    @change="onTemuAttrChanged"
                  />
                  <a-input
                    v-else
                    size="small"
                    v-model:value="temuAttrValues[attrKey(p)]"
                    allow-clear
                    placeholder="请输入"
                    :disabled="p.__rule && (p.__rule.fillMode === 'FORCE_EMPTY' || p.__rule.fillMode === 'FIXED_VALUE')"
                    @change="onTemuAttrChanged"
                  />
                </div>
              </div>
            </div>
          </div>
          <div v-else class="temu-empty">未获取到可填写属性（请先匹配 TEMU 类目）</div>
        </div>
      </a-modal>

      <a-drawer
        v-model:open="editOpen"
        title="编辑标题"
        width="520"
        :destroyOnClose="true"
      >
        <a-form layout="vertical" :model="editForm">
          <a-form-item label="标题" required>
            <a-input v-model:value="editForm.productName" allow-clear placeholder="请输入标题" />
          </a-form-item>

          <div class="drawer-actions">
            <a-space>
              <a-button @click="editOpen = false">取消</a-button>
              <a-button type="primary" :loading="saving" @click="saveEdit()">保存</a-button>
            </a-space>
          </div>
        </a-form>
      </a-drawer>

      <TemuSkuConverterModal
        v-model="skuConvertOpen"
        :record="skuConvertRecord"
      />
    </div>
  </ProLayout>
</template>

<script setup>
 import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import { productCollectionApi } from '@/platform/api/productCollections'
import { publishLogsApi } from '@/platform/api/publishLogs'
import TemuSkuConverterModal from '@/platform/components/TemuSkuConverterModal.vue'

const loading = ref(false)
const rows = ref([])
const total = ref(0)

const filters = reactive({
  q: '',
  sourcePlatform: '',
  collectionStatus: null,
  showDeleted: false,

  // search filters
  temuCatid: null,
  moqMin: null,
  moqMax: null,
  carouselImageCountMin: null,
  carouselImageCountMax: null,
  detailImageCountMin: null,
  detailImageCountMax: null,
  skuCountMin: null,
  skuCountMax: null
})

const loadingTemuCategories = ref(false)
const temuCategoryOptions = ref([])

const filterTemuCategoryOption = (input, option) => {
  const v = (option?.label ?? '').toString().toLowerCase()
  return v.includes((input ?? '').toString().trim().toLowerCase())
}

const fetchTemuCategories = async () => {
  loadingTemuCategories.value = true
  try {
    const res = await productCollectionApi.listTemuCategories({ showDeleted: filters.showDeleted ? true : undefined })
    if (res?.success) {
      const rows = Array.isArray(res.data) ? res.data : []
      temuCategoryOptions.value = rows
        .filter(r => r && r.temuCatid && r.temuCatname)
        .map(r => ({ value: String(r.temuCatid), label: String(r.temuCatname) }))
      return
    }
    message.error(res?.message || '加载类目失败')
  } catch (e) {
    message.error(e.message || '加载类目失败')
  } finally {
    loadingTemuCategories.value = false
  }
}

watch(
  () => filters.showDeleted,
  () => {
    fetchTemuCategories()
  }
)

const page = ref(1)
const pageSize = ref(20)

const statusOptions = [
  { value: 0, label: '未发布' },
  { value: 1, label: '发布中' },
  { value: 2, label: '发布失败' }
]

const statusLabel = (v) => {
  if (v === null || v === undefined) return '-'
  if (v === 0) return '未发布'
  if (v === 1) return '发布中'
  if (v === 2) return '发布失败'
  return String(v)
}

const statusColor = (v) => {
  if (v === 0) return 'default'
  if (v === 1) return 'processing'
  if (v === 2) return 'error'
  return 'default'
}

const extractMoq = (text) => {
  if (!text) return ''
  const s = String(text).replace(/\s+/g, ' ').trim()
  if (!s) return ''

  // Prefer explicit patterns like "10个起批" / "1件起批" / "1000个起批"
  // Accept Chinese units: 个/件/只/条/双/套/箱/对/张/包/瓶/盒/台/本/幅/米
  let m = s.match(/(\d+(?:\.\d+)?)\s*(个|件|只|条|双|套|箱|对|张|包|瓶|盒|台|本|幅|米)\s*起批/)
  if (m) return `${m[1]}${m[2]}`

  // Also handle "10件预估到手单价" style (no 起批 but implies moq)
  m = s.match(/(\d+(?:\.\d+)?)\s*(个|件|只|条|双|套|箱|对|张|包|瓶|盒|台|本|幅|米)\s*预估/)
  if (m) return `${m[1]}${m[2]}`

  // Fallback: handle "1000个起批" inside price ladder strings
  m = s.match(/(\d+(?:\.\d+)?)\s*(个|件|只|条|双|套|箱|对|张|包|瓶|盒|台|本|幅|米)\s*起/)
  if (m) return `${m[1]}${m[2]}`

  // As a last resort, if only a number exists, return that number.
  m = s.match(/\b(\d+(?:\.\d+)?)\b/)
  if (m) return m[1]
  return ''
}

const pagination = computed(() => ({
  current: page.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t) => `共 ${t} 条`,
  pageSizeOptions: ['10', '20', '50', '100']
}))

const columns = [
  { title: '图片', key: 'image', width: 88 },
  { title: '商品', key: 'name', width: 420 },
  { title: 'OCR', key: 'ocr', width: 110 },
  { title: '执行状态', key: 'exec', width: 110 },
  { title: '起批量', key: 'moq', width: 90 },
  { title: '重量', key: 'weight', width: 110 },
  { title: '轮播图数', dataIndex: 'carouselImageCount', key: 'carouselImageCount', width: 90 },
  { title: '详情图数', dataIndex: 'detailImageCount', key: 'detailImageCount', width: 90 },
  { title: 'SKU数', dataIndex: 'skuCount', key: 'skuCount', width: 80 },
  { title: 'Temu类目', dataIndex: 'temuCatname', key: 'temuCatname', width: 120 },
  { title: '状态', key: 'status', width: 110 },
  { title: '操作', key: 'actions', width: 220, fixed: 'right' }
]

const ocrLabel = (v) => {
  const n = v === null || v === undefined ? 0 : Number(v)
  if (n === 0) return '待OCR'
  if (n === 1) return 'OCR中'
  if (n === 2) return '已完成'
  if (n === 3) return '失败'
  return String(v)
}

const ocrColor = (v) => {
  const n = v === null || v === undefined ? 0 : Number(v)
  if (n === 0) return 'default'
  if (n === 1) return 'processing'
  if (n === 2) return 'success'
  if (n === 3) return 'error'
  return 'default'
}

const skuConvertOpen = ref(false)
const skuConvertRecord = ref(null)

const openSkuConvert = (r) => {
  skuConvertRecord.value = r
  skuConvertOpen.value = true
}

const canPublish = (r) => {
  const hasCat = !!(r?.temuCatid && r?.temuCatname)
  // We didn't include temuAttributes/temuSkus in list rows; publish API will validate again.
  return hasCat
}

const publish = async (r) => {
  if (!r?.id) return
  Modal.confirm({
    title: '发布到 TEMU？',
    content: '将自动检查/转换图片为 800x800，并将非 kwcdn 链接上传替换。',
    okText: '发布',
    okType: 'primary',
    cancelText: '取消',
    onOk: async () => {
      try {
        message.loading({ content: '正在发布（可能需要 1-3 分钟）...', key: `publish-${r.id}`, duration: 0 })
        const res = await productCollectionApi.publishToTemu(r.id)
        if (res?.success) {
          const runId = res.data?.runId
          const goodsId = res.data?.goodsId
          message.success({ content: `发布成功 goodsId=${goodsId || '-'}${runId ? ` (runId=${runId})` : ''}`, key: `publish-${r.id}` })
          await fetchList()
          return
        }
        message.error({ content: res?.message || '发布失败', key: `publish-${r.id}` })
      } catch (e) {
        message.error({ content: e.message || '发布失败', key: `publish-${r.id}` })
      }
    }
  })
}

const fetchList = async () => {
  loading.value = true
  try {
    const params = {
      q: filters.q || undefined,
      sourcePlatform: filters.sourcePlatform || undefined,
      collectionStatus: filters.collectionStatus ?? undefined,
      showDeleted: filters.showDeleted ? true : undefined,
      temuCatid: filters.temuCatid || undefined,
      moqMin: filters.moqMin ?? undefined,
      moqMax: filters.moqMax ?? undefined,
      carouselImageCountMin: filters.carouselImageCountMin ?? undefined,
      carouselImageCountMax: filters.carouselImageCountMax ?? undefined,
      detailImageCountMin: filters.detailImageCountMin ?? undefined,
      detailImageCountMax: filters.detailImageCountMax ?? undefined,
      skuCountMin: filters.skuCountMin ?? undefined,
      skuCountMax: filters.skuCountMax ?? undefined,
      page: page.value - 1,
      size: pageSize.value
    }
      const res = await productCollectionApi.list(params)
      if (res?.success) {
        rows.value = res.data?.content || []
        total.value = res.data?.totalElements || 0
        return
      }
    message.error(res?.message || '加载失败')
  } catch (e) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const reload = async () => {
  page.value = 1
  await fetchList()
}

const reset = async () => {
  filters.q = ''
  filters.sourcePlatform = ''
  filters.collectionStatus = null
  filters.showDeleted = false

  filters.temuCatid = null
  filters.moqMin = null
  filters.moqMax = null
  filters.carouselImageCountMin = null
  filters.carouselImageCountMax = null
  filters.detailImageCountMin = null
  filters.detailImageCountMax = null
  filters.skuCountMin = null
  filters.skuCountMax = null

  await fetchTemuCategories()
  await reload()
}

const onTableChange = (p) => {
  page.value = p.current
  pageSize.value = p.pageSize
  fetchList()
}

const editOpen = ref(false)
const saving = ref(false)
const matchingId = ref(null)
const temuModalOpen = ref(false)
const temuOptions = ref([])
const selectedTemuKey = ref(null)
const savingTemu = ref(false)
const temuRecord = ref(null)

const temuAttrOpen = ref(false)
const temuAttrGroups = ref([])
const temuAttrValues = reactive({})
const temuAttrParentSelectedVids = reactive({})
const savingTemuAttr = ref(false)
const aiFillingTemuAttr = ref(false)

const temuTitle = computed(() => temuRecord.value?.productName || '')

const publishFailTipState = reactive({})

const extractPublishFailLines = (logs = []) => {
  const arr = Array.isArray(logs) ? logs : []

  const pickLine = (l) => {
    const stage = l?.stage ? `[${l.stage}]` : ''
    const msg = l?.message || ''
    return `${stage}${msg}`.trim() || '(empty message)'
  }

  // 1) Prefer explicit ERROR logs
  const errLines = arr
    .filter(l => (l?.level || '').toUpperCase() === 'ERROR')
    .slice(-6)
    .map(pickLine)
  if (errLines.length) return errLines

  // 2) Fallback: show WARN logs
  const warnLines = arr
    .filter(l => (l?.level || '').toUpperCase() === 'WARN')
    .slice(-6)
    .map(pickLine)
  if (warnLines.length) return warnLines

  // 3) Fallback: parse "raw response" / payload for errorMsg even if INFO
  const tryParseJson = (s) => {
    if (!s) return null
    try {
      return JSON.parse(s)
    } catch {
      return null
    }
  }

  for (let i = arr.length - 1; i >= 0; i--) {
    const l = arr[i]
    const dataJson = l?.dataJson
    if (!dataJson) continue
    const d1 = tryParseJson(dataJson)
    if (!d1) continue
    const raw = typeof d1.raw === 'string' ? d1.raw : null
    const d2 = raw ? tryParseJson(raw) : null
    const errMsg = d2?.errorMsg || d2?.error || d1?.errorMsg || d1?.error
    const errCode = d2?.errorCode || d2?.code || d1?.errorCode || d1?.code
    const success = d2?.success
    if (success === false || errMsg || errCode) {
      const parts = []
      if (errCode != null && String(errCode).trim() !== '') parts.push(`errorCode=${errCode}`)
      if (errMsg) parts.push(`errorMsg=${errMsg}`)
      if (parts.length) return [parts.join(' ')]
    }
  }

  // 4) Last resort: show last 2 INFO lines
  const infoLines = arr
    .filter(l => (l?.level || '').toUpperCase() === 'INFO')
    .slice(-2)
    .map(pickLine)
  return infoLines
}

const onPublishFailTooltipVisibleChange = async (visible, runId) => {
  if (!visible) return
  const id = String(runId || '').trim()
  if (!id) return
  if (publishFailTipState[id]?.loaded || publishFailTipState[id]?.loading) return

  publishFailTipState[id] = { loading: true, loaded: false, lines: [], error: '' }
  try {
    const res = await publishLogsApi.listLogs(id)
    if (res?.success) {
      const logs = Array.isArray(res.data) ? res.data : []
      const lines = extractPublishFailLines(logs)

      publishFailTipState[id] = {
        loading: false,
        loaded: true,
        lines,
        error: ''
      }
      return
    }
    publishFailTipState[id] = { loading: false, loaded: true, lines: [], error: res?.message || '加载失败' }
  } catch (e) {
    publishFailTipState[id] = { loading: false, loaded: true, lines: [], error: e.message || '加载失败' }
  }
}

const selectedTemuOption = computed(() => {
  const key = selectedTemuKey.value
  if (!key) return null
  return temuOptions.value.find(o => String(o.leafId) === String(key)) || null
})
const editId = ref(null)
const editForm = reactive({
  productName: ''
})

const openEdit = (r) => {
  editId.value = r.id
  editForm.productName = r.productName || ''
  editOpen.value = true
}

const openDetail = (r) => {
  if (!r?.id) return
  const url = `${window.location.origin}/goods/${r.id}`
  window.open(url, '_blank', 'noopener,noreferrer')
}

const saveEdit = async () => {
  if (!editId.value) return
  const title = String(editForm.productName || '').trim()
  if (!title) {
    message.error('标题不能为空')
    return
  }
  saving.value = true
  try {
    const payload = {
      productName: title
    }
    const res = await productCollectionApi.update(editId.value, payload)
    if (res?.success) {
      message.success('已保存')
      editOpen.value = false
      await fetchList()
      return
    }
    message.error(res?.message || '保存失败')
  } catch (e) {
    message.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const doDelete = async (r) => {
  try {
    const res = await productCollectionApi.delete(r.id)
    if (res?.success) {
      message.success('已删除')
      await fetchList()
      return
    }
    message.error(res?.message || '删除失败')
  } catch (e) {
    message.error(e.message || '删除失败')
  }
}

const confirmDelete = (record) => {
  if (!record?.id) return
  Modal.confirm({
    title: '确定删除这条记录？',
    content: '会删除该商品及其所有关联数据，删除后无法恢复。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: () => doDelete(record)
  })
}

const confirmRequeue = (record) => {
  if (!record?.id) return
  const running = record.execStatus === 1
  Modal.confirm({
    title: '退回待执行？',
    content: running
      ? '当前任务处于“执行中”。强制退回后可能会导致重复执行（旧任务可能仍在跑）。确定继续？'
      : '将把执行状态重置为“待执行”，由后台任务重新拉起执行。',
    okText: '退回',
    okType: 'primary',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await productCollectionApi.requeuePostImportTask(record.id, running)
        if (res?.success) {
          message.success('已退回待执行')
          await fetchList()
          return
        }
        message.error(res?.message || '操作失败')
      } catch (e) {
        message.error(e.message || '操作失败')
      }
    }
  })
}

const openTemuMatcher = async (r) => {
  if (!r?.id) return
  matchingId.value = r.id
  selectedTemuKey.value = null
  temuOptions.value = []
  temuRecord.value = r
  try {
    const res = await productCollectionApi.matchTemuCategory(r.id)
    if (res?.success) {
      // backend returns ApiResponse<MatchCategoryResponse>
      const opts = res.data?.options || []
      temuOptions.value = Array.isArray(opts) ? opts : []
      temuModalOpen.value = true
      if (!temuOptions.value.length) {
        message.warning('未匹配到可选类目')
      }
      return
    }
    message.error(res?.message || '匹配失败')
  } catch (e) {
    message.error(e.message || '匹配失败')
  } finally {
    matchingId.value = null
  }
}

const safeJsonParse = (s) => {
  if (!s || typeof s !== 'string') return null
  try {
    return JSON.parse(s)
  } catch {
    return null
  }
}

const attrKey = (p) => {
  // Template may contain duplicate pid values across different templatePid/refPid.
  // Use templatePid as stable unique key for UI value binding.
  if (!p) return ''
  const tp = p.templatePid ?? ''
  const pid = p.pid ?? ''
  return `tp:${tp}|pid:${pid}`
}

const isChildAttr = (p) => {
  return Array.isArray(p?.templatePropertyValueParentList) && p.templatePropertyValueParentList.length > 0
}

const shouldShowTemuAttrItem = (p) => {
  if (!p) return false
  if (!isChildAttr(p)) return true
  // Child attributes should only appear after a valid parent selection exists.
  return childAllowedInfo(p).hasParentSelected
}

const collectSelectedVids = () => {
  const selected = new Set()
  for (const g of (temuAttrGroups.value || [])) {
    const gv = temuAttrValues[attrKey(g)]
    const arr = Array.isArray(gv) ? gv : (gv ? [gv] : [])
    for (const v of arr) {
      if (v === undefined || v === null) continue
      const s = String(v).trim()
      if (s) selected.add(s)
    }
  }
  return selected
}

const findParentPropOfChild = (child) => {
  const tp = child?.parentTemplatePid
  if (!tp) return null
  return (temuAttrGroups.value || []).find(x => String(x?.templatePid ?? '') === String(tp)) || null
}

const childAllowedInfo = (child) => {
  const all = (child?.values || []).map(v => ({ value: String(v.vid), label: v.value }))
  if (!isChildAttr(child)) {
    return { allowed: all, hasParentSelected: true, parentHint: '' }
  }

  const selectedParents = collectSelectedVids()
  const allowed = new Set()
  let hasParentSelected = false
  for (const rule of (child.templatePropertyValueParentList || [])) {
    const parents = Array.isArray(rule?.parentVidList) ? rule.parentVidList : []
    const vids = Array.isArray(rule?.vidList) ? rule.vidList : []
    for (const pv of parents) {
      if (selectedParents.has(String(pv))) {
        hasParentSelected = true
        for (const cv of vids) allowed.add(String(cv))
      }
    }
  }

  // When no parent selection, do not allow picking child values.
  let hint = ''
  if (!hasParentSelected) {
    const parentProp = findParentPropOfChild(child)
    if (parentProp?.values?.length) {
      const parentEnableVids = new Set()
      for (const rule of (child.templatePropertyValueParentList || [])) {
        const parents = Array.isArray(rule?.parentVidList) ? rule.parentVidList : []
        for (const pv of parents) parentEnableVids.add(String(pv))
      }
      const labels = (parentProp.values || [])
        .filter(v => parentEnableVids.has(String(v.vid)))
        .map(v => v.value)
        .filter(Boolean)
      hint = `请先选择父属性：${parentProp.name || '父属性'}${labels.length ? `（可选：${labels.slice(0, 6).join(' / ')}${labels.length > 6 ? ' ...' : ''}）` : ''}`
    } else {
      hint = '请先选择父属性后再填写此项'
    }
  }

  const allowedList = hasParentSelected ? all.filter(o => allowed.has(String(o.value))) : []
  return { allowed: allowedList, hasParentSelected, parentHint: hint }
}

const cleanupInvalidChildSelections = () => {
  const selectedParents = collectSelectedVids()
  for (const p of (temuAttrGroups.value || [])) {
    if (!isChildAttr(p)) continue
    const key = attrKey(p)
    const rawSel = temuAttrValues[key]
    if (rawSel === undefined || rawSel === null) continue

    const allowed = new Set()
    let hasParentSelected = false
    for (const rule of (p.templatePropertyValueParentList || [])) {
      const parents = Array.isArray(rule?.parentVidList) ? rule.parentVidList : []
      const vids = Array.isArray(rule?.vidList) ? rule.vidList : []
      for (const pv of parents) {
        if (selectedParents.has(String(pv))) {
          hasParentSelected = true
          for (const cv of vids) allowed.add(String(cv))
        }
      }
    }

    if (!hasParentSelected) {
      delete temuAttrValues[key]
      continue
    }

    const arr = Array.isArray(rawSel) ? rawSel.map(x => String(x)) : [String(rawSel)]
    const filtered = arr.filter(v => allowed.has(String(v)))
    if (!filtered.length) {
      delete temuAttrValues[key]
      continue
    }
    temuAttrValues[key] = filtered.length > 1 ? filtered : filtered[0]
  }
}

const onTemuAttrChanged = () => {
  cleanupInvalidChildSelections()
  for (const k of Object.keys(temuAttrParentSelectedVids)) delete temuAttrParentSelectedVids[k]
  updateParentSelectionIndex()
}

const updateParentSelectionIndex = () => {
  // Build: parentVid -> Set(childVid)
  const map = {}
  for (const p of (temuAttrGroups.value || [])) {
    if (!isChildAttr(p)) continue
    for (const rule of (p.templatePropertyValueParentList || [])) {
      const parents = Array.isArray(rule?.parentVidList) ? rule.parentVidList : []
      const vids = Array.isArray(rule?.vidList) ? rule.vidList : []
      for (const pv of parents) {
        const k = String(pv)
        if (!map[k]) map[k] = new Set()
        for (const cv of vids) {
          map[k].add(String(cv))
        }
      }
    }
  }

  // Determine selected vids of possible parent attrs (by scanning all attrs current selections)
  for (const p of (temuAttrGroups.value || [])) {
    const key = attrKey(p)
    const raw = temuAttrValues[key]
    const selected = Array.isArray(raw) ? raw.map(v => String(v)) : (raw ? [String(raw)] : [])
    // store for all selected vids so children can reference
    for (const v of selected) {
      if (map[v]) {
        temuAttrParentSelectedVids[v] = true
      }
    }
  }
}

const visibleAttrOptions = (p) => {
  return childAllowedInfo(p).allowed
}

const openTemuAttributes = async (r) => {
  if (!r?.id) return
  temuRecord.value = r
  temuAttrGroups.value = []
  for (const k of Object.keys(temuAttrValues)) delete temuAttrValues[k]
  for (const k of Object.keys(temuAttrParentSelectedVids)) delete temuAttrParentSelectedVids[k]

  // Load existing saved attributes (from detail API)
  try {
    const detail = await productCollectionApi.get(r.id)
    if (detail?.success && detail.data?.temuAttributes) {
      const saved = safeJsonParse(detail.data.temuAttributes)
      if (saved && typeof saved === 'object') {
        const props = Array.isArray(saved.properties) ? saved.properties : []
        if (props.length) {
          for (const p of props) {
            const pid = p?.pid
            const templatePid = p?.templatePid
            if (pid == null) continue
            const key = templatePid ? `tp:${templatePid}|pid:${pid}` : `pid:${pid}`
            if (p.freeText) {
              temuAttrValues[key] = String(p.freeText)
              continue
            }
            const vids = Array.isArray(p.selectedVids) ? p.selectedVids.map(v => String(v)) : []
            if (vids.length > 1) {
              temuAttrValues[key] = vids
            } else if (vids.length === 1) {
              temuAttrValues[key] = vids[0]
            }
          }
        } else {
          const values = saved.values || {}
          for (const [pid, vid] of Object.entries(values)) {
            temuAttrValues[`pid:${pid}`] = vid == null ? '' : String(vid)
          }
        }
      }
    }
  } catch {
    // ignore
  }

  // Fetch attributes options from backend by leaf cat id
  try {
    // Load attribute rules (general + fixed category). Apply before showing.
    let rules = []
    try {
      const leaf = String((r.temuCatid || '').split(',').slice(-1)[0] || '').trim()
      const rr = await productCollectionApi.listTemuAttrRules({ enabled: true })
      if (rr?.success && Array.isArray(rr.data)) {
        rules = rr.data
          .filter(x => x && x.enabled)
          .filter(x => x.ruleType === 'GENERAL' || (x.ruleType === 'FIXED_CATEGORY' && String(x.leafCatId || '').trim() === leaf))
      }
    } catch {
      // ignore
    }

    const res = await productCollectionApi.getTemuCategoryAttributes(r.id)
    if (res?.success && res.data) {
      const obj = safeJsonParse(res.data)
      const props = obj?.result?.properties
      const rawGroups = Array.isArray(props) ? props : []

      // Apply rules by attribute name (Chinese name)
      const byName = new Map()
      for (const rule of rules) {
        const n = String(rule?.attrName || '').trim()
        if (!n) continue
        // later rules override earlier ones
        byName.set(n, rule)
      }

      // attach meta on template property for UI usage
      const nextGroups = []
      for (const p of rawGroups) {
        if (!p || !p.name) continue
        const name = String(p.name).trim()
        const rule = byName.get(name)
        if (rule?.fillMode === 'SKIP') {
          continue
        }
        const copy = { ...p, __rule: rule || null }
        nextGroups.push(copy)
      }
      temuAttrGroups.value = nextGroups

      // Apply FORCE_EMPTY / FIXED_VALUE into current input values (lock later in UI)
      for (const p of temuAttrGroups.value) {
        const rule = p?.__rule
        if (!rule) continue
        const mode = rule.fillMode
        if (mode === 'FORCE_EMPTY') {
          temuAttrValues[attrKey(p)] = ''
        }
        if (mode === 'FIXED_VALUE') {
          const v = String(rule.fixedValue || '')
          // If template expects selection but fixedValue looks like vid, allow user to store as-is.
          // For text input, keep as freeText.
          temuAttrValues[attrKey(p)] = v
        }
      }

      // Cleanup invalid child selections and refresh parent index.
      onTemuAttrChanged()
      temuAttrOpen.value = true
      if (!temuAttrGroups.value.length) {
        message.warning('未返回可填写属性')
      }
      return
    }
    message.error(res?.message || '获取属性失败')
  } catch (e) {
    message.error(e.message || '获取属性失败')
  }
}

const aiFillTemuAttr = async () => {
  const r = temuRecord.value
  if (!r?.id) return
  if (!temuAttrGroups.value.length) {
    message.warning('请先获取属性模板')
    return
  }
  aiFillingTemuAttr.value = true
  try {
    message.loading({ content: 'AI 正在填写（可能需要 1-3 分钟）...', key: 'ai-fill', duration: 0 })
    const res = await productCollectionApi.aiFillTemuAttributes(r.id)
    if (!res?.success) {
      message.error({ content: res?.message || 'AI 填写失败', key: 'ai-fill' })
      return
    }
    const data = res.data || {}
    if (!data.success) {
      message.error({ content: data.errorMsg || 'AI 填写失败', key: 'ai-fill' })
      return
    }

    const filled = Array.isArray(data.properties) ? data.properties : []
    // apply (by pid, choose first matching template property)
    const byPid = new Map()
    for (const g of (temuAttrGroups.value || [])) {
      const pid = g?.pid
      if (pid == null) continue
      const k = String(pid)
      if (!byPid.has(k)) byPid.set(k, [])
      byPid.get(k).push(g)
    }

    for (const p of filled) {
      const pid = p?.pid
      if (pid == null) continue

      // Removed hardcoded exclusions

      const vids = Array.isArray(p.selectedVids) ? p.selectedVids.map(v => String(v)) : []
      const group = (byPid.get(String(pid)) || [])[0]
      const key = group ? attrKey(group) : `pid:${pid}`
      if (p.freeText) {
        temuAttrValues[key] = String(p.freeText)
      } else if (vids.length > 1) {
        temuAttrValues[key] = vids
      } else if (vids.length === 1) {
        temuAttrValues[key] = vids[0]
      }
    }

    // Re-apply rules after AI fill (FORCE_EMPTY / FIXED_VALUE should win)
    for (const g of (temuAttrGroups.value || [])) {
      const rule = g?.__rule
      if (!rule) continue
      if (rule.fillMode === 'FORCE_EMPTY') {
        temuAttrValues[attrKey(g)] = ''
      }
      if (rule.fillMode === 'FIXED_VALUE') {
        temuAttrValues[attrKey(g)] = String(rule.fixedValue || '')
      }
    }

    // Refresh parent-child state and cleanup invalid child selections after AI fills
    onTemuAttrChanged()

    const miss = Array.isArray(data.missingRequiredPids) ? data.missingRequiredPids : []
    const warns = Array.isArray(data.warnings) ? data.warnings : []
    if (miss.length) {
      message.warning({ content: `AI 未能填写部分必填项：${miss.join(', ')}`, key: 'ai-fill' })
    } else if (warns.length) {
      message.info({ content: warns[0], key: 'ai-fill' })
    } else {
      message.success({ content: 'AI 填写完成', key: 'ai-fill' })
    }
  } catch (e) {
    message.error({ content: e.message || 'AI 填写失败/超时', key: 'ai-fill' })
  } finally {
    aiFillingTemuAttr.value = false
  }
}

const saveTemuAttributes = async () => {
  const r = temuRecord.value
  if (!r?.id) return

  // Validation and persistence should honor rules.
  const isSkipByRule = (p) => {
    const r = p?.__rule
    return r && r.fillMode === 'SKIP'
  }
  const isForceEmptyByRule = (p) => {
    const r = p?.__rule
    return r && r.fillMode === 'FORCE_EMPTY'
  }
  const isFixedValueByRule = (p) => {
    const r = p?.__rule
    return r && r.fillMode === 'FIXED_VALUE'
  }

  // validate required
  for (const p of temuAttrGroups.value) {
    if (!p?.required) continue
    // Conditional required: if this is a child attribute but no matching parent is selected,
    // the attribute is not applicable and should not block save.
    if (isChildAttr(p) && !childAllowedInfo(p).hasParentSelected) continue
    if (isSkipByRule(p)) continue
    if (isForceEmptyByRule(p)) continue
    const v = temuAttrValues[attrKey(p)]
    if (v === undefined || v === null || String(v).trim() === '') {
      message.error(`请填写必填属性：${p.name}`)
      return
    }
  }

  savingTemuAttr.value = true
  try {
    const leafCatId = String((r.temuCatid || '').split(',').slice(-1)[0] || '').trim()
    const out = {
      leafCatId,
      savedAt: new Date().toISOString(),
      properties: []
    }

  for (const p of temuAttrGroups.value) {
    const pid = String(p?.pid ?? '')
    if (!pid) continue

    // SKIP: do not persist at all
    if (isSkipByRule(p)) {
      continue
    }

    const rawSel = temuAttrValues[attrKey(p)]

    // FORCE_EMPTY: persist the property but value must be empty string (not 0)
    if (isForceEmptyByRule(p)) {
      out.properties.push({
        pid: p.pid,
        templatePid: p.templatePid,
        refPid: p.refPid,
        valueUnit: Array.isArray(p.valueUnit) && p.valueUnit.length ? String(p.valueUnit[0] ?? '') : '',
        name: p.name,
        required: !!p.required,
        selectedVids: [],
        selectedValues: [],
        freeText: '',
        numberInputValue: ''
      })
      continue
    }

    // FIXED_VALUE: override value and lock
    let effectiveSel = rawSel
    if (isFixedValueByRule(p)) {
      effectiveSel = String(p.__rule?.fixedValue || '')
    }

    const isEmpty =
      effectiveSel === undefined ||
      effectiveSel === null ||
      (Array.isArray(effectiveSel) ? effectiveSel.length === 0 : String(effectiveSel).trim() === '')
      if (isEmpty) continue

      const selectedVids = Array.isArray(effectiveSel) ? effectiveSel.map(x => String(x)) : [String(effectiveSel)]

    // Child attribute validation: if this property has parent rules, only keep selections that satisfy
    // current parent selection. If no matching parent is selected, skip persisting the child.
    let finalSelectedVids = selectedVids
    if (isChildAttr(p)) {
      // Build allowed set from selected parent vids.
      const selectedParents = new Set()
      // scan all current selections as possible parent vids
      for (const g of (temuAttrGroups.value || [])) {
        const gv = temuAttrValues[attrKey(g)]
        const arr = Array.isArray(gv) ? gv : (gv ? [gv] : [])
        for (const v of arr) selectedParents.add(String(v))
      }

      const allowed = new Set()
      let hasParentSelected = false
      for (const rule of (p.templatePropertyValueParentList || [])) {
        const parents = Array.isArray(rule?.parentVidList) ? rule.parentVidList : []
        const vids = Array.isArray(rule?.vidList) ? rule.vidList : []
        for (const pv of parents) {
          if (selectedParents.has(String(pv))) {
            hasParentSelected = true
            for (const cv of vids) allowed.add(String(cv))
          }
        }
      }
      if (hasParentSelected) {
        finalSelectedVids = selectedVids.filter(v => allowed.has(String(v)))
        if (!finalSelectedVids.length) {
          // When filtered to empty, skip persisting this child attribute (prevents parent-child validation errors)
          continue
        }
      } else {
        // No matching parent selected for this child: skip persisting
        continue
      }
    }

    const valueTextMap = new Map((p.values || []).map(v => [String(v.vid), v.value]))
    const finalSelectedValues = (p.values && p.values.length)
      ? finalSelectedVids.map(vid => ({ vid: Number(vid), value: valueTextMap.get(String(vid)) || String(vid) }))
      : []

    out.properties.push({
      pid: p.pid,
      templatePid: p.templatePid,
      refPid: p.refPid,
      valueUnit: Array.isArray(p.valueUnit) && p.valueUnit.length ? String(p.valueUnit[0] ?? '') : '',
      name: p.name,
      required: !!p.required,
      selectedVids: finalSelectedVids,
      selectedValues: finalSelectedValues,
       freeText: (!p.values || !p.values.length) ? (Array.isArray(effectiveSel) ? effectiveSel.join(',') : String(effectiveSel)) : null,
       numberInputValue: (!p.values || !p.values.length) ? (Array.isArray(effectiveSel) ? effectiveSel.join(',') : String(effectiveSel)) : ''
    })
  }

    const res = await productCollectionApi.saveTemuAttributes(r.id, out)
    if (res?.success) {
      message.success('已保存 TEMU 属性')
      temuAttrOpen.value = false
      return
    }
    message.error(res?.message || '保存失败')
  } catch (e) {
    message.error(e.message || '保存失败')
  } finally {
    savingTemuAttr.value = false
  }
}


const saveTemuCategory = async () => {
  const r = temuRecord.value
  const opt = selectedTemuOption.value
  if (!r?.id || !opt) return
  savingTemu.value = true
  try {
    // Save FULL path as required:
    // - temuCatid: comma-separated ids
    // - temuCatname: slash-separated names
    const payload = {
      temuCatid: String(opt.pathIds || opt.leafId),
      temuCatname: String(opt.pathNames || opt.leafName)
    }
    const res = await productCollectionApi.saveTemuCategory(r.id, payload)
    if (res?.success) {
      message.success('已保存 TEMU 类目')
      temuModalOpen.value = false
      await fetchList()
      return
    }
    message.error(res?.message || '保存失败')
  } catch (e) {
    message.error(e.message || '保存失败')
  } finally {
    savingTemu.value = false
  }
}

onMounted(() => {
  fetchTemuCategories()
  fetchList()
})
</script>

<style scoped>
.table-card {
  overflow: hidden;
}

/* Keep page from horizontal scrolling; only table scrolls */
:deep(.ant-table-wrapper) {
  width: 100%;
  max-width: 100%;
}

:deep(.ant-table-content) {
  overflow-x: auto;
  overflow-y: hidden;
}

.range-sep {
  display: inline-flex;
  align-items: center;
  padding: 0 8px;
  color: rgba(15, 23, 42, 0.45);
}

.page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.temu-modal {
  display: grid;
  gap: 14px;
}

.temu-desc {
  padding: 12px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.03);
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.temu-title {
  font-size: 12px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.55);
}

.temu-value {
  margin-top: 6px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.9);
  line-height: 1.4;
}

.temu-path {
  font-weight: 700;
  color: rgba(15, 23, 42, 0.9);
}

.temu-leaf {
  margin-top: 2px;
  font-size: 12px;
  color: rgba(15, 23, 42, 0.55);
}

.temu-empty {
  padding: 12px;
  border-radius: 12px;
  border: 1px dashed rgba(15, 23, 42, 0.18);
  color: rgba(15, 23, 42, 0.6);
}

.temu-attr {
  display: grid;
  gap: 14px;
}

.temu-attr-head {
  padding: 12px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.03);
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.temu-attr-body {
  max-height: 62vh;
  overflow: auto;
  padding-right: 6px;
}


.temu-attr-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: 1fr 1fr 1fr;
}

.temu-attr-item {
  display: grid;
  grid-template-columns: 132px 1fr;
  gap: 8px;
  align-items: center;
  padding: 6px 8px;
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: #fff;
}

.temu-attr-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 800;
  color: rgba(15, 23, 42, 0.9);
  min-width: 0;
}

.name-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.req-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: rgba(239, 68, 68, 0.9);
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.16);
  flex: 0 0 auto;
}

.temu-attr-input :deep(.ant-select-selector) {
  min-height: 30px;
  border-radius: 10px;
}

.temu-attr-input :deep(.ant-input) {
  border-radius: 10px;
}

.temu-attr-input :deep(.ant-select-selection-search-input) {
  height: 28px;
}

.temu-attr-input :deep(.ant-select-selection-overflow) {
  flex-wrap: nowrap;
  overflow: hidden;
}

.temu-attr-input :deep(.ant-select-selection-overflow-item) {
  max-width: 100%;
}

@media (max-width: 1024px) {
  .temu-attr-grid {
    grid-template-columns: 1fr;
  }

  .temu-attr-item {
    grid-template-columns: 1fr;
    align-items: start;
  }
}

.temu-attr-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.9);
}

.req {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(239, 68, 68, 0.12);
  color: rgba(185, 28, 28, 0.95);
  font-weight: 800;
}

.toolbar {
  border-radius: 14px;
}

.table-card {
  border-radius: 14px;
}

.name-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.name-title {
  font-weight: 700;
  color: #0f172a;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.name-sub {
  font-size: 12px;
  color: rgba(15, 23, 42, 0.65);
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}

.pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(15, 23, 42, 0.03);
  color: rgba(15, 23, 42, 0.75);
  font-size: 12px;
  line-height: 1.2;
}

.temu-cat {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.img-empty {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.06);
  display: grid;
  place-items: center;
  color: rgba(15, 23, 42, 0.45);
}

.actions-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.btn-solid {
  border-radius: 10px;
  font-weight: 800;
}

.btn-more {
  border-radius: 10px;
  font-weight: 800;
  color: rgba(15, 23, 42, 0.82);
}

.btn-more:hover {
  border-color: rgba(37, 99, 235, 0.35);
  color: rgba(37, 99, 235, 0.95);
}

.btn-publish {
  border-radius: 10px;
  font-weight: 900;
}

.more-caret {
  margin-left: 6px;
  color: rgba(15, 23, 42, 0.45);
  font-weight: 900;
}

.danger-text {
  color: #ef4444;
  font-weight: 800;
}

.publish-fail-tip {
  max-width: 520px;
}

.tip-line {
  white-space: normal;
  word-break: break-word;
  line-height: 1.35;
}

.drawer-actions {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}
</style>
watch(
  () => ({ ...temuAttrValues }),
  () => {
    // refresh parent-child option filtering when user changes parent selection
    for (const k of Object.keys(temuAttrParentSelectedVids)) delete temuAttrParentSelectedVids[k]
    updateParentSelectionIndex()
  },
  { deep: true }
)
