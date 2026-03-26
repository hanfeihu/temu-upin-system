<template>
  <ProLayout title="TEMU 标题优化">
    <div class="page">
      <a-row :gutter="16">
        <a-col :xs="24" :lg="11">
          <a-card class="panel" :bordered="false">
            <div class="panel-head">
              <div>
                <div class="panel-title">选择商品</div>
                <div class="panel-sub">按商品标题搜索，选择一个 spu 后调用后端封装的方法。</div>
              </div>
            </div>

            <div class="toolbar">
              <a-input
                v-model:value="q"
                placeholder="输入商品标题关键词"
                allow-clear
                @pressEnter="search"
              />
              <a-button @click="search" :loading="loading">搜索</a-button>
            </div>

            <a-table
              rowKey="id"
              size="small"
              :columns="columns"
              :dataSource="rows"
              :loading="loading"
              :pagination="false"
              :rowSelection="rowSelection"
              :scroll="{ y: 560 }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'img'">
                  <a-image
                    v-if="record.productMainImage"
                    :src="record.productMainImage"
                    :width="52"
                    :height="52"
                    :preview="true"
                  />
                  <span v-else>-</span>
                </template>
                <template v-else-if="column.key === 'title'">
                  <div class="title-cell">
                    <div class="title-text">{{ record.productName || '-' }}</div>
                    <div class="title-meta">spuId: {{ record.id }}</div>
                  </div>
                </template>
              </template>
            </a-table>

            <div class="pager">
              <a-pagination
                :current="page + 1"
                :pageSize="size"
                :total="total"
                show-size-changer
                :pageSizeOptions="['10', '20', '50']"
                @change="onPageChange"
                @showSizeChange="onPageSizeChange"
              />
            </div>
          </a-card>
        </a-col>

        <a-col :xs="24" :lg="13">
          <a-card class="panel" :bordered="false">
            <div class="panel-head result-head">
              <div>
                <div class="panel-title">生成结果</div>
                <div class="panel-sub">会把三个字段写回数据库；如果类目匹配成功，也会同步写回 Temu 类目。</div>
              </div>
              <a-space>
                <a-button v-if="selectedId" @click="openDetail">查看商品详情</a-button>
                <a-button type="primary" :disabled="!selectedId" :loading="generating" @click="generate">
                  生成并保存
                </a-button>
              </a-space>
            </div>

            <div v-if="!selectedId" class="empty-block">先在左侧选择一个商品。</div>

            <template v-else>
              <div class="selected-brief" v-if="selectedRecord">
                <div class="selected-title">{{ selectedRecord.productName || '-' }}</div>
                <div class="selected-meta">spuId {{ selectedRecord.id }}</div>
              </div>

              <div v-if="result" class="result-wrap">
                <div class="result-grid">
                  <div class="result-card">
                    <div class="result-label">优化后的英文标题</div>
                    <div class="result-value">{{ result.optimizedTitleEn || '-' }}</div>
                  </div>
                  <div class="result-card">
                    <div class="result-label">优化后的中文标题</div>
                    <div class="result-value">{{ result.optimizedTitleZh || '-' }}</div>
                  </div>
                  <div class="result-card result-card-wide">
                    <div class="result-label">类目关键词</div>
                    <div class="result-value">{{ result.categoryKeywords || '-' }}</div>
                  </div>
                </div>

                <a-descriptions bordered size="small" :column="1" class="meta-box">
                  <a-descriptions-item label="匹配状态">
                    <a-tag :color="result.categoryMatched ? 'green' : 'orange'">
                      {{ result.categoryMatched ? '已匹配到类目' : '未匹配到类目' }}
                    </a-tag>
                  </a-descriptions-item>
                  <a-descriptions-item label="最终匹配词">{{ result.matchedKeyword || '-' }}</a-descriptions-item>
                  <a-descriptions-item label="Temu 类目">{{ result.matchedTemuCatname || '-' }}</a-descriptions-item>
                  <a-descriptions-item label="Temu 类目ID">{{ result.matchedTemuCatid || '-' }}</a-descriptions-item>
                  <a-descriptions-item label="尝试次数">{{ result.attemptCount ?? '-' }}</a-descriptions-item>
                  <a-descriptions-item label="失败关键词">
                    <span v-if="failedKeywordText">{{ failedKeywordText }}</span>
                    <span v-else>-</span>
                  </a-descriptions-item>
                  <a-descriptions-item v-if="result.errorMsg" label="错误信息">{{ result.errorMsg }}</a-descriptions-item>
                </a-descriptions>
              </div>

              <div v-else class="empty-block">点击“生成并保存”后会在这里显示结果。</div>
            </template>
          </a-card>
        </a-col>
      </a-row>
    </div>
  </ProLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import ProLayout from '@/platform/components/ProLayout.vue'
import { productCollectionApi } from '@/platform/api/productCollections'

const router = useRouter()

const loading = ref(false)
const generating = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(0)
const size = ref(20)
const q = ref('')
const selectedId = ref(null)
const result = ref(null)

const columns = [
  { title: '主图', key: 'img', width: 80 },
  { title: '商品', key: 'title' },
  { title: 'Temu类目', dataIndex: 'temuCatname', key: 'temuCatname', width: 260, ellipsis: true }
]

const rowSelection = computed(() => ({
  type: 'radio',
  selectedRowKeys: selectedId.value ? [selectedId.value] : [],
  onChange: (keys) => {
    selectedId.value = Array.isArray(keys) && keys.length ? keys[0] : null
    result.value = null
  }
}))

const selectedRecord = computed(() => rows.value.find(item => item.id === selectedId.value) || null)

const failedKeywordText = computed(() => {
  const list = Array.isArray(result.value?.failedKeywords) ? result.value.failedKeywords.filter(Boolean) : []
  return list.join(' | ')
})

const search = async () => {
  loading.value = true
  try {
    const res = await productCollectionApi.list({
      q: (q.value || '').trim() || undefined,
      page: page.value,
      size: size.value
    })
    if (res?.success) {
      rows.value = Array.isArray(res.data?.content) ? res.data.content : []
      total.value = Number(res.data?.totalElements || 0)
      if (selectedId.value && !rows.value.some(item => item.id === selectedId.value)) {
        selectedId.value = null
        result.value = null
      }
      return
    }
    message.error(res?.message || '搜索失败')
  } catch (e) {
    message.error(e.message || '搜索失败')
  } finally {
    loading.value = false
  }
}

const generate = async () => {
  if (!selectedId.value) {
    message.warning('请先选择一个商品')
    return
  }
  generating.value = true
  try {
    const res = await productCollectionApi.generateTemuTitleOptimization(selectedId.value)
    if (res?.success) {
      result.value = res.data || null
      const matched = !!res.data?.categoryMatched
      message.success(matched ? '已生成并匹配到类目' : '已生成并保存，暂未匹配到类目')
      await search()
      return
    }
    message.error(res?.message || '生成失败')
  } catch (e) {
    message.error(e.message || '生成失败')
  } finally {
    generating.value = false
  }
}

const openDetail = () => {
  if (!selectedId.value) {
    return
  }
  router.push(`/platform/product-collections/${selectedId.value}`)
}

const onPageChange = (p) => {
  page.value = Math.max(0, Number(p || 1) - 1)
  search()
}

const onPageSizeChange = (_page, pageSize) => {
  size.value = Number(pageSize || 20)
  page.value = 0
  search()
}

onMounted(search)
</script>

<style scoped>
.page {
  padding: 16px;
}

.panel {
  min-height: calc(100vh - 128px);
  border-radius: 18px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}

.panel-title {
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.panel-sub {
  margin-top: 4px;
  font-size: 13px;
  color: #64748b;
  line-height: 1.6;
}

.toolbar {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  margin-bottom: 14px;
}

.title-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.title-text {
  font-weight: 700;
  color: #0f172a;
}

.title-meta {
  font-size: 12px;
  color: #64748b;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.result-head {
  margin-bottom: 22px;
}

.selected-brief {
  margin-bottom: 16px;
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(239, 246, 255, 0.96), rgba(240, 253, 250, 0.96));
  border: 1px solid rgba(125, 211, 252, 0.3);
}

.selected-title {
  font-size: 16px;
  font-weight: 800;
  color: #0f172a;
}

.selected-meta {
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
}

.result-wrap {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.result-card {
  padding: 16px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.result-card-wide {
  grid-column: 1 / -1;
}

.result-label {
  font-size: 12px;
  font-weight: 900;
  color: #64748b;
  letter-spacing: 0.04em;
}

.result-value {
  margin-top: 10px;
  font-size: 14px;
  line-height: 1.7;
  color: #0f172a;
  font-weight: 700;
  word-break: break-word;
}

.meta-box {
  border-radius: 16px;
  overflow: hidden;
}

.empty-block {
  min-height: 240px;
  display: grid;
  place-items: center;
  border-radius: 18px;
  border: 1px dashed #cbd5e1;
  color: #64748b;
  background: #f8fafc;
}

@media (max-width: 992px) {
  .panel {
    min-height: auto;
  }

  .result-grid {
    grid-template-columns: 1fr;
  }

  .toolbar {
    grid-template-columns: 1fr;
  }

  .panel-head {
    flex-direction: column;
  }
}
</style>