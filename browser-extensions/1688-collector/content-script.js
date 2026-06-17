(() => {
  const PANEL_ID = 'c1688-collector-panel';
  const STORAGE_ENV_KEY = 'TMINOS_COLLECTOR_ENV_KEY';
  const STORAGE_AUTH_KEY = 'TMINOS_COLLECTOR_AUTH_BY_ENV';
  const STORAGE_TARGET_SHOPS_KEY = 'TMINOS_COLLECTOR_TARGET_SHOPS_BY_ENV';

  const DEFAULTS = globalThis.TMINOS_COLLECTOR_DEFAULTS || {
    baseUrl: 'https://www.tminos.com',
    endpointPath: '/api/platform/product-collections/import'
  };

  if (document.getElementById(PANEL_ID)) return;

  const state = {
    activeEnvironmentKey: '',
    authByEnv: {},
    selectedShopIdsByEnv: {},
    availableShops: [],
    minimized: false,
    dragging: false,
    dragOffsetX: 0,
    dragOffsetY: 0,
    loadingShops: false,
    submittingLogin: false
  };

  function setToastState(node, text, kind = 'neutral') {
    if (!node) return;
    node.textContent = text;
    node.classList.remove('is-neutral', 'is-loading', 'is-success', 'is-error');
    node.classList.add(`is-${kind}`);
  }

  function el(tag, attrs = {}, children = []) {
    const node = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs)) {
      if (k === 'class') node.className = v;
      else if (k === 'text') node.textContent = v;
      else if (k.startsWith('on') && typeof v === 'function') node.addEventListener(k.slice(2), v);
      else node.setAttribute(k, String(v));
    }
    for (const child of children) {
      if (typeof child === 'string') node.appendChild(document.createTextNode(child));
      else if (child) node.appendChild(child);
    }
    return node;
  }

  function normalizeBaseUrl(baseUrl) {
    return String(baseUrl || '').replace(/\/+$/, '');
  }

  function getEnvironments() {
    return DEFAULTS.environments || {};
  }

  function getAvailableEnvironments() {
    const environments = Object.entries(getEnvironments())
      .filter(([, env]) => env && env.baseUrl)
      .map(([key, env]) => ({
        key,
        label: env.label || key,
        baseUrl: normalizeBaseUrl(env.baseUrl)
      }));

    if (environments.length > 0) return environments;

    return [{
      key: 'default',
      label: '默认环境',
      baseUrl: normalizeBaseUrl(DEFAULTS.baseUrl)
    }];
  }

  function getDefaultEnvironmentKey() {
    const environments = getAvailableEnvironments();
    const configuredKey = DEFAULTS.defaultEnvironmentKey;

    if (environments.some((env) => env.key === configuredKey)) {
      return configuredKey;
    }

    return environments[0]?.key || 'default';
  }

  function getEnvironmentConfig(environmentKey = state.activeEnvironmentKey) {
    const environments = getAvailableEnvironments();
    return environments.find((env) => env.key === environmentKey)
      || environments.find((env) => env.key === getDefaultEnvironmentKey())
      || environments[0]
      || { key: 'default', label: '默认环境', baseUrl: normalizeBaseUrl(DEFAULTS.baseUrl) };
  }

  function getStorageArea() {
    return chrome?.storage?.local || null;
  }

  function loadStoredEnvironmentKey() {
    return new Promise((resolve) => {
      const storageArea = getStorageArea();
      if (!storageArea) {
        resolve('');
        return;
      }

      storageArea.get([STORAGE_ENV_KEY], (result) => {
        if (chrome.runtime.lastError) {
          resolve('');
          return;
        }

        resolve(typeof result?.[STORAGE_ENV_KEY] === 'string' ? result[STORAGE_ENV_KEY] : '');
      });
    });
  }

  function persistEnvironmentKey(environmentKey) {
    return new Promise((resolve) => {
      const storageArea = getStorageArea();
      if (!storageArea) {
        resolve(false);
        return;
      }

      storageArea.set({ [STORAGE_ENV_KEY]: environmentKey }, () => {
        resolve(!chrome.runtime.lastError);
      });
    });
  }

  function loadStoredJson(key, fallbackValue) {
    return new Promise((resolve) => {
      const storageArea = getStorageArea();
      if (!storageArea) {
        resolve(fallbackValue);
        return;
      }

      storageArea.get([key], (result) => {
        if (chrome.runtime.lastError) {
          resolve(fallbackValue);
          return;
        }
        resolve(result?.[key] ?? fallbackValue);
      });
    });
  }

  function persistStoredJson(key, value) {
    return new Promise((resolve) => {
      const storageArea = getStorageArea();
      if (!storageArea) {
        resolve(false);
        return;
      }

      storageArea.set({ [key]: value }, () => {
        resolve(!chrome.runtime.lastError);
      });
    });
  }

  function getAuthForEnvironment(environmentKey = state.activeEnvironmentKey) {
    return state.authByEnv?.[environmentKey] || null;
  }

  async function setAuthForEnvironment(environmentKey, auth) {
    state.authByEnv = {
      ...(state.authByEnv || {}),
      [environmentKey]: auth
    };
    return persistStoredJson(STORAGE_AUTH_KEY, state.authByEnv);
  }

  async function clearAuthForEnvironment(environmentKey = state.activeEnvironmentKey) {
    const next = { ...(state.authByEnv || {}) };
    delete next[environmentKey];
    state.authByEnv = next;
    return persistStoredJson(STORAGE_AUTH_KEY, state.authByEnv);
  }

  function getSelectedShopIdsForEnvironment(environmentKey = state.activeEnvironmentKey) {
    return Array.isArray(state.selectedShopIdsByEnv?.[environmentKey])
      ? state.selectedShopIdsByEnv[environmentKey]
      : [];
  }

  async function setSelectedShopIdsForEnvironment(environmentKey, shopIds) {
    state.selectedShopIdsByEnv = {
      ...(state.selectedShopIdsByEnv || {}),
      [environmentKey]: Array.isArray(shopIds) ? shopIds : []
    };
    return persistStoredJson(STORAGE_TARGET_SHOPS_KEY, state.selectedShopIdsByEnv);
  }

  function getBaseUrl() {
    return getEnvironmentConfig().baseUrl;
  }

  function getEndpointUrl(baseUrl) {
    return `${normalizeBaseUrl(baseUrl)}${DEFAULTS.endpointPath}`;
  }

  function getCardLinkEndpointUrl(baseUrl) {
    return `${normalizeBaseUrl(baseUrl)}${DEFAULTS.cardLinkEndpointPath || '/api/platform/alibaba1688-card-links/import'}`;
  }

  function getLoginUrl(baseUrl) {
    return `${normalizeBaseUrl(baseUrl)}${DEFAULTS.authLoginPath || '/api/auth/login'}`;
  }

  function getShopListUrl(baseUrl) {
    const base = `${normalizeBaseUrl(baseUrl)}${DEFAULTS.shopListPath || '/api/platform/temu-shops'}`;
    return `${base}${base.includes('?') ? '&' : '?'}enabled=true`;
  }

  function getSiteProfiles() {
    return DEFAULTS.siteProfiles || {};
  }

  function matchHostname(hostname, patterns = []) {
    const host = String(hostname || '').toLowerCase();
    return patterns.some((pattern) => host === pattern || host.endsWith(`.${pattern}`));
  }

  function detectSiteKey() {
    const host = location.hostname.toLowerCase();
    const profiles = getSiteProfiles();

    for (const [siteKey, profile] of Object.entries(profiles)) {
      if (siteKey === 'generic') continue;
      if (matchHostname(host, profile?.hostPatterns || [])) return siteKey;
    }

    return 'generic';
  }

  function getSiteProfile(siteKey = detectSiteKey()) {
    const profiles = getSiteProfiles();
    return profiles[siteKey] || profiles.generic || {};
  }

  function getHostLabel(baseUrl) {
    try {
      return new URL(baseUrl).host;
    } catch {
      return String(baseUrl || '').replace(/^https?:\/\//i, '').replace(/\/+$/, '') || '-';
    }
  }

  function cloneFullHtml() {
    const doctype = document.doctype
      ? `<!DOCTYPE ${document.doctype.name}${document.doctype.publicId ? ` PUBLIC "${document.doctype.publicId}"` : ''}${document.doctype.systemId ? ` "${document.doctype.systemId}"` : ''}>\n`
      : '<!DOCTYPE html>\n';

    const VOID_TAGS = new Set([
      'area','base','br','col','embed','hr','img','input',
      'link','meta','param','source','track','wbr'
    ]);

    function escAttr(v) {
      return v.replace(/&/g, '&amp;').replace(/"/g, '&quot;');
    }

    function serializeChildren(parent) {
      let out = '';
      for (const child of parent.childNodes) out += serializeNode(child);
      return out;
    }

    function serializeNode(node) {
      if (node.nodeType === Node.TEXT_NODE) return node.textContent || '';
      if (node.nodeType === Node.COMMENT_NODE) return `<!--${node.data}-->`;
      if (node.nodeType !== Node.ELEMENT_NODE) return '';

      const tag = node.tagName.toLowerCase();
      let html = `<${tag}`;
      for (const attr of node.attributes) {
        html += ` ${attr.name}="${escAttr(attr.value)}"`;
      }
      html += '>';

      if (VOID_TAGS.has(tag)) return html;

      // 递归序列化 shadow DOM，以 declarative shadow DOM 格式内联
      if (node.shadowRoot) {
        html += '<template shadowrootmode="open">';
        html += serializeChildren(node.shadowRoot);
        html += '</template>';
      }

      html += serializeChildren(node);
      html += `</${tag}>`;
      return html;
    }

    return doctype + serializeNode(document.documentElement);
  }

  function waitForPageComplete() {
    return new Promise((resolve) => {
      if (document.readyState === 'complete') {
        resolve();
        return;
      }

      const onReadyStateChange = () => {
        if (document.readyState === 'complete') {
          document.removeEventListener('readystatechange', onReadyStateChange);
          resolve();
        }
      };

      document.addEventListener('readystatechange', onReadyStateChange);
    });
  }

  function scrollToTriggerLazyLoad() {
    return new Promise((resolve) => {
      const totalHeight = document.documentElement.scrollHeight;
      const viewportHeight = window.innerHeight;
      const scrollStep = Math.max(viewportHeight * 0.6, 300);
      let currentY = 0;
      const originalY = window.scrollY;

      function step() {
        currentY += scrollStep;
        if (currentY >= totalHeight) {
          // 滚到底后等待 DOM 稳定（新元素可能追加），再回到原位
          window.scrollTo(0, totalHeight);
          waitForDomSettle(1200).then(() => {
            window.scrollTo(0, originalY);
            resolve();
          });
          return;
        }
        window.scrollTo(0, currentY);
        setTimeout(step, 120);
      }

      if (totalHeight <= viewportHeight * 1.5) {
        // 页面不长，不需要滚动
        resolve();
        return;
      }

      step();
    });
  }

  function waitForDomSettle(timeoutMs = 1200) {
    return new Promise((resolve) => {
      let timer = null;
      const observer = new MutationObserver(() => {
        clearTimeout(timer);
        timer = setTimeout(() => {
          observer.disconnect();
          resolve();
        }, 400);
      });

      observer.observe(document.body, {
        childList: true,
        subtree: true,
        attributes: false,
        characterData: false
      });

      // 如果在超时内 DOM 一直没变化，也直接结束
      timer = setTimeout(() => {
        observer.disconnect();
        resolve();
      }, timeoutMs);
    });
  }

  function pickText(selectors = []) {
    for (const selector of selectors) {
      const node = document.querySelector(selector);
      const text = node?.textContent?.trim();
      if (text) return text;
    }
    return '';
  }

  function pickMeta(name) {
    return (
      document.querySelector(`meta[name="${name}"]`)?.getAttribute('content')?.trim()
      || document.querySelector(`meta[property="${name}"]`)?.getAttribute('content')?.trim()
      || ''
    );
  }

  function parseJsonSafely(text) {
    try {
      return JSON.parse(text);
    } catch {
      return null;
    }
  }

  function flattenJsonLdNodes(input) {
    if (!input) return [];
    if (Array.isArray(input)) return input.flatMap((item) => flattenJsonLdNodes(item));
    if (typeof input !== 'object') return [];

    const nodes = [input];
    if (Array.isArray(input['@graph'])) nodes.push(...flattenJsonLdNodes(input['@graph']));
    if (input.mainEntity) nodes.push(...flattenJsonLdNodes(input.mainEntity));
    if (input.itemListElement) nodes.push(...flattenJsonLdNodes(input.itemListElement));
    return nodes;
  }

  function pickJsonLdValue(value) {
    if (Array.isArray(value)) {
      for (const item of value) {
        const picked = pickJsonLdValue(item);
        if (picked) return picked;
      }
      return '';
    }

    if (value && typeof value === 'object') {
      return String(
        value.name
        || value.value
        || value.url
        || value['@id']
        || value.text
        || ''
      ).trim();
    }

    return typeof value === 'string' || typeof value === 'number'
      ? String(value).trim()
      : '';
  }

  function normalizeImageValue(value) {
    if (!value) return [];
    if (Array.isArray(value)) return value.flatMap((item) => normalizeImageValue(item));
    if (typeof value === 'string') {
      const url = normalizeUrl(value);
      return url && /^https?:\/\//i.test(url) ? [url] : [];
    }
    if (typeof value === 'object') {
      return normalizeImageValue(value.url || value.contentUrl || value['@id']);
    }
    return [];
  }

  function getJsonLdProductData(jsonLdList = []) {
    const nodes = jsonLdList.flatMap((item) => flattenJsonLdNodes(item));
    const productNode = nodes.find((node) => {
      const type = node?.['@type'];
      if (Array.isArray(type)) return type.some((entry) => String(entry).toLowerCase() === 'product');
      return String(type || '').toLowerCase() === 'product';
    });

    if (!productNode) return null;

    const offerNode = Array.isArray(productNode.offers)
      ? productNode.offers.find(Boolean)
      : productNode.offers;

    return {
      title: pickJsonLdValue(productNode.name),
      description: pickJsonLdValue(productNode.description),
      seller: pickJsonLdValue(productNode.brand) || pickJsonLdValue(productNode.manufacturer),
      price: pickJsonLdValue(offerNode?.price),
      productId: pickJsonLdValue(productNode.sku) || pickJsonLdValue(productNode.productID),
      images: normalizeImageValue(productNode.image),
      sales: pickJsonLdValue(productNode.aggregateRating?.reviewCount)
    };
  }

  function pickFirstText(values = []) {
    for (const value of values) {
      const text = String(value || '').trim();
      if (text) return text;
    }
    return '';
  }

  function getCanonicalUrl() {
    return normalizeUrl(
      document.querySelector('link[rel="canonical"]')?.getAttribute('href')
      || pickMeta('og:url')
      || ''
    );
  }

  function extractProductIdFromPattern(pattern, sources = []) {
    try {
      const regex = new RegExp(pattern, 'i');
      for (const source of sources) {
        const text = String(source || '');
        const match = text.match(regex);
        if (match?.[1]) return match[1].trim();
      }
    } catch {
      return '';
    }
    return '';
  }

  function getProductId(siteProfile, jsonLdProduct) {
    const sources = [location.href, getCanonicalUrl(), pickMeta('og:url')].filter(Boolean);
    for (const pattern of siteProfile?.productIdPatterns || []) {
      const id = extractProductIdFromPattern(pattern, sources);
      if (id) return id;
    }

    if (jsonLdProduct?.productId) return jsonLdProduct.productId;
    return '';
  }

  function isLikelyProductPage(siteProfile, jsonLdProduct) {
    if (jsonLdProduct?.title) return true;
    if (getProductId(siteProfile, jsonLdProduct)) return true;

    const title = pickText(siteProfile?.titleSelectors || []);
    const price = pickText(siteProfile?.priceSelectors || []);
    return Boolean(title && price);
  }

  function isUsefulImageUrl(url) {
    const value = String(url || '').trim();
    if (!value || !/^https?:\/\//i.test(value)) return false;
    if (/\b(sprite|spacer|icon|logo|avatar|loading|placeholder)\b/i.test(value)) return false;
    return true;
  }

  function normalizeUrl(raw) {
    if (!raw) return '';
    try {
      return new URL(raw, location.href).href;
    } catch {
      return String(raw || '').trim();
    }
  }

  function collectUrls(selectors = [], attrs = ['src', 'href']) {
    const set = new Set();
    for (const selector of selectors) {
      const nodes = document.querySelectorAll(selector);
      for (const node of nodes) {
        for (const attr of attrs) {
          const value = node.getAttribute?.(attr) || (attr in node ? node[attr] : '');
          const url = normalizeUrl(String(value || '').trim());
          if (url && /^https?:\/\//i.test(url)) set.add(url);
        }
      }
    }
    return Array.from(set);
  }

  function collectImageUrlsFromRoot(root) {
    if (!root || typeof root.querySelectorAll !== 'function') return [];

    const attrs = ['src', 'data-src', 'data-lazy-src', 'data-ks-lazyload', 'data-original'];
    const set = new Set();

    function walk(node) {
      if (!node) return;
      const imgs = node.querySelectorAll ? node.querySelectorAll('img') : [];
      for (const img of imgs) {
        for (const attr of attrs) {
          const value = img.getAttribute?.(attr) || (attr in img ? img[attr] : '');
          const url = normalizeUrl(String(value || '').trim());
          if (url && /^https?:\/\//i.test(url)) set.add(url);
        }
      }
      // 递归进入所有 shadow root
      const elements = node.querySelectorAll ? node.querySelectorAll('*') : [];
      for (const el of elements) {
        if (el.shadowRoot) walk(el.shadowRoot);
      }
    }

    walk(root);
    return Array.from(set);
  }

  function extractDetailImagesFromHtmlDescription(siteProfile = {}) {
    const set = new Set();
    const detailSelectors = siteProfile?.detailImageContainers?.length
      ? siteProfile.detailImageContainers
      : ['.html-description'];
    const containers = document.querySelectorAll(detailSelectors.join(', '));

    const collectFromDetailOnly = (root) => {
      if (!root || typeof root.querySelectorAll !== 'function') return;
      for (const url of collectImageUrlsFromRoot(root)) set.add(url);
      const detailRoots = [];

      if (root.id === 'detail') detailRoots.push(root);
      for (const node of root.querySelectorAll('#detail')) {
        detailRoots.push(node);
      }

      for (const detailRoot of detailRoots) {
        for (const url of collectImageUrlsFromRoot(detailRoot)) set.add(url);
      }
    };

    for (const container of containers) {
      // 1) 仅取 #detail 下的 img
      collectFromDetailOnly(container);

      // 2) declarative shadow DOM 转为 open shadowRoot 后的内容
      if (container.shadowRoot) {
        collectFromDetailOnly(container.shadowRoot);
      }

      // 3) 若页面仍保留 template[shadowrootmode]，解析其内容
      const templates = container.querySelectorAll('template[shadowrootmode]');
      for (const tpl of templates) {
        const fragment = tpl.content;
        if (fragment) {
          collectFromDetailOnly(fragment);
        }
      }
    }

    return Array.from(set);
  }

  function extractSkuList() {
    const skuRows = [];

    const rows = document.querySelectorAll(
      '.sku-item, .sku-row, .prop-item, .spec-item, [class*="sku"], [class*="spec"]'
    );

    for (const row of rows) {
      const name = row.querySelector('.name, .label, .title, [class*="name"], [class*="label"]')?.textContent?.trim()
        || row.getAttribute('data-name')
        || '';

      const value = row.querySelector('.value, .desc, .text, [class*="value"], [class*="desc"]')?.textContent?.trim()
        || row.getAttribute('data-value')
        || '';

      const price = row.querySelector('.price, [class*="price"]')?.textContent?.trim() || '';
      const stock = row.querySelector('.stock, [class*="stock"], [class*="inventory"]')?.textContent?.trim() || '';

      if (name || value || price || stock) {
        skuRows.push({ name, value, price, stock });
      }
    }

    return skuRows.slice(0, 200);
  }

  function extractSpecs() {
    const specs = [];

    const kvRows = document.querySelectorAll(
      '.detail-attr-item, .product-attribute-item, .attributes-item, tr, li'
    );

    for (const row of kvRows) {
      const key = row.querySelector('th, .name, .label, dt, .attr-name')?.textContent?.trim() || '';
      const value = row.querySelector('td, .value, .text, dd, .attr-value')?.textContent?.trim() || '';
      if (key && value) specs.push({ key, value });
    }

    return specs.slice(0, 300);
  }

  function extractMinOrder() {
    const text = pickText([
      '.moq',
      '.min-order',
      '.start-qty',
      '[class*="moq"]',
      '[class*="min-order"]',
      '[class*="start"]'
    ]);

    if (text) return text;

    const bodyText = document.body?.innerText || '';
    const m = bodyText.match(/(起批量|最小起订量|最小订购量)\s*[:：]?\s*([^\n]{1,40})/i);
    return m?.[2]?.trim() || '';
  }

  function extractStructuredData() {
    const siteKey = detectSiteKey();
    const siteProfile = getSiteProfile(siteKey);

    const jsonLd = Array.from(document.querySelectorAll('script[type="application/ld+json"]'))
      .map((s) => parseJsonSafely(s.textContent || ''))
      .filter(Boolean)
      .slice(0, 10);

    const jsonLdProduct = getJsonLdProductData(jsonLd);

    const title = pickFirstText([
      pickMeta('og:title'),
      jsonLdProduct?.title,
      pickText(siteProfile.titleSelectors || []),
      document.title
    ]);

    const price = pickFirstText([
      pickMeta('product:price:amount'),
      jsonLdProduct?.price,
      pickText(siteProfile.priceSelectors || [])
    ]);

    const seller = pickFirstText([
      pickMeta('og:site_name'),
      jsonLdProduct?.seller,
      pickText(siteProfile.sellerSelectors || [])
    ]);

    const sales = pickFirstText([
      jsonLdProduct?.sales,
      pickText(siteProfile.salesSelectors || [])
    ]);

    const thumbnailImages = collectUrls(
      siteProfile.thumbnailImageSelectors || [],
      ['src', 'data-src', 'data-lazy-src']
    ).slice(0, 120);

    const carouselImages = collectUrls(
      siteProfile.carouselImageSelectors || [],
      ['src', 'data-src', 'data-lazy-src']
    ).slice(0, 160);

    const detailImages = extractDetailImagesFromHtmlDescription(siteProfile).slice(0, 300);

    const allImages = Array.from(new Set([
      ...(jsonLdProduct?.images || []),
      ...thumbnailImages,
      ...carouselImages,
      ...detailImages,
      ...collectUrls(['img'], ['src', 'data-src', 'data-lazy-src']).slice(0, 300)
    ].filter(isUsefulImageUrl))).slice(0, 600);

    const videos = collectUrls(
      ['video', 'video source', '.video source', '[class*="video"] source', 'a[href*=".mp4"]'],
      ['src', 'href', 'data-src']
    ).slice(0, 60);

    const minOrderQty = extractMinOrder();
    const skuList = extractSkuList();
    const specs = extractSpecs();
    const productIdFromUrl = getProductId(siteProfile, jsonLdProduct);

    const data = {
      site: siteKey,
      pageUrl: location.href,
      canonicalUrl: getCanonicalUrl(),
      title,
      price,
      seller,
      sales,
      productId: productIdFromUrl,
      minOrderQty,
      description: pickFirstText([
        pickMeta('description'),
        pickMeta('og:description'),
        jsonLdProduct?.description
      ]),
      keywords: pickMeta('keywords'),
      thumbnailImages,
      carouselImages,
      detailImages,
      videos,
      imageUrls: allImages,
      skuList,
      specs,
      jsonLd,
      collectedAt: new Date().toISOString()
    };

    return JSON.stringify(data);
  }

  function get1688SearchResultRoot() {
    const candidates = [
      '.feeds-wrapper[data-spm="offerlist"]',
      '.feeds-wrapper',
      '[data-spm="offerlist"]',
      '.common-offer-list-wrapper',
      '.offer-list-row'
    ];

    for (const selector of candidates) {
      const node = document.querySelector(selector);
      if (node) return node;
    }

    return document.body;
  }

  function parseJsonObjectSafely(text) {
    if (!text) return null;
    try {
      const parsed = JSON.parse(text);
      return parsed && typeof parsed === 'object' ? parsed : null;
    } catch {
      return null;
    }
  }

  function get1688OfferIdFromRenderKey(renderKey) {
    const match = String(renderKey || '').match(/_(\d{6,})$/);
    return match?.[1] || '';
  }

  function get1688OfferIdFromHref(href) {
    const normalizedHref = normalizeUrl(href);
    if (!normalizedHref) return '';

    try {
      const url = new URL(normalizedHref, location.href);
      const queryOfferId = url.searchParams.get('offerId') || url.searchParams.get('id');
      if (queryOfferId) return queryOfferId.trim();
    } catch {
      // ignore
    }

    const patterns = [
      /(?:offer|product)\/(\d+)\.html/i,
      /[?&]offerId=(\d+)/i,
      /[?&]id=(\d+)/i
    ];
    for (const pattern of patterns) {
      const match = normalizedHref.match(pattern);
      if (match?.[1]) return match[1].trim();
    }
    return '';
  }

  function build1688DetailUrl(offerId) {
    const normalizedOfferId = String(offerId || '').trim();
    if (!normalizedOfferId) return '';
    return `https://detail.m.1688.com/page/index.html?offerId=${encodeURIComponent(normalizedOfferId)}`;
  }

  function extractOfferIdFromWwLink(cardRoot) {
    const wwLink = cardRoot?.querySelector?.('a.ww-link[data-extra]');
    const extra = parseJsonObjectSafely(wwLink?.getAttribute('data-extra'));
    return String(extra?.offerId || '').trim();
  }

  function dedupeCardLinks(rows) {
    const seen = new Set();
    const deduped = [];

    for (const row of rows) {
      const key = [
        row.type,
        row.offerId || '',
        row.detailUrl || '',
        row.cardHref || '',
        row.renderKey || '',
        row.index || ''
      ].join('|');
      if (seen.has(key)) continue;
      seen.add(key);
      deduped.push(row);
    }

    return deduped;
  }

  function collect1688SearchCardLinks() {
    const root = get1688SearchResultRoot();
    const rows = [];

    const normalCards = Array.from(root.querySelectorAll(
      'a.search-offer-wrapper.search-offer-item.major-offer[href]'
    ));

    for (const card of normalCards) {
      const cardHref = normalizeUrl(card.getAttribute('href') || card.href);
      const renderKey = String(card.getAttribute('data-renderkey') || '').trim();
      const offerId = get1688OfferIdFromHref(cardHref)
        || get1688OfferIdFromRenderKey(renderKey)
        || extractOfferIdFromWwLink(card);

      rows.push({
        type: 'normal',
        offerId: offerId || null,
        detailUrl: cardHref || (offerId ? build1688DetailUrl(offerId) : null),
        cardHref: cardHref || null,
        renderKey: renderKey || null,
        index: String(card.getAttribute('data-index') || '').trim() || null,
        offerIdSource: offerId
          ? (get1688OfferIdFromHref(cardHref) ? 'href' : renderKey ? 'renderKey' : 'ww-link')
          : null,
        cardClass: String(card.className || '').trim() || null
      });
    }

    const adCards = Array.from(root.querySelectorAll('div.search-offer-wrapper.cardui-adOffer'));
    for (const card of adCards) {
      const adAnchor = card.querySelector('a[href]');
      const cardHref = normalizeUrl(adAnchor?.getAttribute('href') || adAnchor?.href || '');
      const renderKey = String(
        card.getAttribute('data-renderkey')
        || adAnchor?.getAttribute('data-renderkey')
        || ''
      ).trim();

      const hrefOfferId = get1688OfferIdFromHref(cardHref);
      const renderKeyOfferId = get1688OfferIdFromRenderKey(renderKey);
      const wwOfferId = extractOfferIdFromWwLink(card);
      const offerId = hrefOfferId || renderKeyOfferId || wwOfferId;

      rows.push({
        type: 'ad',
        offerId: offerId || null,
        detailUrl: offerId ? build1688DetailUrl(offerId) : null,
        cardHref: cardHref || null,
        renderKey: renderKey || null,
        index: String(
          card.getAttribute('data-index')
          || adAnchor?.getAttribute('data-index')
          || ''
        ).trim() || null,
        offerIdSource: hrefOfferId
          ? 'href'
          : renderKeyOfferId
            ? 'renderKey'
            : wwOfferId
              ? 'ww-link'
              : null,
        cardClass: String(card.className || '').trim() || null
      });
    }

    return dedupeCardLinks(rows);
  }

  function is1688SearchResultsPage() {
    if (detectSiteKey() !== '1688') return false;
    if (document.querySelector('a.search-offer-wrapper.search-offer-item.major-offer[href]')) return true;
    if (document.querySelector('div.search-offer-wrapper.cardui-adOffer')) return true;
    return false;
  }

  function sendRuntimeMessage(message) {
    return new Promise((resolve) => {
      chrome.runtime.sendMessage(message, (response) => {
        const err = chrome.runtime.lastError;
        if (err) {
          resolve({ ok: false, error: err.message || String(err) });
          return;
        }
        resolve(response || { ok: false, error: 'Empty response' });
      });
    });
  }

  function parseApiBody(bodyText) {
    if (!bodyText) return null;
    try {
      return JSON.parse(bodyText);
    } catch {
      return null;
    }
  }

  function getApiMessage(result, fallbackMessage) {
    const body = parseApiBody(result?.bodyText);
    return body?.message || body?.error || result?.error || fallbackMessage;
  }

  function loginToBackend(url, username, password) {
    return sendRuntimeMessage({
      type: 'TMINOS_AUTH_LOGIN',
      url,
      username,
      password
    });
  }

  function fetchShopsFromBackend(url, token) {
    return sendRuntimeMessage({
      type: 'TMINOS_FETCH_SHOPS',
      url,
      token
    });
  }

  function postHtmlToApi(url, html, extractedJson, token, targetShopIds) {
    return sendRuntimeMessage({
      type: 'TMINOS_IMPORT_HTML',
      url,
      html,
      extractedJson,
      token,
      targetShopIds
    });
  }

  function postCardLinksToApi(url, items, token) {
    return sendRuntimeMessage({
      type: 'TMINOS_IMPORT_CARD_LINKS',
      url,
      items,
      token
    });
  }

  async function collectAndUploadHtml(setToast, setBusy, auth, targetShopIds) {
    try {
      if (!auth?.accessToken) {
        throw new Error('请先登录后台账号');
      }
      if (!Array.isArray(targetShopIds) || targetShopIds.length === 0) {
        throw new Error('请至少勾选 1 个采集店铺');
      }

      setBusy(true);
      setToast('等待页面加载完成…', 'loading');

      await waitForPageComplete();

      setToast('正在滚动页面触发懒加载…', 'loading');
      await scrollToTriggerLazyLoad();

      setToast('正在生成完整HTML（含Shadow DOM）…', 'loading');

      const html = cloneFullHtml();
      const extractedJson = extractStructuredData();

      setToast('正在上传到接口…', 'loading');
      const baseUrl = getBaseUrl();
      const endpointUrl = getEndpointUrl(baseUrl);

      const result = await postHtmlToApi(endpointUrl, html, extractedJson, auth.accessToken, targetShopIds);
      const body = parseApiBody(result?.bodyText);

      if (!result?.ok || body?.success === false) {
        if (result?.status === 401) {
          await clearAuthForEnvironment();
          throw new Error('登录已失效，请重新登录');
        }
        throw new Error(body?.message || getApiMessage(result, `${result?.status || ''} ${result?.statusText || ''}`.trim() || 'Upload failed'));
      }

      const brief = body?.message || getApiMessage(result, (result.bodyText || '').trim().slice(0, 200));
      setToast(`采集成功：${result.status} ${result.statusText}${brief ? ` | ${brief}` : ''}`, 'success');
    } catch (err) {
      setToast(`采集失败：${err?.message || String(err)}`, 'error');
    } finally {
      setBusy(false);
    }
  }

  function initUI() {
    const siteProfile = getSiteProfile();
    const jsonLd = Array.from(document.querySelectorAll('script[type="application/ld+json"]'))
      .map((s) => parseJsonSafely(s.textContent || ''))
      .filter(Boolean)
      .slice(0, 10);
    const jsonLdProduct = getJsonLdProductData(jsonLd);
    const productPage = isLikelyProductPage(siteProfile, jsonLdProduct);
    const searchResultsPage = is1688SearchResultsPage();
    const root = el('div', { id: PANEL_ID });

    const card = el('div', { class: 'c1688-card' });
    const header = el('div', { class: 'c1688-header' });
    const titleWrap = el('div', { class: 'c1688-title-wrap' });
    const title = el('div', { class: 'c1688-title', text: siteProfile.label || '商品采集助手' });
    const subtitle = el('div', {
      class: 'c1688-subtitle',
      text: searchResultsPage
        ? '采集当前列表卡片链接并上传到 TMINOS'
        : '采集当前页面并上传到 TMINOS 接口'
    });

    const btnMin = el('button', {
      class: 'c1688-icon-btn',
      title: '最小化/展开',
      text: '—',
      onclick: () => {
        state.minimized = !state.minimized;
        root.classList.toggle('c1688-minimized', state.minimized);
      }
    });

    const btnClose = el('button', {
      class: 'c1688-icon-btn',
      title: '关闭',
      text: '×',
      onclick: () => root.remove()
    });

    titleWrap.appendChild(title);
    titleWrap.appendChild(subtitle);

    const actions = el('div', { class: 'c1688-actions' }, [btnMin, btnClose]);
    header.appendChild(titleWrap);
    header.appendChild(actions);

    const body = el('div', { class: 'c1688-body' });
    const environmentLabel = el('label', { class: 'c1688-field-label', text: '上传环境' });
    const environmentSelect = el('select', { class: 'c1688-select', 'aria-label': '上传环境' });
    for (const env of getAvailableEnvironments()) {
      environmentSelect.appendChild(el('option', {
        value: env.key,
        text: `${env.label} (${getHostLabel(env.baseUrl)})`
      }));
    }
    const environmentSection = el('div', { class: 'c1688-section' }, [environmentLabel, environmentSelect]);

    const authSection = el('div', { class: 'c1688-section c1688-section-card' });
    authSection.appendChild(el('div', { class: 'c1688-field-label', text: '后台登录' }));
    const authSummary = el('div', { class: 'c1688-status-text', text: '未登录' });
    const authForm = el('div', { class: 'c1688-form-grid' });
    const usernameInput = el('input', {
      class: 'c1688-input',
      type: 'text',
      placeholder: '后台账号',
      value: 'admin'
    });
    const passwordInput = el('input', {
      class: 'c1688-input',
      type: 'password',
      placeholder: '后台密码'
    });
    authForm.appendChild(usernameInput);
    authForm.appendChild(passwordInput);

    const authActionsRow = el('div', { class: 'c1688-inline-actions' });
    const btnLogin = el('button', { class: 'c1688-btn', text: '登录后台' });
    const btnLogout = el('button', { class: 'c1688-btn c1688-secondary', text: '退出登录' });
    authActionsRow.appendChild(btnLogin);
    authActionsRow.appendChild(btnLogout);
    authSection.appendChild(authSummary);
    authSection.appendChild(authForm);
    authSection.appendChild(authActionsRow);

    const shopSection = el('div', { class: 'c1688-section c1688-section-card' });
    shopSection.appendChild(el('div', { class: 'c1688-field-label', text: '采集店铺' }));
    const shopStatus = el('div', { class: 'c1688-status-text', text: '请先登录后加载店铺。' });
    const shopToolbar = el('div', { class: 'c1688-inline-actions' });
    const btnSelectAllShops = el('button', { class: 'c1688-btn c1688-secondary', text: '全选' });
    const btnClearShops = el('button', { class: 'c1688-btn c1688-secondary', text: '清空' });
    const btnReloadShops = el('button', { class: 'c1688-btn c1688-secondary', text: '刷新店铺' });
    shopToolbar.appendChild(btnSelectAllShops);
    shopToolbar.appendChild(btnClearShops);
    shopToolbar.appendChild(btnReloadShops);
    const shopList = el('div', { class: 'c1688-checkbox-list' });
    shopSection.appendChild(shopStatus);
    shopSection.appendChild(shopToolbar);
    shopSection.appendChild(shopList);

    const meta = el('div', { class: 'c1688-meta' });
    const metaTitle = el('div', { class: 'c1688-meta-title', text: '上传目标' });
    const metaGrid = el('div', { class: 'c1688-meta-grid' });
    const metaEnv = el('div', { class: 'c1688-meta-item' }, [
      el('div', { class: 'c1688-meta-k', text: '环境' }),
      el('div', { class: 'c1688-meta-v', text: '-' })
    ]);
    const metaHost = el('div', { class: 'c1688-meta-item' }, [
      el('div', { class: 'c1688-meta-k', text: '域名' }),
      el('div', { class: 'c1688-meta-v', text: '-' })
    ]);
    const metaApi = el('div', { class: 'c1688-meta-item' }, [
      el('div', { class: 'c1688-meta-k', text: '接口' }),
      el('div', { class: 'c1688-meta-v', text: '-' })
    ]);
    const metaLogin = el('div', { class: 'c1688-meta-item' }, [
      el('div', { class: 'c1688-meta-k', text: '登录' }),
      el('div', { class: 'c1688-meta-v', text: '-' })
    ]);
    const metaShop = el('div', { class: 'c1688-meta-item' }, [
      el('div', { class: 'c1688-meta-k', text: '店铺' }),
      el('div', { class: 'c1688-meta-v', text: '-' })
    ]);
    metaGrid.appendChild(metaEnv);
    metaGrid.appendChild(metaHost);
    metaGrid.appendChild(metaApi);
    metaGrid.appendChild(metaLogin);
    metaGrid.appendChild(metaShop);
    meta.appendChild(metaTitle);
    meta.appendChild(metaGrid);

    const toast = el('div', { class: 'c1688-toast is-neutral', text: '就绪，等待开始采集。' });

    const hint = el('div', {
      class: 'c1688-hint',
      text: searchResultsPage
        ? '提示：先滚动到需要的位置，再点击采集卡片链接。'
        : '提示：页面若有懒加载内容，请先滚动到需要的位置，再点击采集。'
    });

    const renderAuthSummary = () => {
      const auth = getAuthForEnvironment();
      const loggedIn = !!auth?.accessToken;
      authSummary.textContent = loggedIn
        ? `已登录：${auth.displayName || auth.username || '后台账号'}`
        : '未登录，采集前需要先登录后台。';
      authForm.style.display = loggedIn ? 'none' : 'grid';
      btnLogin.style.display = loggedIn ? 'none' : 'block';
      btnLogout.style.display = loggedIn ? 'block' : 'none';
      btnLogin.disabled = state.submittingLogin;
      btnLogin.textContent = state.submittingLogin ? '登录中…' : '登录后台';
    };

    const renderShopList = () => {
      const auth = getAuthForEnvironment();
      const selectedSet = new Set(getSelectedShopIdsForEnvironment());
      shopList.innerHTML = '';

      if (!auth?.accessToken) {
        shopStatus.textContent = '请先登录后加载店铺。';
        btnSelectAllShops.disabled = true;
        btnClearShops.disabled = true;
        btnReloadShops.disabled = true;
        shopList.appendChild(el('div', { class: 'c1688-empty', text: '登录后可选择要采集到哪些店铺。' }));
        return;
      }

      btnSelectAllShops.disabled = state.loadingShops || state.availableShops.length === 0;
      btnClearShops.disabled = state.loadingShops || state.availableShops.length === 0;
      btnReloadShops.disabled = state.loadingShops;

      if (state.loadingShops) {
        shopStatus.textContent = '正在加载后台店铺…';
        shopList.appendChild(el('div', { class: 'c1688-empty', text: '店铺加载中…' }));
        return;
      }

      if (!state.availableShops.length) {
        shopStatus.textContent = '当前环境下没有可用店铺。';
        shopList.appendChild(el('div', { class: 'c1688-empty', text: '没有返回启用中的后台店铺。' }));
        return;
      }

      shopStatus.textContent = `已选择 ${selectedSet.size} / ${state.availableShops.length} 个店铺`;
      state.availableShops.forEach((shop) => {
        const checkbox = el('input', { type: 'checkbox' });
        checkbox.checked = selectedSet.has(shop.shopId);
        checkbox.addEventListener('change', async () => {
          const nextSelected = new Set(getSelectedShopIdsForEnvironment());
          if (checkbox.checked) nextSelected.add(shop.shopId);
          else nextSelected.delete(shop.shopId);
          await setSelectedShopIdsForEnvironment(state.activeEnvironmentKey, Array.from(nextSelected));
          renderShopList();
          updateMetaDisplay();
        });

        const textWrap = el('div', { class: 'c1688-checkbox-text' }, [
          el('div', { class: 'c1688-checkbox-title', text: shop.shopName }),
          el('div', { class: 'c1688-checkbox-sub', text: shop.shopId })
        ]);
        const row = el('label', { class: 'c1688-checkbox-row' }, [checkbox, textWrap]);
        shopList.appendChild(row);
      });
    };

    const updateMetaDisplay = (message) => {
      const environment = getEnvironmentConfig();
      const baseUrl = environment.baseUrl;
      const auth = getAuthForEnvironment();
      const selectedShopCount = getSelectedShopIdsForEnvironment().length;
      metaEnv.querySelector('.c1688-meta-v').textContent = environment.label;
      metaHost.querySelector('.c1688-meta-v').textContent = getHostLabel(baseUrl);
      metaApi.querySelector('.c1688-meta-v').textContent = `HTML ${DEFAULTS.endpointPath || '-'} | 卡片 ${DEFAULTS.cardLinkEndpointPath || '-'}`;
      metaLogin.querySelector('.c1688-meta-v').textContent = auth?.accessToken
        ? (auth.displayName || auth.username || '已登录')
        : '未登录';
      metaShop.querySelector('.c1688-meta-v').textContent = searchResultsPage
        ? '无需店铺'
        : selectedShopCount > 0
          ? `${selectedShopCount} 个已选`
          : '未选择';
      if (message) setToastState(toast, message, 'neutral');
    };

    const loadAvailableShops = async (silent = false) => {
      const auth = getAuthForEnvironment();
      if (!auth?.accessToken) {
        state.availableShops = [];
        renderShopList();
        updateMetaDisplay();
        return;
      }

      state.loadingShops = true;
      renderShopList();
      try {
        const result = await fetchShopsFromBackend(getShopListUrl(getBaseUrl()), auth.accessToken);
        if (!result?.ok) {
          if (result?.status === 401) {
            await clearAuthForEnvironment();
            state.availableShops = [];
            renderAuthSummary();
            renderShopList();
            updateMetaDisplay();
            if (!silent) setToastState(toast, '登录已失效，请重新登录。', 'error');
            return;
          }
          throw new Error(getApiMessage(result, '加载店铺失败'));
        }

        const body = parseApiBody(result.bodyText);
        const shops = Array.isArray(body?.data) ? body.data : [];
        state.availableShops = shops
          .filter((item) => item?.shopId && item?.shopName)
          .map((item) => ({
            shopId: String(item.shopId),
            shopName: String(item.shopName)
          }));

        const validShopIds = new Set(state.availableShops.map((shop) => shop.shopId));
        let selectedShopIds = getSelectedShopIdsForEnvironment().filter((shopId) => validShopIds.has(shopId));
        if (selectedShopIds.length === 0 && state.availableShops.length > 0) {
          selectedShopIds = state.availableShops.map((shop) => shop.shopId);
        }
        await setSelectedShopIdsForEnvironment(state.activeEnvironmentKey, selectedShopIds);
        renderShopList();
        updateMetaDisplay();
        if (!silent) {
          setToastState(toast, `店铺已刷新，共 ${state.availableShops.length} 个。`, 'success');
        }
      } catch (err) {
        state.availableShops = [];
        renderShopList();
        updateMetaDisplay();
        if (!silent) {
          setToastState(toast, `加载店铺失败：${err?.message || String(err)}`, 'error');
        }
      } finally {
        state.loadingShops = false;
        renderShopList();
      }
    };

    environmentSelect.addEventListener('change', async () => {
      const nextEnvironmentKey = environmentSelect.value;
      state.activeEnvironmentKey = nextEnvironmentKey;
      state.availableShops = [];
      renderAuthSummary();
      renderShopList();
      updateMetaDisplay(`已切换到 ${getEnvironmentConfig(nextEnvironmentKey).label}。`);
      const persisted = await persistEnvironmentKey(nextEnvironmentKey);
      if (!persisted) {
        setToastState(toast, '环境已切换，但保存默认环境失败。', 'error');
      }
      await loadAvailableShops(true);
    });

    btnLogin.addEventListener('click', async () => {
      const username = String(usernameInput.value || '').trim();
      const password = String(passwordInput.value || '');
      if (!username || !password) {
        setToastState(toast, '请输入后台账号和密码。', 'error');
        return;
      }

      state.submittingLogin = true;
      renderAuthSummary();
      try {
        const result = await loginToBackend(getLoginUrl(getBaseUrl()), username, password);
        if (!result?.ok) {
          throw new Error(getApiMessage(result, '登录失败'));
        }
        const body = parseApiBody(result.bodyText);
        if (!body?.success || !body?.data?.accessToken) {
          throw new Error(body?.message || '登录失败');
        }
        await setAuthForEnvironment(state.activeEnvironmentKey, body.data);
        passwordInput.value = '';
        renderAuthSummary();
        await loadAvailableShops(true);
        updateMetaDisplay();
        setToastState(toast, `登录成功：${body.data.displayName || body.data.username || username}`, 'success');
      } catch (err) {
        setToastState(toast, `登录失败：${err?.message || String(err)}`, 'error');
      } finally {
        state.submittingLogin = false;
        renderAuthSummary();
      }
    });

    btnLogout.addEventListener('click', async () => {
      await clearAuthForEnvironment();
      state.availableShops = [];
      renderAuthSummary();
      renderShopList();
      updateMetaDisplay();
      setToastState(toast, '已退出当前环境登录。', 'neutral');
    });

    btnSelectAllShops.addEventListener('click', async () => {
      await setSelectedShopIdsForEnvironment(
        state.activeEnvironmentKey,
        state.availableShops.map((shop) => shop.shopId)
      );
      renderShopList();
      updateMetaDisplay();
    });

    btnClearShops.addEventListener('click', async () => {
      await setSelectedShopIdsForEnvironment(state.activeEnvironmentKey, []);
      renderShopList();
      updateMetaDisplay();
    });

    btnReloadShops.addEventListener('click', async () => {
      await loadAvailableShops();
    });

    const btnCollect = el('button', {
      class: 'c1688-btn',
      text: '采集并上传HTML',
      onclick: async () => {
        const setToast = (t, kind = 'neutral') => setToastState(toast, t, kind);
        const setBusy = (busy) => {
          btnCollect.disabled = busy;
          btnCollect.textContent = busy ? '处理中…' : '采集并上传HTML';
        };

        if (!productPage) {
          setToast('当前页面看起来不像商品详情页，请打开商品页后重试。', 'error');
          return;
        }

        await collectAndUploadHtml(
          setToast,
          setBusy,
          getAuthForEnvironment(),
          getSelectedShopIdsForEnvironment()
        );
        renderAuthSummary();
        renderShopList();
        updateMetaDisplay();
      }
    });

    const btnCopy = el('button', {
      class: 'c1688-btn c1688-secondary',
      text: '复制完整HTML到剪贴板',
      onclick: async () => {
        try {
          setToastState(toast, '正在滚动页面触发懒加载…', 'loading');
          await waitForPageComplete();
          await scrollToTriggerLazyLoad();
          setToastState(toast, '正在序列化（含Shadow DOM）…', 'loading');
          const html = cloneFullHtml();
          await navigator.clipboard.writeText(html);
          setToastState(toast, '已复制到剪贴板。', 'success');
        } catch (err) {
          setToastState(toast, `复制失败：${err?.message || String(err)}`, 'error');
        }
      }
    });

    const btnCollectCardLinks = el('button', {
      class: 'c1688-btn c1688-secondary',
      text: '采集卡片链接',
      onclick: async () => {
        try {
          const auth = getAuthForEnvironment();
          if (!auth?.accessToken) {
            setToastState(toast, '请先登录后台账号。', 'error');
            return;
          }

          btnCollectCardLinks.disabled = true;
          btnCollectCardLinks.textContent = '采集上传中…';

          if (!is1688SearchResultsPage()) {
            setToastState(toast, '当前页面不像 1688 搜索结果页，请先打开搜索列表页。', 'error');
            return;
          }

          await waitForPageComplete();
          await waitForDomSettle(800);
          const cardLinks = collect1688SearchCardLinks();

          if (!cardLinks.length) {
            setToastState(toast, '未采集到卡片详情链接，请确认页面已加载出商品卡片。', 'error');
            return;
          }

          console.log('[1688-collector] card detail links', cardLinks);
          console.log(JSON.stringify(cardLinks, null, 2));
          globalThis.__C1688_CARD_LINKS__ = cardLinks;

          const result = await postCardLinksToApi(getCardLinkEndpointUrl(getBaseUrl()), cardLinks, auth.accessToken);
          const body = parseApiBody(result?.bodyText);

          if (!result?.ok || body?.success === false) {
            if (result?.status === 401) {
              await clearAuthForEnvironment();
              renderAuthSummary();
              updateMetaDisplay();
              throw new Error('登录已失效，请重新登录');
            }
            throw new Error(body?.message || getApiMessage(result, '上传卡片链接失败'));
          }

          const summary = body?.data
            ? `有效 ${body.data.validCount ?? cardLinks.length} 条，新增 ${body.data.insertedCount ?? 0} 条，更新 ${body.data.updatedCount ?? 0} 条`
            : `共上传 ${cardLinks.length} 条`;
          const skippedText = body?.data?.skippedMissingOfferIdCount
            ? `，跳过无 offerId ${body.data.skippedMissingOfferIdCount} 条`
            : '';
          setToastState(toast, `已采集 ${cardLinks.length} 条卡片链接并上传后台，${summary}${skippedText}。`, 'success');
        } catch (err) {
          setToastState(toast, `采集卡片链接失败：${err?.message || String(err)}`, 'error');
        } finally {
          btnCollectCardLinks.disabled = false;
          btnCollectCardLinks.textContent = '采集卡片链接';
        }
      }
    });

    const actionSection = el('div', { class: 'c1688-section c1688-button-grid' }, searchResultsPage ? [] : [btnCollect]);
    const utilitySection = el('div', { class: 'c1688-section c1688-button-grid' }, searchResultsPage ? [btnCollectCardLinks] : [btnCopy, btnCollectCardLinks]);

    if (searchResultsPage) {
      shopSection.style.display = 'none';
    }

    body.appendChild(environmentSection);
    body.appendChild(authSection);
    body.appendChild(shopSection);
    if (!searchResultsPage) {
      body.appendChild(actionSection);
    }
    body.appendChild(utilitySection);
    body.appendChild(meta);
    body.appendChild(hint);
    body.appendChild(toast);

    // init meta
    (async () => {
      state.authByEnv = await loadStoredJson(STORAGE_AUTH_KEY, {});
      state.selectedShopIdsByEnv = await loadStoredJson(STORAGE_TARGET_SHOPS_KEY, {});
      const storedEnvironmentKey = await loadStoredEnvironmentKey();
      state.activeEnvironmentKey = getEnvironmentConfig(storedEnvironmentKey).key;
      environmentSelect.value = state.activeEnvironmentKey;
      renderAuthSummary();
      renderShopList();
      updateMetaDisplay(
        searchResultsPage
          ? undefined
          : productPage
            ? undefined
            : '当前页面不是明显的商品详情页，仍可复制 HTML，但不建议直接上传。'
      );
      if (!searchResultsPage) {
        await loadAvailableShops(true);
      }
    })();

    card.appendChild(header);
    card.appendChild(body);
    root.appendChild(card);

    // Drag support (simple)
    header.addEventListener('mousedown', (e) => {
      state.dragging = true;
      const rect = root.getBoundingClientRect();
      state.dragOffsetX = e.clientX - rect.left;
      state.dragOffsetY = e.clientY - rect.top;
      e.preventDefault();
    });

    window.addEventListener('mousemove', (e) => {
      if (!state.dragging) return;
      const left = Math.max(8, Math.min(window.innerWidth - 260, e.clientX - state.dragOffsetX));
      const top = Math.max(8, Math.min(window.innerHeight - 60, e.clientY - state.dragOffsetY));
      root.style.left = `${left}px`;
      root.style.top = `${top}px`;
      root.style.right = 'auto';
    });

    window.addEventListener('mouseup', () => {
      state.dragging = false;
    });

    document.documentElement.appendChild(root);
  }

  // 避免太早注入导致 body 未就绪
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initUI, { once: true });
  } else {
    initUI();
  }
})();
