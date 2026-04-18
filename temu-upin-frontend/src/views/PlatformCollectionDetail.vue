<template>
  <div class="pc-page">
    <div class="container">
      <div class="pc-head">
        <button class="head-btn" type="button" @click="goBack">
          <LeftOutlined />
          返回
        </button>
        <div class="head-right">
          <a-button @click="openSplitModal" :disabled="!canSplitProduct">商品拆分</a-button>
          <a-button :loading="swappingImages" @click="swapCarouselAndDetail">轮播详情图交换</a-button>
          <a-button :loading="replacingKwcdn" @click="replaceImagesToKwcdn">一键替换图片链接</a-button>
          <a-button :loading="batchTranslatingImages" @click="translateAllImages">一键翻译所有图片</a-button>
          <a-button :loading="normalizingImages" @click="normalizeAllImagesTo800">一键规范化图片</a-button>
          <a-button @click="go('/ai')">AI 做图</a-button>
          <a-button @click="openUrl(pc.productUrl)" :disabled="!pc.productUrl">打开链接</a-button>
          <a-button type="primary" @click="openRaw">全部字段</a-button>
        </div>
      </div>

      <div class="product-grid">
        <div class="media-col">
          <div class="company-strip" v-if="pc.companyName">
            <div class="company-name">
              <BankOutlined />
              {{ pc.companyName }}
            </div>
            <div class="company-meta">
              <span class="meta-item">
                <EnvironmentOutlined />
                {{ pc.shippingLocation || '-' }}
              </span>
              <span class="meta-item">
                <LineChartOutlined />
                年销 {{ pc.annualSales || '-' }}
              </span>
            </div>
            <div class="service-scores">
              <span>
                <RedoOutlined style="color: #3b82f6" />
                回头率 {{ pc.repeatCustomerRate ?? '-' }}%
              </span>
              <span>
                <StarOutlined style="color: #eab308" />
                服务 {{ toFixed(pc.serviceScore, 1) }}
              </span>
            </div>
          </div>

          <div class="media-grid">
            <div class="thumb-col" v-if="galleryImages.length || pc.carouselVideo">
              <button
                v-if="pc.carouselVideo"
                class="thumbnail-item video-thumb"
                :class="{ active: showVideo }"
                type="button"
                @click="selectVideo"
                title="点击播放视频"
              >
                <template v-if="videoPoster">
                  <img :src="videoPoster" alt="video cover" loading="lazy" />
                  <span class="video-play" aria-hidden="true">
                    <PlayCircleOutlined />
                  </span>
                </template>
                <template v-else>
                  <PlayCircleOutlined />
                  <span>视频</span>
                </template>
              </button>

                <div
                  v-for="(imgUrl, idx) in galleryImages"
                  :key="imgUrl + idx"
                  class="thumbnail-item"
                  :class="{ active: !showVideo && idx === activeIndex }"
                  :title="'图片 ' + (idx + 1)"
                >
                  <button class="thumb-hit" type="button" @click="selectImage(idx)">
                    <img :src="imgUrl" alt="thumb" loading="lazy" />
                  </button>
                </div>
            </div>

            <div class="main-col">
              <div class="main-image-container" v-if="!showVideo">
                <img v-if="activeImage" :src="activeImage" alt="product" @error="onMainImgError" />
                <div v-else class="img-empty">暂无图片</div>

                <div v-if="activeImage" class="main-actions">
                  <button
                    class="main-action"
                    type="button"
                    title="图片融合"
                    @click.stop="openFusion()"
                  >
                    <AppstoreOutlined />
                  </button>
                  <button
                    class="main-action"
                    type="button"
                    title="翻译"
                    @click.stop="openTranslate('carousel', activeIndex, activeImage)"
                  >
                    <GlobalOutlined />
                  </button>
                  <button
                    v-if="isDeletableCarouselImage(activeImage)"
                    class="main-action danger"
                    type="button"
                    title="删除"
                    @click.stop="deleteCarouselImage(activeImage)"
                  >
                    <DeleteOutlined />
                  </button>
                </div>
              </div>
              <div class="video-container" v-else>
                <video :src="pc.carouselVideo" controls preload="metadata" :poster="videoPoster" />
              </div>
            </div>
          </div>
        </div>

        <div class="info-col">
          <div class="product-title">{{ pc.productName || '-' }}</div>

          <div v-if="(pc.targetShopNames || []).length" class="shop-row">
            <span class="cat-label">采集店铺</span>
            <div class="shop-tags">
              <a-tag v-for="shopName in pc.targetShopNames" :key="shopName" color="blue">
                {{ shopName }}
              </a-tag>
            </div>
          </div>

          <div class="category-row">
            <span class="cat-label">原始类目</span>
            <span class="cat-value">{{ pc.originalCategory || '-' }}</span>
          </div>

          <div v-if="pc.temuCatname" class="category-row">
            <span class="cat-label">Temu类目</span>
            <span class="cat-value">{{ pc.temuCatname }}</span>
          </div>

          <div v-if="pc.temuOptimizedTitleEn || pc.temuOptimizedTitleZh || pc.temuCategoryKeywords" class="ai-title-card">
            <div class="ai-title-head">AI 标题与类目关键词</div>
            <div v-if="pc.temuOptimizedTitleEn" class="ai-title-row">
              <span class="ai-title-label">英文标题</span>
              <span class="ai-title-value">{{ pc.temuOptimizedTitleEn }}</span>
            </div>
            <div v-if="pc.temuOptimizedTitleZh" class="ai-title-row">
              <span class="ai-title-label">中文标题</span>
              <span class="ai-title-value">{{ pc.temuOptimizedTitleZh }}</span>
            </div>
            <div v-if="pc.temuCategoryKeywords" class="ai-title-row">
              <span class="ai-title-label">类目关键词</span>
              <span class="ai-title-value">{{ pc.temuCategoryKeywords }}</span>
            </div>
          </div>

          

          <div class="price-section">
            <div class="price-range">
              <span class="label">价格区间</span>
              <span class="value">{{ priceRangeText }}</span>
              <span class="unit">/个</span>
            </div>
              <div class="moq-info">
              <div><span>起订量</span> {{ pc.moq ?? '-' }} 个</div>
              <div><span>库存</span> {{ totalStockText }}</div>
            </div>
          </div>

          <div class="sku-selector" v-if="skuProps.length">
            <div class="sku-title">
              <AppstoreOutlined />
              规格选择
            </div>

            <div class="sku-props">
              <div class="sku-prop" v-for="p in skuProps" :key="p.name">
                <div class="sku-prop-name">{{ p.name }}</div>
                <div class="sku-prop-values">
                  <button
                    v-for="v in (p.values || [])"
                    :key="v.name"
                    class="sku-value"
                    :class="{ active: selectedSku[p.name] === v.name }"
                    type="button"
                    @click="onSelectSkuValue(p.name, v.name)"
                  >
                    <img v-if="v.image" :src="v.image" alt="" loading="lazy" />
                    <span>{{ v.name }}</span>
                  </button>
                </div>
              </div>
            </div>

            <div class="sku-selected" v-if="selectedSkuKey">
              <div class="sku-selected-row">
                <span class="k">当前</span>
                <span class="v">{{ selectedSkuKey }}</span>
              </div>
              <div class="sku-selected-row">
                <span class="k">skuId</span>
                <span class="v">{{ selectedSkuRow?.skuId || '-' }}</span>
              </div>
              <div class="sku-selected-row">
                <span class="k">库存</span>
                <span class="v">{{ selectedSkuRow?.stock ?? '-' }}</span>
              </div>
              <div class="sku-selected-row">
                <span class="k">价格</span>
                <span class="v">{{ selectedSkuPriceText }}</span>
              </div>
            </div>
          </div>

          

          <div class="shipping-info" v-if="pc.shippingServicesInfo || pc.baseFreight != null">
            <div class="shipping-line" v-if="pc.shippingServicesInfo">
              <CarOutlined style="color: #2563eb" />
              <span>{{ pc.shippingServicesInfo }}</span>
            </div>
            <div style="margin-top: 12px" v-if="pc.baseFreight != null">
              <span class="freight">
                <TagOutlined />
                基础运费 ¥{{ numText(pc.baseFreight, 2) }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <div class="detail-section">
        <div class="detail-layout">
          <div class="detail-images-col" v-if="detailImagesArr.length">
            <div class="section-title" style="margin-top: 0">
              <PictureOutlined />
              商品细节图
            </div>
             <div class="detail-images">
                <div class="detail-img-item" v-for="(img, idx) in detailImagesToShow" :key="img + idx">
                  <img :src="img" alt="detail" loading="lazy" />

                  <button
                    class="detail-add-carousel"
                    type="button"
                    :disabled="addingDetailImage === img"
                    :title="carouselImagesArr.includes(img) ? '这张图已在轮播图中' : '添加到轮播'"
                    @click.stop="addDetailImageToCarousel(img)"
                  >
                    {{ addingDetailImage === img ? '添加中...' : '添加到轮播' }}
                  </button>

                  <button class="detail-translate" type="button" title="翻译" @click.stop="openTranslate('detail', idx, img)">
                    <GlobalOutlined />
                  </button>

                  <button class="detail-del" type="button" title="删除" @click.stop="deleteDetailImage(img)">
                    <DeleteOutlined />
                  </button>
                </div>
              </div>
           </div>

          <div class="detail-meta-col">
        <div class="section-title">
          <TableOutlined />
          产品参数
        </div>
        <div class="attr-grid" v-if="attributePairs.length">
          <div class="attr-item" v-for="([key, value], idx) in attributePairs" :key="key + idx">
            <span class="attr-label">{{ key }}</span>
            <span class="attr-value">{{ value }}</span>
          </div>
        </div>
        <div v-else class="empty-block">暂无参数</div>

        <div v-if="temuAttributePairs.length" style="margin-top: 16px">
          <div class="section-title" style="margin-top: 0">
            <TableOutlined />
            TEMU 商品属性
          </div>
          <div class="attr-grid">
            <div class="attr-item" v-for="([key, value], idx) in temuAttributePairs" :key="key + idx">
              <span class="attr-label">{{ key }}</span>
              <span class="attr-value">{{ value }}</span>
            </div>
          </div>
        </div>

        <div v-if="temuSkuRows.length" style="margin-top: 16px">
          <div class="section-title" style="margin-top: 0">
            <TableOutlined />
            TEMU SKU
          </div>
          <div class="temu-sku-table">
            <div class="temu-sku-head">
              <span>属性</span>
              <span>供货价</span>
              <span>重量</span>
              <span>尺寸</span>
            </div>
            <div class="temu-sku-row" v-for="(r, idx) in temuSkuRows" :key="(r.temuSkuId || r.originSkuId || r.specKey || '') + idx">
              <span class="mono" :title="r.specKey || ''">{{ r.specKey || '-' }}</span>
              <span class="mono">{{ r.supplyPrice != null ? ('¥' + Number(r.supplyPrice).toFixed(2)) : '-' }}</span>
              <span class="mono">{{ r.weightG != null ? (r.weightG + 'g') : '-' }}</span>
              <span class="mono">{{ dimText(r) }}</span>
            </div>
          </div>
        </div>

        <div class="packaging-info" v-if="hasPackaging">
          <div>包装尺寸 <span>{{ pc.packagingDimensions || '-' }} cm</span></div>
          <div>包装重量 <span>{{ pc.packagingWeight ?? '-' }}g</span></div>
          <div>
            长×宽×高
            <span>{{ pc.packagingLength ?? '-' }}×{{ pc.packagingWidth ?? '-' }}×{{ pc.packagingHeight ?? '-' }}</span>
          </div>
        </div>

        <div class="footer-note">
          <CheckCircleOutlined />
          {{ pc.sourcePlatform || '-' }} 商品ID {{ pc.productId || '-' }} · 数据同步于 {{ todayText }}
        </div>
          </div>
        </div>
      </div>
    </div>

    <a-drawer v-model:open="rawOpen" title="全部字段" width="860" :destroyOnClose="true">
      <div class="raw">
        <div class="raw-row" v-for="k in allKeys" :key="k">
          <div class="rk">{{ k }}</div>
          <div class="rv">
            <pre v-if="isLarge(pc[k])" class="pre">{{ stringify(pc[k]) }}</pre>
            <span v-else>{{ stringify(pc[k]) }}</span>
          </div>
        </div>
      </div>
    </a-drawer>

    <a-modal
      v-model:open="fusionState.open"
      title="图片融合（Stable Diffusion）"
      :destroyOnClose="true"
      width="900"
      :bodyStyle="{ maxHeight: '80vh', overflowY: 'auto', overflowX: 'hidden' }"
      @cancel="closeFusion"
    >
      <template #footer>
        <div class="fuse-footer">
          <div class="fuse-footer-left">
            <span v-if="fusionState.errorMsg" class="fuse-footer-error">{{ fusionState.errorMsg }}</span>
            <span v-else class="fuse-footer-tip">选 2-4 张轮播图，填提示词，生成后可保存到轮播图</span>
          </div>
          <div class="fuse-footer-actions">
            <a-button @click="closeFusion">关闭</a-button>
            <a-button type="primary" :loading="fusionState.loading" @click="runFusion">开始生成</a-button>
            <a-button
              type="primary"
              ghost
              :disabled="!fusionState.resultUrl"
              :loading="fusionState.saving"
              @click="saveFusionToCarousel"
            >
              保存到轮播图
            </a-button>
          </div>
        </div>
      </template>

      <div class="fuse-wrap">
        <div class="fuse-strip">
          <div class="fuse-strip-head">
          </div>
          <div ref="fusionStripRef" class="fuse-strip-row" @wheel="onFusionStripWheel">
            <button
              v-for="(u, idx) in carouselImagesArr"
              :key="u + idx"
              class="fuse-strip-item"
              type="button"
              @click="addFusionImage(u)"
              :title="u"
            >
              <img :src="u" alt="" loading="lazy" draggable="false" @dragstart.prevent />
            </button>
          </div>
        </div>

        <div class="fuse-main">
          <div class="fuse-left">
            <div class="fuse-block-title">融合输入</div>
            <div class="fuse-picked">
              <div v-if="!fusionState.selected.length" class="fuse-empty">
                还没选择图片。请从上方轮播图点选（至少 2 张）。
              </div>
              <div v-else class="fuse-picked-list">
                <div v-for="(u, idx) in fusionState.selected" :key="u + idx" class="fuse-picked-item">
                  <img :src="u" alt="" loading="lazy" draggable="false" @dragstart.prevent />
                  <div class="fuse-picked-actions">
                    <span class="fuse-picked-idx">#{{ idx + 1 }}</span>
                    <button class="fuse-picked-remove" type="button" @click="removeFusionImage(idx)">移除</button>
                  </div>
                </div>
              </div>
            </div>

            <div class="fuse-form">
              <div class="fuse-field">
                <div class="fuse-label">提示词</div>
                <a-textarea
                  v-model:value="fusionState.prompt"
                  :autoSize="{ minRows: 2, maxRows: 5 }"
                  placeholder="描述你希望融合后的图：比如‘两件商品同框展示，电商白底海报，柔光棚拍，清晰，真实’"
                />
              </div>
              <div class="fuse-field">
                <div class="fuse-label">负面提示词</div>
                <a-input v-model:value="fusionState.negativePrompt" placeholder="可选：blurry, low quality, watermark..." />
              </div>

              <div class="fuse-params">
                <div class="fuse-param">
                  <div class="fuse-param-label">尺寸</div>
                  <div class="fuse-param-val">
                    <a-input-number v-model:value="fusionState.width" :min="256" :max="2048" />
                    <span class="fuse-x">x</span>
                    <a-input-number v-model:value="fusionState.height" :min="256" :max="2048" />
                  </div>
                </div>
                <div class="fuse-param">
                  <div class="fuse-param-label">strength</div>
                  <div class="fuse-param-val">
                    <a-input-number v-model:value="fusionState.strength" :min="0" :max="1" :step="0.05" />
                  </div>
                </div>
                <div class="fuse-param">
                  <div class="fuse-param-label">model</div>
                  <div class="fuse-param-val">
                    <a-select v-model:value="fusionState.model" style="width: 200px">
                      <a-select-option value="sd3.5-large">sd3.5-large</a-select-option>
                      <a-select-option value="sd3.5-large-turbo">sd3.5-large-turbo</a-select-option>
                      <a-select-option value="sd3.5-medium">sd3.5-medium</a-select-option>
                    </a-select>
                  </div>
                </div>
                <div class="fuse-param">
                  <div class="fuse-param-label">seed</div>
                  <div class="fuse-param-val">
                    <a-input-number v-model:value="fusionState.seed" :min="0" :max="4294967294" style="width: 220px" />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="fuse-right">
            <div class="fuse-block-title">生成结果</div>
            <div class="fuse-preview">
              <div v-if="!fusionState.resultUrl" class="fuse-preview-empty">
                <div v-if="fusionState.loading" class="fuse-preview-loading">正在生成...</div>
                <div v-else class="fuse-preview-hint">点击底部“开始生成”</div>
              </div>
              <img
                v-else
                class="fuse-preview-img"
                :src="fusionState.resultUrl"
                alt=""
                loading="lazy"
                @error="onFusionResultImgError"
              />
            </div>

            <div class="fuse-preview-meta" v-if="fusionState.resultUrl">
              <a :href="fusionState.resultUrl" target="_blank" rel="noopener noreferrer">打开</a>
              <button class="fuse-link-btn" type="button" @click="copyFusionResultUrl">复制链接</button>
            </div>
            <div class="fuse-preview-url" v-if="fusionState.resultUrl" :title="fusionState.resultUrl">
              {{ fusionState.resultUrl }}
            </div>
          </div>
        </div>
      </div>
    </a-modal>

    <a-modal
      v-model:open="translateState.open"
      title="图片翻译"
      :footer="null"
      :destroyOnClose="true"
      width="760"
      :bodyStyle="{ maxHeight: '82vh', overflow: 'auto' }"
      @cancel="closeTranslate"
    >
      <div class="translate-modal">
        <div class="translate-controls">
          <div class="tc-row">
            <span class="tc-label">源语言</span>
            <a-select v-model:value="translateState.sourceLanguage" style="width: 140px">
              <a-select-option value="zh">中文(zh)</a-select-option>
              <a-select-option value="en">英文(en)</a-select-option>
              <a-select-option value="es">西语(es)</a-select-option>
              <a-select-option value="fr">法语(fr)</a-select-option>
              <a-select-option value="de">德语(de)</a-select-option>
              <a-select-option value="it">意大利语(it)</a-select-option>
              <a-select-option value="pt">葡语(pt)</a-select-option>
              <a-select-option value="ru">俄语(ru)</a-select-option>
              <a-select-option value="ja">日语(ja)</a-select-option>
              <a-select-option value="ko">韩语(ko)</a-select-option>
            </a-select>

            <span class="tc-label" style="margin-left: 12px">目标语言</span>
            <a-select v-model:value="translateState.targetLang" style="width: 140px">
              <a-select-option value="en">英文(en)</a-select-option>
              <a-select-option value="zh">中文(zh)</a-select-option>
              <a-select-option value="es">西语(es)</a-select-option>
              <a-select-option value="fr">法语(fr)</a-select-option>
              <a-select-option value="de">德语(de)</a-select-option>
              <a-select-option value="it">意大利语(it)</a-select-option>
              <a-select-option value="pt">葡语(pt)</a-select-option>
              <a-select-option value="ru">俄语(ru)</a-select-option>
              <a-select-option value="ja">日语(ja)</a-select-option>
              <a-select-option value="ko">韩语(ko)</a-select-option>
            </a-select>

            <span class="tc-label" style="margin-left: 12px">翻译厂家</span>
            <a-select v-model:value="translateState.provider" style="width: 140px">
              <a-select-option value="temu">TEMU</a-select-option>
              <a-select-option value="aliyun">阿里云</a-select-option>
            </a-select>
          </div>

          <div class="tc-row" style="margin-top: 10px">
            <a-checkbox v-model:checked="translateState.containDetail">包含细节(containDetail)</a-checkbox>
            <a-checkbox v-model:checked="translateState.uploadToOss" style="margin-left: 14px">上传到OSS(可选)</a-checkbox>

            <div class="tc-actions">
              <a-button @click="closeTranslate">取消</a-button>
              <a-button type="primary" :loading="translateState.loading" @click="runTranslate">开始翻译</a-button>
              <a-button
                type="primary"
                ghost
                :disabled="!(translateState.storedUrl || translateState.translatedUrl)"
                :loading="translateState.saving"
                @click="saveTranslated"
              >
                保存并替换
              </a-button>
            </div>
          </div>

          <div class="tc-hint" v-if="translateState.taskId">
            taskId: <span class="mono">{{ translateState.taskId }}</span>
          </div>
          <div class="tc-error" v-if="translateState.errorMsg">{{ translateState.errorMsg }}</div>
        </div>

        <div class="translate-compare">
          <div class="img-box">
            <div class="img-title">原图</div>
            <div class="img-frame">
              <img :src="translateState.originalUrl" alt="original" loading="lazy" />
            </div>
          </div>
          <div class="img-box">
            <div class="img-title">翻译结果</div>
            <div class="img-frame">
              <img v-if="(translateState.storedUrl || translateState.translatedUrl)" :src="(translateState.storedUrl || translateState.translatedUrl)" alt="translated" loading="lazy" />
              <div v-else class="img-placeholder">
                <span v-if="translateState.loading">正在翻译...</span>
                <span v-else>点击“开始翻译”生成结果</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </a-modal>

    <a-modal
      v-model:open="splitState.open"
      title="按 SKU 拆分商品"
      width="1080px"
      :confirm-loading="splitState.saving"
      ok-text="保存拆分"
      cancel-text="取消"
      @ok="submitSplit"
    >
      <div class="split-modal">
        <div class="split-toolbar">
          <div>
            <div class="split-title">新商品分组</div>
            <div class="split-subtitle">每个 SKU 只能归到一个分组，保存后原商品会被删除并替换为新商品。</div>
          </div>
          <a-button type="dashed" @click="addSplitGroup">新增分组</a-button>
        </div>

        <div class="split-groups">
          <div v-for="group in splitState.groups" :key="group.key" class="split-group-card">
            <div class="split-group-head">
              <a-input v-model:value="group.name" :maxlength="50" placeholder="请输入分组名称" />
              <a-button danger type="text" @click="removeSplitGroup(group.key)" :disabled="splitState.groups.length <= 2">删除</a-button>
            </div>
            <div class="split-group-meta">已分配 {{ splitGroupCount(group.key) }} 个 SKU</div>
          </div>
        </div>

        <div class="split-sku-list">
          <div v-for="row in splitSkuRows" :key="row.id" class="split-sku-item">
            <div class="split-sku-main">
              <img v-if="row.image" :src="row.image" alt="sku" class="split-sku-image" />
              <div v-else class="split-sku-image split-sku-image-empty">无图</div>
              <div class="split-sku-info">
                <div class="split-sku-title">{{ row.specLabel || row.skuId || ('SKU ' + row.id) }}</div>
                <div class="split-sku-meta">SKU ID: {{ row.skuId || '-' }}</div>
                <div class="split-sku-meta">规格: {{ row.specJsonText || '-' }}</div>
                <div class="split-sku-meta">库存: {{ row.stock ?? '-' }} | 价格: {{ formatMoney(row.price) }}</div>
              </div>
            </div>
            <a-select
              class="split-sku-select"
              :value="splitState.assignments[row.id] || undefined"
              placeholder="选择分组"
              @change="value => assignSplitGroup(row.id, value)"
            >
              <a-select-option v-for="group in splitState.groups" :key="group.key" :value="group.key">
                {{ group.name || '未命名分组' }}
              </a-select-option>
            </a-select>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
 import { message, Modal } from 'ant-design-vue'
import { productCollectionApi } from '@/platform/api/productCollections'
import {
  AppstoreOutlined,
  BankOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CodeOutlined,
  EnvironmentOutlined,
  HeartOutlined,
  LeftOutlined,
  LineChartOutlined,
  LinkOutlined,
  MessageOutlined,
  PictureOutlined,
  PlayCircleOutlined,
  RedoOutlined,
  StarOutlined,
  TableOutlined,
  TagOutlined,
  CarOutlined,
  DeleteOutlined,
  GlobalOutlined
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()

const id = computed(() => String(route.params.id || ''))
const pc = reactive({})

const replacingKwcdn = ref(false)
const normalizingImages = ref(false)
const batchTranslatingImages = ref(false)
const swappingImages = ref(false)
const addingDetailImage = ref('')
const splitState = reactive({
  open: false,
  saving: false,
  groups: [],
  assignments: {}
})

const replaceImagesToKwcdn = async () => {
  if (!id.value) return
  replacingKwcdn.value = true
  try {
    const res = await productCollectionApi.replaceImagesToKwcdn(id.value)
    if (res?.success) {
      const data = res.data || {}
      await load()
      message.success(data.changed ? '已替换并保存到数据库' : '图片已是 kwcdn 域名，无需替换')
      return
    }
    message.error(res?.message || '替换失败')
  } catch (e) {
    message.error(e.message || '替换失败')
  } finally {
    replacingKwcdn.value = false
  }
}

const normalizeAllImagesTo800 = async () => {
  if (!id.value) return
  normalizingImages.value = true
  try {
    const res = await productCollectionApi.normalizeAllImagesTo800(id.value)
    if (res?.success) {
      const data = res.data || {}
      await load()
      const total = data.totalImages || 0
      const changedCount = (data.changes || []).length
      const skuCount = data.skuImageChanged || 0
      const changes = Array.isArray(data.changes) ? data.changes : []
      Modal.info({
        title: '图片规范化结果',
        width: 820,
        content: h('div', { class: 'normalize-result' }, [
          h('div', { class: 'normalize-summary' }, [
            h('div', null, `总图片数：${total}`),
            h('div', null, `已处理变更：${changedCount}`),
            h('div', null, `SKU 图片变更：${skuCount}`),
            h('div', null, data.changed ? '结果：已写回数据库' : '结果：所有图片已是 800x800，无需处理')
          ]),
          changes.length
            ? h('div', { class: 'normalize-change-list' }, changes.map((item, idx) =>
                h('div', { key: `${item?.field || 'field'}_${idx}`, class: 'normalize-change-item' }, [
                  h('div', { class: 'normalize-change-field' }, item?.field || '-'),
                  h('div', { class: 'normalize-change-meta' }, [
                    h('span', null, `原尺寸：${item?.width || '-'} x ${item?.height || '-'}`),
                    h('span', null, `原因：${item?.reason || '-'}`)
                  ]),
                  h('div', { class: 'normalize-change-url' }, `原图：${item?.original || '-'}`),
                  h('div', { class: 'normalize-change-url' }, `结果：${item?.uploaded || '-'}`)
                ])
              ))
            : h('div', { class: 'normalize-empty' }, '没有图片需要变更')
        ])
      })
      return
    }
    message.error(res?.message || '规范化失败')
  } catch (e) {
    message.error(e.message || '规范化失败')
  } finally {
    normalizingImages.value = false
  }
}

const translateAllImages = async () => {
  if (!id.value) return
  batchTranslatingImages.value = true
  try {
    const res = await productCollectionApi.translateAllImages(id.value, { provider: 'aliyun' })
    if (res?.success) {
      const data = res.data || {}
      await load()
      Modal.info({
        title: '批量翻译结果',
        width: 860,
        content: h('div', { class: 'normalize-result' }, [
          h('div', { class: 'normalize-summary' }, [
            h('div', null, `翻译通道：${data.provider || 'aliyun'}`),
            h('div', null, `字段数：${data.totalFields || 0}`),
            h('div', null, `去重后图片数：${data.uniqueImages || 0}`),
            h('div', null, `成功：${data.successCount || 0}`),
            h('div', null, `失败：${data.failedCount || 0}`),
            h('div', null, `SKU 图片变更：${data.skuChanged || 0}`),
            h('div', null, data.changed ? '结果：已写回数据库' : '结果：没有可更新的图片')
          ]),
          Array.isArray(data.changes) && data.changes.length
            ? h('div', { class: 'normalize-change-list' }, data.changes.map((item, idx) =>
                h('div', { key: `${item?.sourceUrl || 'source'}_${idx}`, class: 'normalize-change-item' }, [
                  h('div', { class: 'normalize-change-field' }, Array.isArray(item?.fieldRefs) ? item.fieldRefs.join(' , ') : '-'),
                  h('div', { class: 'normalize-change-meta' }, [
                    h('span', null, `状态：${item?.status || '-'}`),
                    h('span', null, `通道：${item?.provider || '-'}`)
                  ]),
                  h('div', { class: 'normalize-change-url' }, `原图：${item?.sourceUrl || '-'}`),
                  h('div', { class: 'normalize-change-url' }, `译图：${item?.translatedUrl || '-'}`),
                  item?.errorMsg
                    ? h('div', { class: 'normalize-change-url' }, `错误：${item.errorMsg}`)
                    : null
                ].filter(Boolean))
              ))
            : h('div', { class: 'normalize-empty' }, '没有可翻译图片')
        ])
      })
      return
    }
    message.error(res?.message || '批量翻译失败')
  } catch (e) {
    message.error(e.message || '批量翻译失败')
  } finally {
    batchTranslatingImages.value = false
  }
}

const swapCarouselAndDetail = async () => {
  if (!id.value) return
  const curCarousel = safeJsonParse(pc.carouselImages)
  const curDetail = safeJsonParse(pc.detailImages)
  const nextCarousel = Array.isArray(curDetail) ? curDetail : []
  const nextDetail = Array.isArray(curCarousel) ? curCarousel : []

  if ((nextCarousel.length === 0) && (nextDetail.length === 0)) {
    message.info('暂无可交换的图片数据')
    return
  }

  swappingImages.value = true
  try {
    const payload = {
      carouselImages: JSON.stringify(nextCarousel),
      detailImages: JSON.stringify(nextDetail)
    }
    const res = await productCollectionApi.update(id.value, payload)
    if (res?.success) {
      pc.carouselImages = payload.carouselImages
      pc.detailImages = payload.detailImages
      message.success('已交换轮播图与详情图')
      return
    }
    message.error(res?.message || '交换失败')
  } catch (e) {
    message.error(e.message || '交换失败')
  } finally {
    swappingImages.value = false
  }
}

const rawOpen = ref(false)
const showVideo = ref(false)
const mainImgFailed = ref(false)
const showAllDetails = ref(true)
const skuImgFailed = ref({})

const go = (path) => router.push(path)

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/platform/product-collections')
}

const openRaw = () => {
  rawOpen.value = true
}

const openUrl = (u) => {
  if (!u) return
  window.open(u, '_blank', 'noopener,noreferrer')
}

const copyLink = async () => {
  const u = pc.productUrl
  if (!u) return
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(String(u))
    } else {
      const ta = document.createElement('textarea')
      ta.value = String(u)
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    message.success('链接已复制')
  } catch {
    message.error('复制失败')
  }
}

const toFixed = (v, n) => {
  const num = Number(v)
  if (!Number.isFinite(num)) return '-'
  return num.toFixed(n)
}

const numText = (v, n = 2) => {
  const num = Number(v)
  if (!Number.isFinite(num)) return '-'
  return num.toFixed(n)
}

const safeJsonParse = (s) => {
  if (!s || typeof s !== 'string') return null
  try {
    return JSON.parse(s)
  } catch {
    return null
  }
}

const trimText = (value) => {
  if (value == null) return ''
  return String(value).trim()
}

const formatMoney = (value) => {
  const num = Number(value)
  if (!Number.isFinite(num)) return '-'
  return `¥${num.toFixed(2)}`
}

const buildSplitGroupKey = () => `group_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`

const createSplitGroup = (index) => ({
  key: buildSplitGroupKey(),
  name: `拆分商品${index}`
})

const parseSpecJsonText = (specJson, fallback = '') => {
  const parsed = safeJsonParse(specJson)
  if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
    return Object.entries(parsed)
      .map(([key, value]) => `${key}: ${value ?? ''}`)
      .join(' | ')
  }
  return trimText(specJson) || fallback
}

const isDeletableCarouselImage = (u) => {
  if (!u) return false
  // Do not allow deleting main image from this UI.
  if (pc.productMainImage && u === pc.productMainImage) return false
  return carouselImagesArr.value.includes(u)
}

const deleteCarouselImage = async (u) => {
  if (!u) return
  if (!id.value) return

  Modal.confirm({
    title: '删除轮播图',
    content: '确定删除这张轮播图吗？',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const next = carouselImagesArr.value.filter(x => x && x !== u)
        const json = JSON.stringify(next)
        const res = await productCollectionApi.update(id.value, { carouselImages: json })
        if (res?.success) {
          pc.carouselImages = json
          message.success('已删除')
          return
        }
        message.error(res?.message || '删除失败')
      } catch (e) {
        message.error(e.message || '删除失败')
      }
    }
  })
}

const translateState = reactive({
  open: false,
  kind: '',
  index: -1,
  originalUrl: '',
  translatedUrl: '',
  storedUrl: '',
  taskId: '',
  loading: false,
  saving: false,
  sourceLanguage: 'zh',
  targetLang: 'en',
  provider: 'temu',
  containDetail: true,
  uploadToOss: false,
  errorMsg: ''
})

const fusionState = reactive({
  open: false,
  selected: [],
  resultUrl: '',
  loading: false,
  saving: false,
  prompt: 'Two products composed in one image, clean e-commerce layout, studio lighting, photorealistic',
  negativePrompt: 'blurry, low quality, distorted, watermark, text',
  width: 800,
  height: 800,
  strength: 0.1,
  model: 'sd3.5-large',
  seed: 0,
  errorMsg: ''
})

const onFusionResultImgError = () => {
  // If the image fails to render (URL too long / blocked / unsupported), keep the URL visible below.
  if (!fusionState.errorMsg) {
    fusionState.errorMsg = '结果图片加载失败（但可以尝试“打开结果”查看）'
  }
}

const copyFusionResultUrl = async () => {
  const u = fusionState.resultUrl
  if (!u) return
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(String(u))
    } else {
      const ta = document.createElement('textarea')
      ta.value = String(u)
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    message.success('已复制')
  } catch {
    message.error('复制失败')
  }
}

const fusionStripRef = ref(null)

const onFusionStripWheel = (e) => {
  const el = fusionStripRef.value
  if (!el) return
  // If the strip can scroll horizontally, keep the wheel gesture here.
  const canScroll = el.scrollWidth > el.clientWidth + 1
  if (!canScroll) return
  // Map vertical wheel to horizontal scroll (trackpad/mouse compatibility)
  if (e && e.cancelable) e.preventDefault()
  if (e && typeof e.stopPropagation === 'function') e.stopPropagation()
  if (Math.abs(e.deltaY) > Math.abs(e.deltaX)) {
    el.scrollLeft += e.deltaY
  } else {
    el.scrollLeft += e.deltaX
  }
}



const openFusion = () => {
  fusionState.open = true
  fusionState.selected = []
  fusionState.resultUrl = ''
  fusionState.loading = false
  fusionState.saving = false
  fusionState.errorMsg = ''
}

const closeFusion = () => {
  fusionState.open = false
}

const addFusionImage = (u) => {
  if (!u) return
  if (fusionState.selected.includes(u)) return
  fusionState.selected = [...fusionState.selected, u]
}

const removeFusionImage = (idx) => {
  fusionState.selected = fusionState.selected.filter((_, i) => i !== idx)
}

const runFusion = async () => {
  if (!fusionState.open) return
  if (!id.value) return
  if (!fusionState.selected || fusionState.selected.length < 2) {
    message.error('至少选择 2 张图片')
    return
  }
  fusionState.loading = true
  fusionState.errorMsg = ''
  fusionState.resultUrl = ''
  try {
    const payload = {
      imageUrls: fusionState.selected,
      prompt: fusionState.prompt,
      negativePrompt: fusionState.negativePrompt,
      width: fusionState.width,
      height: fusionState.height,
      strength: fusionState.strength,
      model: fusionState.model,
      seed: fusionState.seed
    }
    const res = await productCollectionApi.fuseImages(id.value, payload)
    if (res?.success && res.data?.imageUrl) {
      fusionState.resultUrl = res.data.imageUrl
      message.success('生成完成')
      return
    }
    fusionState.errorMsg = res?.message || '生成失败'
    message.error(fusionState.errorMsg)
  } catch (e) {
    fusionState.errorMsg = e.message || '生成失败'
    message.error(fusionState.errorMsg)
  } finally {
    fusionState.loading = false
  }
}

const saveFusionToCarousel = async () => {
  if (!fusionState.resultUrl) return
  if (!id.value) return
  fusionState.saving = true
  try {
    const next = [...carouselImagesArr.value, fusionState.resultUrl]
    const res = await productCollectionApi.update(id.value, { carouselImages: JSON.stringify(next) })
    if (res?.success) {
      pc.carouselImages = JSON.stringify(next)
      message.success('已保存到轮播图')
      fusionState.open = false
      return
    }
    message.error(res?.message || '保存失败')
  } catch (e) {
    message.error(e.message || '保存失败')
  } finally {
    fusionState.saving = false
  }
}

const openTranslate = (kind, index, url) => {
  if (!id.value) return
  if (!url) return

  translateState.open = true
  translateState.kind = kind
  translateState.index = index
  translateState.originalUrl = url
  translateState.translatedUrl = ''
  translateState.storedUrl = ''
  translateState.taskId = ''
  translateState.loading = false
  translateState.saving = false
  translateState.errorMsg = ''
}

const runTranslate = async () => {
  if (!translateState.open) return
  if (!translateState.originalUrl) return
  if (!id.value) return

  translateState.loading = true
  translateState.errorMsg = ''
  translateState.translatedUrl = ''
  translateState.storedUrl = ''
  translateState.taskId = ''

  try {
    const payload = {
      imageUrl: translateState.originalUrl,
      sourceLanguage: translateState.sourceLanguage,
      targetLang: translateState.targetLang,
      provider: translateState.provider,
      containDetail: translateState.containDetail,
      uploadToOss: translateState.uploadToOss
    }
    const res = await productCollectionApi.translateImage(id.value, payload)
    if (res?.success && res.data) {
      translateState.translatedUrl = res.data.translatedUrl || ''
      translateState.storedUrl = res.data.storedUrl || ''
      translateState.taskId = res.data.taskId || ''
      message.success('翻译完成')
      return
    }
    translateState.errorMsg = res?.message || '翻译失败'
    message.error(translateState.errorMsg)
  } catch (e) {
    translateState.errorMsg = e.message || '翻译失败'
    message.error(translateState.errorMsg)
  } finally {
    translateState.loading = false
  }
}

const closeTranslate = () => {
  translateState.open = false
}

const saveTranslated = async () => {
  if (!translateState.open) return
  if (!id.value) return
  const nextUrl = translateState.storedUrl || translateState.translatedUrl
  if (!nextUrl) {
    message.error('还没有翻译结果')
    return
  }

  translateState.saving = true
  try {
    if (translateState.kind === 'carousel') {
      const cur = galleryImages.value
      if (translateState.index < 0 || translateState.index >= cur.length) {
        message.error('图片索引无效')
        return
      }

      const updated = [...cur]
      updated[translateState.index] = nextUrl

      // Persist back to fields: main image is stored separately
      let newMain = pc.productMainImage
      let newCarousel = carouselImagesArr.value.slice()
      const original = translateState.originalUrl

      if (original && pc.productMainImage === original) {
        newMain = nextUrl
      }
      newCarousel = newCarousel.map(u => (u === original ? nextUrl : u))

      const res = await productCollectionApi.update(id.value, {
        productMainImage: newMain,
        carouselImages: JSON.stringify(newCarousel)
      })
      if (res?.success) {
        pc.productMainImage = newMain
        pc.carouselImages = JSON.stringify(newCarousel)
        message.success('已保存并替换原图')
        translateState.open = false
        return
      }
      message.error(res?.message || '保存失败')
      return
    }

    if (translateState.kind === 'detail') {
      const cur = detailImagesArr.value
      if (translateState.index < 0 || translateState.index >= cur.length) {
        message.error('图片索引无效')
        return
      }
      const updated = [...cur]
      const original = translateState.originalUrl
      updated[translateState.index] = nextUrl

      const res = await productCollectionApi.update(id.value, {
        detailImages: JSON.stringify(updated)
      })
      if (res?.success) {
        pc.detailImages = JSON.stringify(updated)
        message.success('已保存并替换原图')
        translateState.open = false
        return
      }
      message.error(res?.message || '保存失败')
      return
    }
  } catch (e) {
    message.error(e.message || '保存失败')
  } finally {
    translateState.saving = false
  }
}

const deleteDetailImage = async (u) => {
  if (!u) return
  if (!id.value) return

  Modal.confirm({
    title: '删除详情图',
    content: '确定删除这张详情图吗？',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const next = detailImagesArr.value.filter(x => x && x !== u)
        const json = JSON.stringify(next)
        const res = await productCollectionApi.update(id.value, { detailImages: json })
        if (res?.success) {
          pc.detailImages = json
          message.success('已删除')
          return
        }
        message.error(res?.message || '删除失败')
      } catch (e) {
        message.error(e.message || '删除失败')
      }
    }
  })
}

const addDetailImageToCarousel = async (u) => {
  if (!u) return
  if (!id.value) return
  if (carouselImagesArr.value.includes(u)) {
    message.info('这张图已经在轮播图里了')
    return
  }

  addingDetailImage.value = u
  try {
    const next = [...carouselImagesArr.value, u]
    const json = JSON.stringify(next)
    const res = await productCollectionApi.update(id.value, { carouselImages: json })
    if (res?.success) {
      pc.carouselImages = json
      message.success('已添加到轮播图')
      return
    }
    message.error(res?.message || '添加失败')
  } catch (e) {
    message.error(e.message || '添加失败')
  } finally {
    addingDetailImage.value = ''
  }
}

const carouselImagesArr = computed(() => {
  const arr = safeJsonParse(pc.carouselImages)
  return Array.isArray(arr) ? arr.filter(Boolean) : []
})

const detailImagesArr = computed(() => {
  const arr = safeJsonParse(pc.detailImages)
  return Array.isArray(arr) ? arr.filter(Boolean) : []
})

const galleryImages = computed(() => {
  const list = []
  if (pc.productMainImage) list.push(pc.productMainImage)
  for (const u of carouselImagesArr.value) {
    if (u && !list.includes(u)) list.push(u)
  }
  return list
})

const activeIndex = ref(0)
const activeImage = computed(() => {
  if (mainImgFailed.value && activeIndex.value === 0) {
    const next = galleryImages.value.find(Boolean)
    return next || ''
  }
  return galleryImages.value[activeIndex.value] || ''
})

watch(
  () => galleryImages.value.join('|'),
  () => {
    activeIndex.value = 0
    showVideo.value = false
    mainImgFailed.value = false
    skuImgFailed.value = {}
  }
)

const onMainImgError = () => {
  mainImgFailed.value = true
}

const onSkuImgError = (idx) => {
  skuImgFailed.value = { ...skuImgFailed.value, [idx]: true }
}

const selectImage = (idx) => {
  activeIndex.value = idx
  showVideo.value = false
}

const selectVideo = () => {
  showVideo.value = true
}

const videoPoster = computed(() => {
  // Use the first image as the video poster/cover
  return galleryImages.value[0] || ''
})

const skuList = computed(() => {
  if (Array.isArray(pc.skuRows) && pc.skuRows.length) {
    return pc.skuRows.map(row => ({
      id: row?.id,
      name: row?.specKey || '',
      skuId: row?.skuId || '',
      stock: row?.stock,
      price: row?.price,
      image: row?.image,
      specJson: row?.specJson || ''
    }))
  }

  const obj = safeJsonParse(pc.skuData)
  const list = obj?.skus
  return Array.isArray(list) ? list : []
})

const splitSkuRows = computed(() => {
  const rows = Array.isArray(pc.skuRows) ? pc.skuRows : []
  return rows
    .filter(row => row?.id != null)
    .map(row => ({
      id: row.id,
      skuId: row?.skuId || '',
      specKey: row?.specKey || '',
      specJson: row?.specJson || '',
      specLabel: trimText(row?.specKey),
      specJsonText: parseSpecJsonText(row?.specJson),
      stock: row?.stock,
      price: row?.price,
      image: row?.image || ''
    }))
})

const canSplitProduct = computed(() => splitSkuRows.value.length >= 2)

const skuModel = computed(() => {
  if (Array.isArray(pc.skuPropsExt) && pc.skuPropsExt.length) {
    const props = pc.skuPropsExt.map(prop => ({
      fid: prop?.fid,
      name: prop?.name || '',
      sort: prop?.sort ?? 0,
      values: Array.isArray(prop?.values)
        ? prop.values.map(v => ({
            name: v?.value || '',
            image: v?.image || '',
            sort: v?.sort ?? 0
          }))
        : []
    }))

    const rows = Array.isArray(pc.skuRows) ? pc.skuRows : []
    const skuMapObj = rows.reduce((acc, row) => {
      if (row?.specKey) {
        acc[row.specKey] = {
          skuId: row?.skuId || '',
          stock: row?.stock,
          price: row?.price,
          image: row?.image || '',
          specJson: row?.specJson || ''
        }
      }
      return acc
    }, {})

    return {
      props,
      skuMap: skuMapObj,
      meta: { keySep: '>' }
    }
  }

  const obj = safeJsonParse(pc.skuModel)
  return obj && typeof obj === 'object' && !Array.isArray(obj) ? obj : null
})

const skuProps = computed(() => {
  const list = skuModel.value?.props
  return Array.isArray(list) ? list : []
})

const skuMap = computed(() => {
  const m = skuModel.value?.skuMap
  return m && typeof m === 'object' && !Array.isArray(m) ? m : {}
})

const skuKeySep = computed(() => {
  const sep = skuModel.value?.meta?.keySep
  return typeof sep === 'string' && sep ? sep : '>'
})

const selectedSku = ref({})

watch(
  () => [skuProps.value, pc.id],
  () => {
    // reset defaults on load
    const next = {}
    for (const p of skuProps.value) {
      const name = p?.name
      const values = Array.isArray(p?.values) ? p.values : []
      if (typeof name === 'string' && name && values.length) {
        const first = values[0]
        if (first?.name) next[name] = first.name
      }
    }
    selectedSku.value = next
  },
  { immediate: true }
)

const selectedSkuKey = computed(() => {
  if (!skuProps.value.length) return ''
  const parts = []
  for (const p of skuProps.value) {
    const name = p?.name
    if (!name) continue
    parts.push(selectedSku.value?.[name] || '')
  }
  return parts.join(skuKeySep.value)
})

const selectedSkuRow = computed(() => {
  const key = selectedSkuKey.value
  if (!key) return null
  return skuMap.value?.[key] || null
})

const selectedSkuPriceText = computed(() => {
  const price = Number(selectedSkuRow.value?.price)
  if (!Number.isFinite(price)) return '-'
  return `¥${price.toFixed(2)}`
})

const onSelectSkuValue = (propName, valName) => {
  selectedSku.value = { ...selectedSku.value, [propName]: valName }
}

// metaPairs removed: info already shown on left

const attributePairs = computed(() => {
  const obj = safeJsonParse(pc.attributesData)
  if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return []
  return Object.entries(obj).map(([k, v]) => [String(k), v == null ? '' : String(v)])
})

const temuAttributePairs = computed(() => {
  const obj = safeJsonParse(pc.temuAttributes)
  const props = obj?.properties
  if (!Array.isArray(props) || !props.length) return []
  return props
    .filter(p => p && p.name)
    .map(p => {
      const name = String(p.name)
      const req = p.required ? '（必填）' : ''
      const selected = Array.isArray(p.selectedValues) ? p.selectedValues : []
      const text = selected.length
        ? selected.map(v => v?.value).filter(Boolean).join('，')
        : (p.freeText ? String(p.freeText) : '')
      return [`${name}${req}`, text || '-']
    })
})

const temuSkuRows = computed(() => {
  const list = pc.temuSkus
  return Array.isArray(list) ? list : []
})

const dimText = (r) => {
  const l = r?.lengthCm
  const w = r?.widthCm
  const h = r?.heightCm
  if (l == null || w == null || h == null) return '-'
  return `${l}x${w}x${h}cm`
}

const priceRangeText = computed(() => {
  const prices = skuList.value.map(s => Number(s.price)).filter(n => Number.isFinite(n))
  if (prices.length) {
    const min = Math.min(...prices)
    const max = Math.max(...prices)
    if (min === max) return `¥${min.toFixed(2)}`
    return `¥${min.toFixed(2)} ~ ${max.toFixed(2)}`
  }

  const min = Number(pc.minPrice)
  const max = Number(pc.maxPrice)
  if (Number.isFinite(min) && Number.isFinite(max)) {
    if (min === max) return `¥${min.toFixed(2)}`
    return `¥${min.toFixed(2)} ~ ${max.toFixed(2)}`
  }

  if (Number.isFinite(min)) return `¥${min.toFixed(2)}`
  if (Number.isFinite(max)) return `¥${max.toFixed(2)}`
  return '-'
})

const totalStockText = computed(() => {
  const nums = skuList.value.map(s => Number(s.stock)).filter(n => Number.isFinite(n))
  if (!nums.length) return '-'
  const sum = nums.reduce((a, b) => a + b, 0)
  return `${sum.toLocaleString()} 件`
})

const hasPackaging = computed(() => {
  return !!(
    pc.packagingDimensions ||
    pc.packagingWeight != null ||
    pc.packagingLength != null ||
    pc.packagingWidth != null ||
    pc.packagingHeight != null
  )
})

const detailImagesToShow = computed(() => {
  return detailImagesArr.value
})

const allKeys = computed(() => Object.keys(pc).sort((a, b) => a.localeCompare(b)))

const stringify = (v) => {
  if (v === null || v === undefined) return '-'
  if (Array.isArray(v)) return v.join(',')
  if (typeof v === 'object') {
    try {
      return JSON.stringify(v, null, 2)
    } catch {
      return String(v)
    }
  }
  return String(v)
}

const isLarge = (v) => {
  if (v == null) return false
  const s = stringify(v)
  return s.length > 160 || s.includes('\n')
}

const todayText = computed(() => {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
})

const resetSplitState = () => {
  splitState.groups = [createSplitGroup(1), createSplitGroup(2)]
  splitState.assignments = {}
}

const openSplitModal = () => {
  if (!canSplitProduct.value) {
    message.info('至少需要 2 个 SKU 才能拆分商品')
    return
  }
  resetSplitState()
  splitState.open = true
}

const addSplitGroup = () => {
  splitState.groups.push(createSplitGroup(splitState.groups.length + 1))
}

const removeSplitGroup = (groupKey) => {
  if (splitState.groups.length <= 2) {
    message.warning('至少保留两个分组')
    return
  }
  splitState.groups = splitState.groups.filter(group => group.key !== groupKey)
  const nextAssignments = {}
  Object.entries(splitState.assignments || {}).forEach(([rowId, assignedGroupKey]) => {
    if (assignedGroupKey !== groupKey) {
      nextAssignments[rowId] = assignedGroupKey
    }
  })
  splitState.assignments = nextAssignments
}

const assignSplitGroup = (rowId, groupKey) => {
  splitState.assignments = {
    ...splitState.assignments,
    [rowId]: groupKey
  }
}

const splitGroupCount = (groupKey) => {
  return splitSkuRows.value.filter(row => splitState.assignments[row.id] === groupKey).length
}

const buildSplitPayload = () => {
  const groups = splitState.groups
    .map((group, index) => ({
      name: trimText(group.name) || `拆分商品${index + 1}`,
      skuRowIds: splitSkuRows.value
        .filter(row => splitState.assignments[row.id] === group.key)
        .map(row => row.id)
    }))
    .filter(group => group.skuRowIds.length > 0)

  if (groups.length < 2) {
    throw new Error('至少需要两个有 SKU 的分组')
  }
  if (groups.some(group => !group.skuRowIds.length)) {
    throw new Error('存在空分组，请先删除或分配 SKU')
  }
  if (Object.keys(splitState.assignments || {}).length !== splitSkuRows.value.length) {
    throw new Error('请先为所有 SKU 选择分组')
  }
  return { groups }
}

const submitSplit = async () => {
  if (!id.value) return
  let payload
  try {
    payload = buildSplitPayload()
  } catch (error) {
    message.warning(error.message || '拆分数据不完整')
    return
  }

  splitState.saving = true
  try {
    const res = await productCollectionApi.split(id.value, payload)
    if (!res?.success) {
      message.error(res?.message || '商品拆分失败')
      return
    }
    const products = Array.isArray(res.data?.products) ? res.data.products : []
    const firstProduct = products[0]
    splitState.open = false
    message.success(`拆分完成，已生成 ${products.length} 个商品`)
    if (firstProduct?.id) {
      router.replace(`/platform/product-collections/${firstProduct.id}`)
      return
    }
    router.replace('/platform/product-collections')
  } catch (e) {
    message.error(e.message || '商品拆分失败')
  } finally {
    splitState.saving = false
  }
}

const load = async () => {
  if (!id.value) return
  try {
    const res = await productCollectionApi.get(id.value)
    if (res?.success) {
      Object.assign(pc, res.data || {})
      return
    }
    message.error(res?.message || '加载失败')
  } catch (e) {
    message.error(e.message || '加载失败')
  }
}

onMounted(load)
</script>

<style scoped>
.pc-page {
  min-height: 100vh;
  background: #f4f6f9;
  color: #1e293b;
  line-height: 1.5;
  padding: 24px 16px;
}

.container {
  max-width: 1280px;
  margin: 0 auto;
  background: #ffffff;
  border-radius: 28px;
  box-shadow: 0 20px 40px -12px rgba(0, 0, 0, 0.12);
  overflow: hidden;
  padding: 26px 26px 36px;
}

.pc-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.head-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  padding: 10px 14px;
  border-radius: 14px;
  cursor: pointer;
  font-weight: 700;
  color: #0f172a;
}

.head-right {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

:deep(.normalize-result) {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

:deep(.normalize-summary) {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
  padding: 12px 14px;
  background: #f8fafc;
  border-radius: 12px;
  color: #0f172a;
}

:deep(.normalize-change-list) {
  max-height: 420px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

:deep(.normalize-change-item) {
  padding: 12px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
}

:deep(.normalize-change-field) {
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 6px;
}

:deep(.normalize-change-meta) {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  color: #475569;
  font-size: 12px;
  margin-bottom: 6px;
}

:deep(.normalize-change-url) {
  color: #334155;
  font-size: 12px;
  word-break: break-all;
  margin-top: 4px;
}

:deep(.normalize-empty) {
  padding: 16px;
  border-radius: 12px;
  background: #f8fafc;
  color: #475569;
}

.product-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 32px;
  margin-bottom: 42px;
}

@media (max-width: 880px) {
  .product-grid {
    grid-template-columns: 1fr;
    gap: 24px;
  }
}

.media-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.media-col .company-strip {
  margin-bottom: -2px;
}

.media-summary {
  background: #ffffff;
  border-radius: 20px;
  border: 1px solid #edf2f7;
  padding: 10px 12px;
}

.summary-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: #f8fafc;
  font-size: 12px;
  color: rgba(15, 23, 42, 0.78);
  white-space: nowrap;
}

.chip :deep(svg) {
  color: rgba(37, 99, 235, 0.9);
}

.chip-label {
  color: rgba(15, 23, 42, 0.55);
  font-weight: 800;
}

.chip-value {
  color: rgba(15, 23, 42, 0.92);
  font-weight: 900;
}

.media-grid {
  display: grid;
  grid-template-columns: 84px 1fr;
  gap: 14px;
  align-items: start;
}

.thumb-col {
  --thumb-size: 74px;
  --thumb-gap: 10px;
  --thumb-visible: 6;

  display: flex;
  flex-direction: column;
  gap: var(--thumb-gap);
  /* Fixed visible count; overflow scroll for the rest */
  height: calc(var(--thumb-visible) * var(--thumb-size) + (var(--thumb-visible) - 1) * var(--thumb-gap));
  max-height: calc(var(--thumb-visible) * var(--thumb-size) + (var(--thumb-visible) - 1) * var(--thumb-gap));
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: contain;
  scroll-snap-type: y proximity;
  scrollbar-gutter: stable;
  padding-right: 2px;
}

.thumb-col::-webkit-scrollbar {
  width: 8px;
}

.thumb-col::-webkit-scrollbar-thumb {
  background: rgba(15, 23, 42, 0.16);
  border-radius: 999px;
  border: 2px solid transparent;
  background-clip: content-box;
}

.thumb-col::-webkit-scrollbar-track {
  background: transparent;
}

.main-col {
  min-width: 0;
}

.main-image-container {
  background: #fafbfc;
  border-radius: 24px;
  overflow: hidden;
  border: 1px solid #edf2f7;
  aspect-ratio: 1 / 1;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 12px -6px rgba(0, 0, 0, 0.05);
  position: relative;
}

.main-image-container img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.main-actions {
  position: absolute;
  right: 12px;
  top: 12px;
  display: inline-flex;
  gap: 10px;
  opacity: 0;
  transform: translateY(-6px);
  transition: all 0.16s ease;
}

.main-image-container:hover .main-actions {
  opacity: 1;
  transform: translateY(0);
}

.main-action {
  width: 40px;
  height: 40px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.18);
  background: rgba(255, 255, 255, 0.92);
  color: #0f172a;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  backdrop-filter: blur(6px);
}

.main-action:hover {
  border-color: rgba(37, 99, 235, 0.3);
  background: rgba(255, 255, 255, 0.98);
}

.main-action.danger {
  color: #ef4444;
}

.main-action.danger:hover {
  border-color: rgba(239, 68, 68, 0.35);
}

.img-empty {
  color: #64748b;
  font-weight: 700;
}

.thumbnail-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-top: 6px;
}

.thumbnail-item {
  width: var(--thumb-size);
  height: var(--thumb-size);
  min-height: var(--thumb-size);
  aspect-ratio: 1 / 1;
  flex: 0 0 var(--thumb-size);
  border-radius: 16px;
  border: 2px solid transparent;
  overflow: hidden;
  cursor: pointer;
  background: #f1f5f9;
  transition: all 0.15s;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.02);
  padding: 0;
  position: relative;
  scroll-snap-align: start;
}

.thumb-hit {
  width: 100%;
  height: 100%;
  border: 0;
  background: transparent;
  padding: 0;
  cursor: pointer;
  display: block;
}

.thumb-del {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 26px;
  height: 26px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.18);
  background: rgba(255, 255, 255, 0.92);
  color: #ef4444;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.thumb-del:hover {
  background: #ffffff;
  border-color: rgba(239, 68, 68, 0.35);
}


.thumbnail-item.active {
  border-color: #2563eb;
  box-shadow: 0 6px 12px -8px rgba(37, 99, 235, 0.32);
}

.thumbnail-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.video-thumb {
  position: relative;
  background: #1e293b;
  color: white;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  gap: 4px;
}

.video-thumb :deep(svg) {
  font-size: 22px;
  color: #facc15;
}

.video-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.video-play {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: radial-gradient(circle at 50% 50%, rgba(15, 23, 42, 0.05), rgba(15, 23, 42, 0.45));
}

.video-play :deep(svg) {
  font-size: 28px;
  color: rgba(255, 255, 255, 0.92);
  filter: drop-shadow(0 6px 10px rgba(0, 0, 0, 0.35));
}

.video-container {
  border-radius: 20px;
  overflow: hidden;
  background: #0f172a;
  border: 1px solid #e2e8f0;
}

.video-container video {
  width: 100%;
  display: block;
  max-height: 520px;
  object-fit: contain;
  background: #000;
}

.info-col {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.product-title {
  font-size: 24px;
  font-weight: 800;
  line-height: 1.3;
  color: #0f172a;
  letter-spacing: -0.3px;
}

.shop-row {
  display: grid;
  gap: 10px;
}

.shop-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.category-row {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 10px;
  align-items: baseline;
  padding: 10px 12px;
  border-radius: 18px;
  background: #f8fafc;
  border: 1px solid #e9eef3;
}

.cat-label {
  font-size: 12px;
  color: rgba(15, 23, 42, 0.55);
  font-weight: 900;
  letter-spacing: 0.04em;
}

.cat-value {
  font-size: 13px;
  color: rgba(15, 23, 42, 0.86);
  font-weight: 700;
  word-break: break-word;
}

.ai-title-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(239, 246, 255, 0.95), rgba(236, 253, 245, 0.95));
  border: 1px solid rgba(125, 211, 252, 0.32);
}

.ai-title-head {
  font-size: 13px;
  font-weight: 900;
  color: #0f172a;
  letter-spacing: 0.04em;
}

.ai-title-row {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 10px;
  align-items: start;
}

.ai-title-label {
  font-size: 12px;
  color: rgba(15, 23, 42, 0.55);
  font-weight: 800;
}

.ai-title-value {
  font-size: 13px;
  color: rgba(15, 23, 42, 0.88);
  font-weight: 700;
  word-break: break-word;
}

.product-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 18px;
  align-items: center;
  font-size: 14px;
  color: #475569;
}

.rating-badge {
  background: #ffedd5;
  color: #9a3412;
  padding: 4px 12px;
  border-radius: 40px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.rating-badge :deep(svg) {
  color: #f97316;
}

.badge-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.collect-badge {
  background: #fee2e2;
  color: #b91c1c;
  padding: 4px 12px;
  border-radius: 40px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.price-section {
  background: #f8fafc;
  border-radius: 28px;
  padding: 18px 20px;
  border: 1px solid #e9eef3;
}

.price-range {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}

.price-range .label {
  font-size: 15px;
  color: #64748b;
}

.price-range .value {
  font-size: 32px;
  font-weight: 900;
  color: #0f172a;
  line-height: 1;
}

.price-range .unit {
  font-size: 16px;
  color: #475569;
  margin-left: 4px;
}

.moq-info {
  display: flex;
  gap: 22px;
  margin-top: 16px;
  font-size: 14px;
  border-top: 1px dashed #cbd5e1;
  padding-top: 16px;
  flex-wrap: wrap;
}

.moq-info span {
  font-weight: 800;
  color: #1e293b;
  margin-right: 6px;
}

.sku-selector {
  background: #ffffff;
  border-radius: 20px;
  padding: 8px 0;
}

.sku-props {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sku-prop-name {
  font-weight: 800;
  font-size: 13px;
  color: #0f172a;
  margin-bottom: 8px;
}

.sku-prop-values {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.sku-value {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: #fff;
  cursor: pointer;
  font-size: 13px;
  color: #0f172a;
}

.sku-value img {
  width: 28px;
  height: 28px;
  border-radius: 10px;
  object-fit: cover;
  background: #f1f5f9;
}

.sku-value.active {
  border-color: rgba(37, 99, 235, 0.6);
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.12);
}

.sku-selected {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(37, 99, 235, 0.06), rgba(255, 255, 255, 0.8));
  border: 1px dashed rgba(37, 99, 235, 0.35);
}

.sku-selected-row {
  display: flex;
  gap: 10px;
  align-items: baseline;
  margin: 6px 0;
}

.sku-selected-row .k {
  width: 44px;
  color: rgba(15, 23, 42, 0.6);
  font-size: 12px;
}

.sku-selected-row .v {
  flex: 1;
  color: #0f172a;
  font-size: 13px;
  word-break: break-all;
}

.sku-title {
  font-weight: 800;
  margin-bottom: 12px;
  font-size: 16px;
  color: #334155;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.sku-list {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
}

.sku-item {
  background: #f1f5f9;
  border-radius: 18px;
  padding: 8px 10px;
  border: 1.5px solid transparent;
  font-size: 13px;
  font-weight: 600;
  transition: all 0.15s;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.02);
  display: grid;
  grid-template-columns: 56px 1fr;
  align-items: center;
  gap: 10px;
}

.sku-thumb {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.7);
  display: grid;
  place-items: center;
}

.sku-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.sku-thumb-empty {
  font-size: 11px;
  color: #64748b;
  font-weight: 700;
}

.sku-info {
  min-width: 0;
}

.sku-name {
  font-weight: 900;
  color: #0f172a;
  font-size: 12px;
  line-height: 1.15;
}

.sku-sub {
  margin-top: 4px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: baseline;
}

.sku-item .sku-price {
  color: #2563eb;
  font-weight: 900;
  font-size: 14px;
}

.sku-item .sku-stock {
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
}

.company-strip {
  background: #f0f9ff;
  border-radius: 20px;
  padding: 12px 14px;
  border: 1px solid #dbeafe;
}

.company-name {
  font-size: 15px;
  font-weight: 900;
  display: flex;
  align-items: center;
  gap: 8px;
}

.company-name :deep(svg) {
  color: #2563eb;
}

.company-meta {
  display: flex;
  flex-wrap: wrap;
  margin-top: 10px;
  gap: 12px;
  font-size: 13px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #334155;
}

.service-scores {
  display: flex;
  gap: 16px;
  margin-top: 10px;
  font-size: 13px;
  background: #ffffff;
  padding: 8px 10px;
  border-radius: 18px;
  flex-wrap: wrap;
}

.shipping-info {
  background: #f8fafc;
  border-radius: 20px;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
}

.shipping-line {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #0f172a;
}

.freight {
  background: #dcfce7;
  color: #166534;
  padding: 4px 16px;
  border-radius: 40px;
  font-weight: 800;
  font-size: 14px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.detail-section {
  margin-top: 40px;
  border-top: 2px solid #ecf3fa;
  padding-top: 32px;
}

.detail-layout {
  display: grid;
  grid-template-columns: 420px 1fr;
  gap: 26px;
  align-items: start;
}

.detail-images-col {
  align-self: start;
  position: static;
  max-height: none;
  overflow: visible;
  padding-right: 0;
}

.detail-images {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
  margin-top: 12px;
}

.detail-img-item {
  position: relative;
}

.detail-add-carousel {
  position: absolute;
  top: 10px;
  left: 10px;
  height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.18);
  background: rgba(255, 255, 255, 0.94);
  color: #0f172a;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 12px;
  font-weight: 800;
}

.detail-add-carousel:hover:not(:disabled) {
  background: #ffffff;
  border-color: rgba(37, 99, 235, 0.35);
  color: #1d4ed8;
}

.detail-add-carousel:disabled {
  cursor: not-allowed;
  color: #64748b;
}

.detail-translate {
  position: absolute;
  top: 10px;
  right: 46px;
  width: 32px;
  height: 32px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.18);
  background: rgba(255, 255, 255, 0.92);
  color: #0f172a;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.detail-translate:hover {
  background: #ffffff;
  border-color: rgba(37, 99, 235, 0.28);
}

.detail-del {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 32px;
  height: 32px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.18);
  background: rgba(255, 255, 255, 0.92);
  color: #ef4444;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.detail-del:hover {
  background: #ffffff;
  border-color: rgba(239, 68, 68, 0.35);
}

.detail-images img {
  width: 100%;
  border-radius: 18px;
  border: 1px solid #e9eef3;
  box-shadow: 0 8px 18px -10px rgba(0, 0, 0, 0.1);
  display: block;
}

.translate-modal {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.split-modal {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.split-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.split-title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.split-subtitle {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
}

.split-groups {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.split-group-card {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 12px;
  background: #f8fafc;
}

.split-group-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.split-group-meta {
  margin-top: 8px;
  color: #475569;
  font-size: 12px;
}

.split-sku-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 520px;
  overflow-y: auto;
  padding-right: 4px;
}

.split-sku-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  padding: 12px 14px;
}

.split-sku-main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1;
}

.split-sku-image {
  width: 68px;
  height: 68px;
  border-radius: 14px;
  object-fit: cover;
  background: #e2e8f0;
  flex: 0 0 68px;
}

.split-sku-image-empty {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
  font-size: 12px;
}

.split-sku-info {
  min-width: 0;
}

.split-sku-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  word-break: break-word;
}

.split-sku-meta {
  margin-top: 4px;
  color: #475569;
  font-size: 12px;
  word-break: break-word;
}

.split-sku-select {
  width: 220px;
  flex: 0 0 220px;
}

@media (max-width: 900px) {
  .split-toolbar {
    flex-direction: column;
  }

  .split-sku-item {
    flex-direction: column;
    align-items: stretch;
  }

  .split-sku-select {
    width: 100%;
    flex-basis: auto;
  }
}

.translate-controls {
  border: 1px solid #e7eef6;
  border-radius: 16px;
  padding: 12px 12px;
  background: #f8fafc;
}

.tc-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.tc-label {
  font-size: 12px;
  font-weight: 900;
  color: rgba(15, 23, 42, 0.64);
  letter-spacing: 0.02em;
}

.tc-actions {
  margin-left: auto;
  display: inline-flex;
  gap: 10px;
}

.tc-hint {
  margin-top: 8px;
  font-size: 12px;
  color: rgba(15, 23, 42, 0.62);
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
}

.tc-error {
  margin-top: 8px;
  font-size: 12px;
  color: #b91c1c;
}

.translate-compare {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.img-box {
  border: 1px solid #eef2f7;
  border-radius: 18px;
  background: #ffffff;
  overflow: hidden;
}

.img-title {
  padding: 10px 12px;
  font-size: 12px;
  font-weight: 900;
  color: rgba(15, 23, 42, 0.72);
  background: linear-gradient(180deg, #f8fafc, #ffffff);
  border-bottom: 1px solid #eef2f7;
}

.img-frame {
  width: 100%;
  aspect-ratio: 4 / 3;
  background: #0b1220;
  display: flex;
  align-items: center;
  justify-content: center;
}

.img-frame img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #0b1220;
}

.img-placeholder {
  color: rgba(255, 255, 255, 0.72);
  font-weight: 700;
}

@media (max-width: 820px) {
  .translate-compare {
    grid-template-columns: 1fr;
  }
}

.detail-meta-col {
  min-width: 0;
}

.section-title {
  font-size: 22px;
  font-weight: 900;
  margin-bottom: 18px;
  display: flex;
  align-items: center;
  gap: 10px;
  color: #0f172a;
}

.section-title :deep(svg) {
  color: #2563eb;
}

.attr-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 12px 16px;
  background: #f9fcff;
  padding: 18px;
  border-radius: 28px;
  border: 1px solid #e2edf7;
}

.temu-sku-table {
  border: 1px solid #e2edf7;
  border-radius: 18px;
  overflow: hidden;
  background: #ffffff;
}

.temu-sku-head {
  display: grid;
  grid-template-columns: 1.2fr 0.6fr 0.5fr 0.7fr;
  gap: 10px;
  padding: 10px 12px;
  font-size: 12px;
  font-weight: 900;
  color: rgba(15, 23, 42, 0.68);
  background: #f9fcff;
  border-bottom: 1px solid #e2edf7;
}

.temu-sku-row {
  display: grid;
  grid-template-columns: 1.2fr 0.6fr 0.5fr 0.7fr;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px dashed #e5eaf2;
  font-size: 12px;
  color: rgba(15, 23, 42, 0.86);
}

.temu-sku-row:last-child {
  border-bottom: 0;
}

.attr-item {
  display: flex;
  justify-content: space-between;
  border-bottom: 1px dashed #cbd5e1;
  padding-bottom: 8px;
  font-size: 13px;
  gap: 12px;
}

.attr-label {
  color: #475569;
  font-weight: 700;
}

.attr-value {
  font-weight: 800;
  color: #0f172a;
  max-width: 60%;
  text-align: right;
  word-break: break-word;
}

.empty-block {
  padding: 16px;
  border-radius: 18px;
  border: 1px dashed #cbd5e1;
  color: #64748b;
  background: #ffffff;
}

.packaging-info {
  display: flex;
  flex-wrap: wrap;
  gap: 18px 32px;
  background: #f1f4f9;
  border-radius: 24px;
  padding: 18px 22px;
  margin: 24px 0 22px;
  font-weight: 700;
}

.packaging-info span {
  font-weight: 900;
  color: #1e293b;
  margin-left: 6px;
}


@media (max-width: 980px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }
  .detail-images-col {
    position: static;
    max-height: none;
    overflow: visible;
    padding-right: 0;
  }
}

@media (max-width: 1200px) {
  .detail-layout {
    grid-template-columns: 360px 1fr;
  }
}

.footer-note {
  margin-top: 40px;
  color: #94a3b8;
  font-size: 14px;
  text-align: center;
  border-top: 1px solid #e9edf2;
  padding-top: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.raw {
  display: grid;
  gap: 10px;
}

.raw-row {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 12px;
  padding: 12px;
  border-radius: 16px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(15, 23, 42, 0.02);
}

.rk {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 12px;
  color: rgba(15, 23, 42, 0.65);
}

.rv {
  color: rgba(15, 23, 42, 0.86);
  word-break: break-word;
}

.pre {
  margin: 0;
  padding: 10px;
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.03);
  border: 1px solid rgba(15, 23, 42, 0.06);
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 12px;
}

@media (max-width: 640px) {
  .container {
    padding: 18px 16px 26px;
  }
  .pc-head {
    flex-direction: column;
    align-items: stretch;
  }
  .head-right {
    justify-content: flex-start;
  }
  .price-range .value {
    font-size: 28px;
  }
}

@media (max-width: 880px) {
  .media-grid {
    grid-template-columns: 72px 1fr;
  }
  .video-container video {
    max-height: 420px;
  }
}

/* --- Fuse Modal (rewrite) --- */
.fuse-wrap {
  display: grid;
  gap: 12px;
  max-width: 100%;
  overflow-x: hidden;
}

.fuse-strip {
  border: 1px solid rgba(15, 23, 42, 0.10);
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.02);
  padding: 10px 10px 8px;
  max-width: 100%;
  overflow: hidden;
}

.fuse-strip-head {
  display: none;
}


.fuse-strip-row {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 6px;
  -webkit-overflow-scrolling: touch;
  scroll-snap-type: x mandatory;
  max-width: 100%;
  width: 100%;
  min-width: 0;
}

.fuse-strip-row::-webkit-scrollbar {
  height: 6px;
}

.fuse-strip-row::-webkit-scrollbar-thumb {
  background: rgba(15, 23, 42, 0.18);
  border-radius: 999px;
}

.fuse-strip-row::-webkit-scrollbar-track {
  background: rgba(15, 23, 42, 0.06);
  border-radius: 999px;
}

.fuse-strip-item {
  width: 104px;
  height: 104px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: #fff;
  overflow: hidden;
  flex: 0 0 auto;
  cursor: pointer;
  padding: 0;
  scroll-snap-align: start;
}

.fuse-strip-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.fuse-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 12px;
  align-items: start;
  min-width: 0;
}

.fuse-left,
.fuse-right {
  min-width: 0;
}

.fuse-right {
  position: sticky;
  top: 0;
}

.fuse-block-title {
  font-weight: 900;
  color: rgba(15, 23, 42, 0.9);
  margin: 2px 0 8px;
}

.fuse-picked {
  border: 1px solid rgba(15, 23, 42, 0.10);
  border-radius: 14px;
  background: #fff;
  padding: 10px;
}

.fuse-empty {
  color: rgba(15, 23, 42, 0.55);
  padding: 8px;
}

.fuse-picked-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 10px;
}

.fuse-picked-item {
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 14px;
  overflow: hidden;
  background: rgba(15, 23, 42, 0.02);
}

.fuse-picked-item img {
  width: 100%;
  height: 140px;
  object-fit: cover;
  background: #fff;
  display: block;
}

.fuse-picked-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
}

.fuse-picked-idx {
  font-size: 12px;
  color: rgba(15, 23, 42, 0.6);
  font-weight: 800;
}

.fuse-picked-remove {
  border: 1px solid rgba(239, 68, 68, 0.25);
  background: rgba(239, 68, 68, 0.06);
  color: rgba(239, 68, 68, 0.95);
  border-radius: 10px;
  padding: 6px 10px;
  cursor: pointer;
}

.fuse-form {
  margin-top: 12px;
  border: 1px solid rgba(15, 23, 42, 0.10);
  border-radius: 14px;
  background: #fff;
  padding: 12px;
  display: grid;
  gap: 10px;
}

.fuse-field {
  display: grid;
  gap: 6px;
}

.fuse-label {
  font-weight: 900;
  color: rgba(15, 23, 42, 0.75);
}

.fuse-params {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  padding-top: 6px;
}

.fuse-param {
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 12px;
  padding: 10px;
  background: rgba(15, 23, 42, 0.02);
}

.fuse-param-label {
  font-size: 12px;
  font-weight: 900;
  color: rgba(15, 23, 42, 0.65);
  margin-bottom: 6px;
}

.fuse-param-val {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.fuse-x {
  opacity: 0.6;
}

.fuse-preview {
  border: 1px solid rgba(15, 23, 42, 0.10);
  border-radius: 14px;
  background: #fff;
  overflow: hidden;
  min-height: 320px;
  display: grid;
  place-items: center;
}

.fuse-preview-img {
  width: 100%;
  height: 100%;
  max-height: 520px;
  object-fit: contain;
  background: #fff;
}

.fuse-preview-empty {
  padding: 18px;
  color: rgba(15, 23, 42, 0.55);
  text-align: center;
}

.fuse-preview-loading {
  font-weight: 900;
  color: rgba(15, 23, 42, 0.7);
}

.fuse-preview-meta {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-top: 8px;
}

.fuse-preview-meta a {
  color: rgba(37, 99, 235, 0.95);
  font-weight: 900;
}

.fuse-link-btn {
  border: 1px solid rgba(37, 99, 235, 0.22);
  background: rgba(37, 99, 235, 0.06);
  color: rgba(37, 99, 235, 0.95);
  border-radius: 10px;
  padding: 6px 10px;
  cursor: pointer;
}

.fuse-preview-url {
  margin-top: 6px;
  font-size: 12px;
  color: rgba(15, 23, 42, 0.55);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fuse-footer {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.fuse-footer-left {
  min-width: 0;
  max-width: 55%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fuse-footer-tip {
  color: rgba(15, 23, 42, 0.55);
}

.fuse-footer-error {
  color: rgba(239, 68, 68, 0.95);
  font-weight: 900;
}

.fuse-footer-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

@media (max-width: 980px) {
  .fuse-main {
    grid-template-columns: 1fr;
  }
  .fuse-right {
    position: static;
  }
  .fuse-params {
    grid-template-columns: 1fr;
  }
}
</style>
