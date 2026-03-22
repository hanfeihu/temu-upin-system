<template>
  <ProLayout title="尺码调试">
    <div class="page">
      <a-card :bordered="false" class="card">
        <div class="head">
          <div>
            <div class="title">TEMU 尺码模板调试</div>
            <div class="sub">按商品逐步查看 `class.get`、`meta.get`、`settings.get` 和 `sizecharts.create` 尝试结果。</div>
          </div>
          <a-space wrap>
            <a-input v-model:value="productId" placeholder="商品ID，例如 145" style="width: 180px" allow-clear @pressEnter="reload" />
            <a-input v-model:value="sizeValue" placeholder="尺码值，例如 110" style="width: 180px" allow-clear @pressEnter="reload" />
            <a-button type="primary" :loading="loading" @click="reload">开始调试</a-button>
          </a-space>
        </div>

        <a-alert
          v-if="result"
          type="info"
          show-icon
          :message="`当前商品 ${result.productId || '-'}，类目叶子节点 ${result.leafCatId || '-'}，当前输入尺码值 ${result.sizeValue || '-'}`"
          style="margin-bottom: 16px"
        />

        <div v-if="result" class="grid">
          <a-card size="small" title="商品 / 凭证">
            <div class="kv"><span>商品ID</span><span class="mono">{{ result.productId || '-' }}</span></div>
            <div class="kv"><span>类目ID</span><span class="mono">{{ result.temuCatid || '-' }}</span></div>
            <div class="kv"><span>类目名称</span><span>{{ result.temuCatname || '-' }}</span></div>
            <div class="kv"><span>店铺ID</span><span class="mono">{{ result.shopId || '-' }}</span></div>
            <div class="kv"><span>AppKey</span><span class="mono">{{ result.appKey || '-' }}</span></div>
          </a-card>

          <a-card size="small" title="class.get">
            <div class="kv"><span>success</span><span>{{ yesNo(result.classRespSuccess) }}</span></div>
            <div class="kv"><span>errorCode</span><span class="mono">{{ result.classRespCode ?? '-' }}</span></div>
            <div class="kv"><span>errorMsg</span><span>{{ result.classRespMsg || '-' }}</span></div>
            <pre class="pre">{{ pretty(result.classInfo) }}</pre>
          </a-card>

          <a-card size="small" title="meta.get">
            <div class="kv"><span>success</span><span>{{ yesNo(result.metaRespSuccess) }}</span></div>
            <div class="kv"><span>errorCode</span><span class="mono">{{ result.metaRespCode ?? '-' }}</span></div>
            <div class="kv"><span>errorMsg</span><span>{{ result.metaRespMsg || '-' }}</span></div>
            <div class="tip">先看 `groupList` 和 `elementList`，确认哪些元信息能进 `content.meta`。</div>
          </a-card>

          <a-card size="small" title="settings.get">
            <div class="kv"><span>success</span><span>{{ yesNo(result.settingsRespSuccess) }}</span></div>
            <div class="kv"><span>errorCode</span><span class="mono">{{ result.settingsRespCode ?? '-' }}</span></div>
            <div class="kv"><span>errorMsg</span><span>{{ result.settingsRespMsg || '-' }}</span></div>
            <div class="kv"><span>groupChName</span><span>{{ result.settingsGroupChName || '-' }}</span></div>
            <div class="kv"><span>groupEnName</span><span>{{ result.settingsGroupEnName || '-' }}</span></div>
            <div class="tip">`sizeList` 像是页面可勾选的合法尺码值集合，`mappingContent` 更像标准样例。</div>
          </a-card>
        </div>

        <a-card v-if="result?.settingsSizeList?.length" size="small" title="可选尺码值" style="margin-top: 16px">
          <div class="tag-wrap">
            <a-tag v-for="item in result.settingsSizeList" :key="item" color="blue">{{ item }}</a-tag>
          </div>
        </a-card>

        <a-card v-if="result" size="small" title="单接口原始调试" style="margin-top: 16px">
          <div class="sub" style="margin-bottom: 12px">先点上面的“开始调试”拿到商品凭证，再手动改每个接口入参。页面直接展示接口原始返回值，不做业务加工。</div>
          <div class="query-grid single-grid">
            <a-card size="small" :title="singleClassItem.title">
              <a-space direction="vertical" style="width:100%">
                <div class="api-name">{{ singleClassItem.apiType }}</div>
                <a-input v-model:value="queryForms.classGet.catId" placeholder="叶子类目ID，例如 35082" />
                <a-input v-model:value="queryForms.classGet.classId" placeholder="可选 classId，例如 125" />
                <a-button type="primary" :loading="queryLoading.classGet" @click="runQuery('classGet')">执行 class.get</a-button>
                <div v-if="queryResults.classGet" class="query-result">
                  <div class="query-title">实际入参</div>
                  <pre class="pre small">{{ pretty(queryResults.classGet.effectiveParams) }}</pre>
                  <div class="query-title">原始返回</div>
                  <pre class="pre small raw-pre">{{ rawText(queryResults.classGet.raw) }}</pre>
                </div>
              </a-space>
            </a-card>

            <a-card v-for="item in extraQueryItems" :key="item.key" size="small" :title="item.title">
              <a-space direction="vertical" style="width:100%">
                <div class="api-name">{{ item.apiType }}</div>
                <template v-for="field in item.fields" :key="field.key">
                  <a-input
                    v-if="field.key !== 'pageSize' && field.key !== 'offset'"
                    v-model:value="queryForms[item.key][field.key]"
                    :placeholder="field.placeholder"
                  />
                  <a-input-number
                    v-else
                    v-model:value="queryForms[item.key][field.key]"
                    :placeholder="field.placeholder"
                    style="width:100%"
                  />
                </template>
                <a-button type="primary" :loading="queryLoading[item.key]" @click="runQuery(item.key)">执行 {{ item.title }}</a-button>
                <div v-if="queryResults[item.key]" class="query-result">
                  <div class="query-title">实际入参</div>
                  <pre class="pre small">{{ pretty(queryResults[item.key].effectiveParams) }}</pre>
                  <div class="query-title">原始返回</div>
                  <pre class="pre small raw-pre">{{ rawText(queryResults[item.key].raw) }}</pre>
                </div>
              </a-space>
            </a-card>
          </div>
        </a-card>

        <a-card v-if="attempts.length" size="small" title="创建模板尝试" style="margin-top: 16px">
          <a-table :columns="columns" :dataSource="attempts" :pagination="false" size="small" rowKey="name">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'success'">
                <a-tag :color="record.success ? 'green' : 'red'">{{ record.success ? '成功' : '失败' }}</a-tag>
              </template>
              <template v-else-if="column.key === 'code'">
                <span class="mono">{{ record.errorCode ?? '-' }}</span>
              </template>
              <template v-else-if="column.key === 'msg'">
                <div>{{ record.errorMsg || '-' }}</div>
                <div class="req mono">{{ record.requestId || '' }}</div>
              </template>
              <template v-else-if="column.key === 'payload'">
                <pre class="pre small">{{ pretty(record.payload) }}</pre>
              </template>
            </template>
          </a-table>
        </a-card>

        <a-empty v-if="!loading && !result" description="输入商品ID后开始调试" style="margin-top: 24px" />
      </a-card>
    </div>
  </ProLayout>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import ProLayout from '@/platform/components/ProLayout.vue'
import { productCollectionApi } from '@/platform/api/productCollections'

const route = useRoute()
const router = useRouter()

const productId = ref('')
const sizeValue = ref('110')
const loading = ref(false)
const result = ref(null)
const queryLoading = ref({ classGet: false, metaGet: false, settingsGet: false, listGet: false })
const queryResults = ref({})
const queryForms = ref({
  classGet: { catId: '', classId: '' },
  metaGet: { catId: '', classId: '' },
  settingsGet: { catId: '', classId: '' },
  listGet: { catId: '', offset: 0, pageSize: 20 }
})

const attempts = computed(() => Array.isArray(result.value?.attempts) ? result.value.attempts : [])

const columns = [
  { title: '尝试名', dataIndex: 'name', key: 'name', width: 240 },
  { title: '结果', key: 'success', width: 90 },
  { title: '错误码', key: 'code', width: 100 },
  { title: '错误信息 / requestId', key: 'msg', width: 240 },
  { title: 'Payload', key: 'payload' }
]

const queryItems = [
  {
    key: 'classGet',
    title: 'class.get',
    apiType: 'class.get',
    fields: [
      { key: 'catId', placeholder: '叶子类目ID' },
      { key: 'classId', placeholder: '可选 classId' }
    ]
  },
  {
    key: 'metaGet',
    title: 'meta.get',
    apiType: 'meta.get',
    fields: [
      { key: 'catId', placeholder: '叶子类目ID' },
      { key: 'classId', placeholder: '尺码分类ID' }
    ]
  },
  {
    key: 'settingsGet',
    title: 'settings.get',
    apiType: 'settings.get',
    fields: [
      { key: 'catId', placeholder: '叶子类目ID' },
      { key: 'classId', placeholder: '尺码分类ID' }
    ]
  },
  {
    key: 'listGet',
    title: 'sizecharts.get',
    apiType: 'list',
    fields: [
      { key: 'catId', placeholder: '叶子类目ID' },
      { key: 'offset', placeholder: 'offset' },
      { key: 'pageSize', placeholder: 'pageSize' }
    ]
  }
]

const singleClassItem = queryItems[0]
const extraQueryItems = queryItems.slice(1)

const yesNo = (v) => (v ? '是' : '否')

const pretty = (v) => {
  if (v == null) return ''
  try {
    return JSON.stringify(v, null, 2)
  } catch {
    return String(v)
  }
}

const prettyRaw = (v) => {
  if (!v) return ''
  try {
    return JSON.stringify(JSON.parse(String(v)), null, 2)
  } catch {
    return String(v)
  }
}

const rawText = (v) => (v == null ? '' : String(v))

const initQueryForms = (data) => {
  const leafCatId = data?.leafCatId || ''
  const classId = data?.classInfo?.classId || ''
  queryForms.value = {
    classGet: { catId: String(leafCatId || ''), classId: classId ? String(classId) : '' },
    metaGet: { catId: String(leafCatId || ''), classId: classId ? String(classId) : '' },
    settingsGet: { catId: String(leafCatId || ''), classId: classId ? String(classId) : '' },
    listGet: { catId: String(leafCatId || ''), offset: 0, pageSize: 20 }
  }
}

const syncQuery = () => {
  productId.value = String(route.query.id || productId.value || '')
  sizeValue.value = String(route.query.sizeValue || sizeValue.value || '110')
}

const reload = async () => {
  const id = String(productId.value || '').trim()
  const size = String(sizeValue.value || '').trim()
  if (!id) {
    message.warning('请先输入商品ID')
    return
  }
  loading.value = true
  try {
    router.replace({ query: { ...route.query, id, sizeValue: size || undefined } })
    const res = await productCollectionApi.debugTemuSizeChart(id, size)
    if (res?.success) {
      result.value = res.data || null
      initQueryForms(result.value)
      return
    }
    message.error(res?.message || '调试失败')
  } catch (e) {
    message.error(e.message || '调试失败')
  } finally {
    loading.value = false
  }
}

const runQuery = async (key) => {
  const id = String(productId.value || '').trim()
  if (!id) {
    message.warning('请先输入商品ID并开始调试')
    return
  }
  const config = queryItems.find(it => it.key === key)
  if (!config) return
  queryLoading.value = { ...queryLoading.value, [key]: true }
  try {
    const form = { ...(queryForms.value[key] || {}) }
    const payload = { apiType: config.apiType }
    Object.keys(form).forEach(k => {
      const v = form[k]
      if (v !== '' && v != null) payload[k] = v
    })
    const res = await productCollectionApi.debugTemuSizeChartQuery(id, payload)
    if (res?.success) {
      queryResults.value = { ...queryResults.value, [key]: res.data || null }
      return
    }
    message.error(res?.message || `执行 ${config.title} 失败`)
  } catch (e) {
    message.error(e.message || `执行 ${config.title} 失败`)
  } finally {
    queryLoading.value = { ...queryLoading.value, [key]: false }
  }
}

watch(() => route.query, syncQuery, { deep: true })

onMounted(async () => {
  syncQuery()
  if (productId.value) {
    await reload()
  }
})
</script>

<style scoped>
.page {
  padding: 16px;
}

.card {
  border-radius: 18px;
}

.head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.title {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.sub {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.kv {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  color: #334155;
}

.tip {
  margin-top: 10px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.pre {
  margin: 8px 0 0;
  padding: 10px 12px;
  background: #0f172a;
  color: #dbeafe;
  border-radius: 12px;
  white-space: pre-wrap;
  word-break: break-word;
  overflow: auto;
}

.pre.small {
  max-height: 240px;
  font-size: 12px;
}

.req {
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
}

.tag-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.query-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.single-grid {
  grid-template-columns: 1fr;
}

.api-name {
  font-size: 12px;
  color: #64748b;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.query-result {
  margin-top: 8px;
}

.query-title {
  margin-top: 8px;
  margin-bottom: 6px;
  font-size: 12px;
  color: #64748b;
}

.raw-pre {
  background: #111827;
  color: #e5eefb;
}

@media (max-width: 1100px) {
  .grid {
    grid-template-columns: 1fr;
  }

  .query-grid {
    grid-template-columns: 1fr;
  }
}
</style>
