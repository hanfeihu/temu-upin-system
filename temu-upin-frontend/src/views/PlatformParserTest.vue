<template>
  <ProLayout title="解析器测试">
    <div class="page">
      <a-card :bordered="false" class="intro-card">
        <div class="intro-head">
          <div>
            <div class="title">HTML 解析器对比测试</div>
            <div class="sub">
              左侧测试 1688 解析器，右侧测试 TEMU 解析器。两边都解析成功后，可展开字段对比和原始 JSON 对照，方便快速验证差异。
            </div>
          </div>
          <a-button @click="resetAll">清空</a-button>
        </div>

        <a-alert
          type="info"
          show-icon
          message="建议直接粘贴完整商品详情页 HTML。页面不会自动对比，只有在两侧都完成解析后才会启用“对比结果”。"
        />

        <div class="lookup-row">
          <a-input
            v-model:value="runLookupId"
            allow-clear
            placeholder="输入 Run ID，例如 PTR-7BD97B06AD364B64"
            class="lookup-input"
          />
          <a-button :loading="lookupLoading" @click="loadRunById">按 Run ID 查看样本</a-button>
        </div>
      </a-card>

      <a-row :gutter="16" class="workspace-row">
        <a-col :xs="24" :xl="12">
          <a-card :bordered="false" class="workspace-card">
            <div class="panel-head">
              <div>
                <div class="panel-title">1688 Parser</div>
                <div class="panel-sub">调用 `/platform/parser-test/alibaba1688`，入参固定为 <span class="mono">{ html }</span>。</div>
              </div>
              <a-button type="primary" :loading="left.loading" @click="handleParse('left')">解析 1688</a-button>
            </div>

            <a-form layout="vertical">
              <a-form-item label="1688 HTML">
                <a-textarea
                  v-model:value="left.html"
                  :rows="15"
                  placeholder="粘贴 1688 商品详情页 HTML"
                />
              </a-form-item>
            </a-form>

            <div class="panel-meta">
              <span class="meta-label">状态</span>
              <a-tag :color="statusColor(left)">{{ statusText(left) }}</a-tag>
              <span v-if="left.parsedAt" class="meta-time">{{ left.parsedAt }}</span>
            </div>

            <div v-if="left.runId" class="run-banner">
              <span class="meta-label">Run ID</span>
              <span class="run-id mono">{{ left.runId }}</span>
            </div>

            <a-alert
              v-if="left.error"
              type="error"
              show-icon
              :message="left.error"
              class="state-alert"
            />

            <a-empty v-if="!left.result && !left.loading && !left.error" description="解析结果会显示在这里" />

            <div v-else-if="left.result" class="result-section">
              <div class="result-section-title">解析结果 JSON</div>
              <pre class="json-panel">{{ pretty(left.result) }}</pre>
            </div>
          </a-card>
        </a-col>

        <a-col :xs="24" :xl="12">
          <a-card :bordered="false" class="workspace-card">
            <div class="panel-head">
              <div>
                <div class="panel-title">TEMU Parser</div>
                <div class="panel-sub">调用 `/platform/parser-test/temu`，入参固定为 <span class="mono">{ html }</span>。</div>
              </div>
              <a-button type="primary" :loading="right.loading" @click="handleParse('right')">解析 TEMU</a-button>
            </div>

            <a-form layout="vertical">
              <a-form-item label="TEMU HTML">
                <a-textarea
                  v-model:value="right.html"
                  :rows="15"
                  placeholder="粘贴 TEMU 商品详情页 HTML"
                />
              </a-form-item>
            </a-form>

            <div class="panel-meta">
              <span class="meta-label">状态</span>
              <a-tag :color="statusColor(right)">{{ statusText(right) }}</a-tag>
              <span v-if="right.parsedAt" class="meta-time">{{ right.parsedAt }}</span>
            </div>

            <div v-if="right.runId" class="run-banner">
              <span class="meta-label">Run ID</span>
              <span class="run-id mono">{{ right.runId }}</span>
            </div>

            <a-alert
              v-if="right.error"
              type="error"
              show-icon
              :message="right.error"
              class="state-alert"
            />

            <a-empty v-if="!right.result && !right.loading && !right.error" description="解析结果会显示在这里" />

            <div v-else-if="right.result" class="result-section">
              <div class="result-section-title">解析结果 JSON</div>
              <pre class="json-panel">{{ pretty(right.result) }}</pre>
            </div>
          </a-card>
        </a-col>
      </a-row>

      <a-card v-if="canCompare" :bordered="false" class="compare-entry-card">
        <div class="compare-entry">
          <div>
            <div class="compare-entry-title">两侧解析已完成</div>
            <div class="compare-entry-sub">点击按钮查看关键字段差异和原始 JSON 并排对照。</div>
          </div>
          <a-button type="primary" @click="showComparison = true">对比结果</a-button>
        </div>
      </a-card>

      <template v-if="showComparison && canCompare">
        <a-card :bordered="false" class="compare-card">
          <div class="compare-head">
            <div>
              <div class="panel-title">关键字段对比</div>
              <div class="panel-sub">优先展示内部测试最常看的字段；若字段缺失会直接标记为空。</div>
            </div>
          </div>

          <div class="compare-table">
            <div class="compare-row compare-row-head">
              <div class="compare-cell field-cell">字段</div>
              <div class="compare-cell">1688</div>
              <div class="compare-cell">TEMU</div>
            </div>
            <div
              v-for="item in compareItems"
              :key="item.key"
              class="compare-row"
              :class="{ different: item.different }"
            >
              <div class="compare-cell field-cell">
                <div class="field-label">{{ item.label }}</div>
                <div class="field-key mono">{{ item.key }}</div>
              </div>
              <div class="compare-cell">
                <div class="compare-value">{{ item.leftDisplay }}</div>
              </div>
              <div class="compare-cell">
                <div class="compare-value">{{ item.rightDisplay }}</div>
              </div>
            </div>
          </div>
        </a-card>

        <a-row :gutter="16">
          <a-col :xs="24" :xl="12">
            <a-card :bordered="false" class="compare-card">
              <div class="panel-head compact-head">
                <div>
                  <div class="panel-title">1688 原始 JSON</div>
                  <div class="panel-sub">保留后端返回原始结构，便于直接检查解析内容。</div>
                </div>
              </div>
              <pre class="json-panel compare-json">{{ pretty(left.result) }}</pre>
            </a-card>
          </a-col>
          <a-col :xs="24" :xl="12">
            <a-card :bordered="false" class="compare-card">
              <div class="panel-head compact-head">
                <div>
                  <div class="panel-title">TEMU 原始 JSON</div>
                  <div class="panel-sub">与左侧并排查看，便于定位结构差异和字段缺口。</div>
                </div>
              </div>
              <pre class="json-panel compare-json">{{ pretty(right.result) }}</pre>
            </a-card>
          </a-col>
        </a-row>
      </template>
    </div>
  </ProLayout>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import { parserTestApi } from '@/platform/api/parserTest'

const createParserState = () => ({
  html: '',
  loading: false,
  result: null,
  error: '',
  parsedAt: '',
  runId: ''
})

const left = reactive(createParserState())
const right = reactive(createParserState())
const showComparison = ref(false)
const runLookupId = ref('')
const lookupLoading = ref(false)

const compareFieldMetas = [
  { key: 'productName', label: '商品标题' },
  { key: 'title', label: '标题 title' },
  { key: 'productId', label: '商品 / 产品 ID' },
  { key: 'spuId', label: 'SPU ID' },
  { key: 'skuId', label: 'SKU ID' },
  { key: 'mainImage', label: '主图' },
  { key: 'productMainImage', label: '商品主图字段' },
  { key: 'gallery', label: '图集' },
  { key: 'images', label: '图片数组' },
  { key: 'price', label: '价格' },
  { key: 'salePrice', label: '销售价' },
  { key: 'originPrice', label: '原价' },
  { key: 'currency', label: '币种' },
  { key: 'description', label: '商品描述' },
  { key: 'detailHtml', label: '详情 HTML' },
  { key: 'attributes', label: '属性' },
  { key: 'specs', label: '规格' },
  { key: 'skus', label: 'SKU 列表' },
  { key: 'variants', label: '变体' },
  { key: 'salesCount', label: '销量' },
  { key: 'shopName', label: '店铺名' },
  { key: 'shopId', label: '店铺 ID' },
  { key: 'categoryPath', label: '类目路径' }
]

const canCompare = computed(() => !!left.result && !!right.result)

const pretty = (value) => {
  if (value == null) return ''
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

const formatTime = () => {
  const now = new Date()
  const pad = (value) => String(value).padStart(2, '0')
  return `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

const statusText = (state) => {
  if (state.loading) return '解析中'
  if (state.result) return '已完成'
  if (state.error) return '解析失败'
  return '待解析'
}

const statusColor = (state) => {
  if (state.loading) return 'processing'
  if (state.result) return 'green'
  if (state.error) return 'red'
  return 'default'
}

const resetState = (state) => {
  state.loading = false
  state.result = null
  state.error = ''
  state.parsedAt = ''
  state.runId = ''
}

const normalizeValue = (value) => {
  if (value == null || value === '') return '-'
  if (Array.isArray(value)) return value.length ? JSON.stringify(value) : '-'
  if (typeof value === 'object') return Object.keys(value).length ? JSON.stringify(value) : '-'
  return String(value)
}

const pickFieldValue = (source, key) => {
  if (!source || typeof source !== 'object') return undefined
  return source[key]
}

const compareItems = computed(() => {
  return compareFieldMetas.map((meta) => {
    const leftValue = pickFieldValue(left.result, meta.key)
    const rightValue = pickFieldValue(right.result, meta.key)
    const leftDisplay = normalizeValue(leftValue)
    const rightDisplay = normalizeValue(rightValue)

    return {
      ...meta,
      leftDisplay,
      rightDisplay,
      different: leftDisplay !== rightDisplay
    }
  })
})

const getStateBySide = (side) => (side === 'left' ? left : right)

const applyRunPayload = (state, payload) => {
  state.html = payload?.html || ''
  state.runId = payload?.runId || ''
  state.result = payload?.result ?? null
  state.error = payload?.success === false ? (payload?.errorMessage || '解析失败') : ''
  state.parsedAt = payload?.createdAt ? `最近解析 ${payload.createdAt}` : ''
}

const parseBySide = async (side) => {
  const state = getStateBySide(side)
  const html = String(state.html || '').trim()
  if (!html) {
    message.warning(side === 'left' ? '请先输入 1688 HTML' : '请先输入 TEMU HTML')
    return
  }

  state.loading = true
  state.error = ''
  state.result = null
  state.parsedAt = ''
  state.runId = ''
  if (!canCompare.value) {
    showComparison.value = false
  }

  try {
    const res = side === 'left'
      ? await parserTestApi.parseAlibaba1688(html)
      : await parserTestApi.parseTemu(html)

    if (res?.success) {
      const payload = res.data ?? null
      applyRunPayload(state, payload)
      if (!state.parsedAt) {
        state.parsedAt = `最近解析 ${formatTime()}`
      }
      if (payload?.success === false) {
        message.error(`${side === 'left' ? '1688' : 'TEMU'} 解析失败${state.runId ? ` · RunID ${state.runId}` : ''}`)
        return
      }
      message.success(`${side === 'left' ? '1688' : 'TEMU'} 解析完成${state.runId ? ` · RunID ${state.runId}` : ''}`)
      return
    }

    state.error = res?.message || '解析失败'
    message.error(state.error)
  } catch (error) {
    state.error = error.message || '解析失败'
    message.error(state.error)
  } finally {
    state.loading = false
  }
}

const handleParse = (side) => {
  parseBySide(side)
}

const loadRunById = async () => {
  const runId = String(runLookupId.value || '').trim()
  if (!runId) {
    message.warning('请先输入 Run ID')
    return
  }

  lookupLoading.value = true
  try {
    const res = await parserTestApi.getRun(runId)
    if (res?.success) {
      const payload = res.data ?? null
      const target = payload?.parserType === 'TEMU' ? right : left
      resetState(target)
      applyRunPayload(target, payload)
      showComparison.value = canCompare.value
      message.success(`已加载样本 ${runId}`)
      return
    }
    message.error(res?.message || '加载样本失败')
  } catch (error) {
    message.error(error.message || '加载样本失败')
  } finally {
    lookupLoading.value = false
  }
}

const resetAll = () => {
  left.html = ''
  right.html = ''
  runLookupId.value = ''
  resetState(left)
  resetState(right)
  showComparison.value = false
}
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.intro-card,
.workspace-card,
.compare-entry-card,
.compare-card {
  border-radius: 16px;
}

.intro-head,
.panel-head,
.compare-entry,
.compare-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.intro-head {
  margin-bottom: 16px;
}

.lookup-row {
  margin-top: 16px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.lookup-input {
  max-width: 420px;
}

.title,
.panel-title,
.compare-entry-title {
  color: #0f172a;
  font-weight: 800;
}

.title {
  font-size: 20px;
}

.panel-title,
.compare-entry-title {
  font-size: 16px;
}

.sub,
.panel-sub,
.compare-entry-sub,
.meta-time,
.field-key {
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.workspace-row {
  margin-bottom: 0;
}

.panel-head {
  margin-bottom: 16px;
}

.compact-head {
  margin-bottom: 12px;
}

.panel-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.run-banner {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding: 8px 12px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid rgba(148, 163, 184, 0.24);
}

.run-id {
  color: #0f172a;
  font-weight: 700;
}

.meta-label,
.field-label {
  color: #334155;
  font-weight: 600;
}

.state-alert {
  margin-bottom: 16px;
}

.result-section-title {
  margin-bottom: 10px;
  color: #334155;
  font-weight: 700;
}

.json-panel {
  margin: 0;
  padding: 16px;
  border-radius: 14px;
  background: #0f172a;
  color: rgba(248, 250, 252, 0.96);
  font-size: 12px;
  line-height: 1.7;
  overflow: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}

.compare-entry {
  align-items: center;
}

.compare-table {
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 14px;
  overflow: hidden;
}

.compare-row {
  display: grid;
  grid-template-columns: minmax(220px, 1.1fr) minmax(0, 1fr) minmax(0, 1fr);
  background: rgba(255, 255, 255, 0.9);
}

.compare-row + .compare-row {
  border-top: 1px solid rgba(148, 163, 184, 0.18);
}

.compare-row-head {
  background: #f8fafc;
}

.compare-row.different {
  background: rgba(254, 242, 242, 0.72);
}

.compare-cell {
  padding: 14px 16px;
  min-width: 0;
}

.compare-cell + .compare-cell {
  border-left: 1px solid rgba(148, 163, 184, 0.18);
}

.field-cell {
  background: rgba(248, 250, 252, 0.82);
}

.compare-value {
  color: #0f172a;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.compare-json {
  min-height: 420px;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}

@media (max-width: 1200px) {
  .compare-row {
    grid-template-columns: minmax(180px, 1fr) minmax(0, 1fr) minmax(0, 1fr);
  }
}

@media (max-width: 900px) {
  .compare-row {
    grid-template-columns: 1fr;
  }

  .compare-cell + .compare-cell {
    border-left: 0;
    border-top: 1px solid rgba(148, 163, 184, 0.18);
  }
}
</style>
