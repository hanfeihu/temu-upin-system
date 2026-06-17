export async function waitForSettledPage(page, {
  settleDelayMs = 1200,
  networkidleTimeoutMs = 8000,
} = {}) {
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
  if (settleDelayMs > 0) {
    await page.waitForTimeout(settleDelayMs);
  }
  if (networkidleTimeoutMs > 0) {
    await page.waitForLoadState('networkidle', { timeout: networkidleTimeoutMs }).catch(() => {});
  }
}

export async function waitForExtensionInsights(page, timeoutMs = 10000, pollMs = 800) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const ready = await page.evaluate(() => {
      const text = document.body?.innerText || '';
      return text.includes('1688采购助手') && (text.includes('上架时间') || text.includes('月成交'));
    }).catch(() => false);

    if (ready) {
      return true;
    }

    await page.waitForTimeout(Math.max(pollMs, 100)).catch(() => {});
  }

  return false;
}

export async function dismissPurchaseAssistantWelcome(page) {
  const hasWelcome = await page.evaluate(() => {
    const text = document.body?.innerText || '';
    return text.includes('欢迎使用1688采购助手');
  }).catch(() => false);

  if (!hasWelcome) {
    return false;
  }

  const closeCandidates = [
    'button:has-text("我知道了")',
    'button:has-text("知道了")',
    'button:has-text("立即体验")',
    'button:has-text("开始使用")',
    'button:has-text("跳过")',
    '[role="button"]:has-text("我知道了")',
    '[role="button"]:has-text("知道了")',
    '[role="button"]:has-text("立即体验")',
    '[role="button"]:has-text("开始使用")',
    '[role="button"]:has-text("跳过")',
    '[aria-label="关闭"]',
    '.ant-modal-close',
    '.next-dialog-close',
    '[class*="close"]',
  ];

  for (const selector of closeCandidates) {
    const locator = page.locator(selector).first();
    const count = await locator.count().catch(() => 0);
    if (!count) {
      continue;
    }
    const visible = await locator.isVisible().catch(() => false);
    if (!visible) {
      continue;
    }
    const clicked = await locator.click({ timeout: 1500 }).then(() => true).catch(() => false);
    if (clicked) {
      await page.waitForTimeout(600).catch(() => {});
      return true;
    }
  }

  return false;
}

export async function scrollPageForLazyContent(page, {
  scrollDelayMs = 160,
  finalDelayMs = 1200,
} = {}) {
  await page.evaluate(async ({ scrollDelayMs: delayMs, finalDelayMs: settleMs }) => {
    const totalHeight = document.documentElement.scrollHeight || document.body.scrollHeight || 0;
    const viewportHeight = window.innerHeight || 800;
    if (totalHeight <= viewportHeight * 1.5) {
      return;
    }
    const step = Math.max(Math.floor(viewportHeight * 0.7), 320);
    let current = 0;
    while (current < totalHeight) {
      current += step;
      window.scrollTo(0, current);
      await new Promise((resolve) => setTimeout(resolve, delayMs));
    }
    await new Promise((resolve) => setTimeout(resolve, settleMs));
    window.scrollTo(0, 0);
  }, {
    scrollDelayMs: Math.max(scrollDelayMs, 0),
    finalDelayMs: Math.max(finalDelayMs, 0),
  });
}

export async function serializePageWithShadowDom(page) {
  return page.evaluate(() => {
    const VOID_TAGS = new Set([
      'area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input',
      'link', 'meta', 'param', 'source', 'track', 'wbr',
    ]);

    function escAttr(value) {
      return String(value || '')
        .replace(/&/g, '&amp;')
        .replace(/"/g, '&quot;');
    }

    function serializeChildren(parent) {
      let html = '';
      for (const child of parent.childNodes) {
        html += serializeNode(child);
      }
      return html;
    }

    function serializeNode(node) {
      if (node.nodeType === Node.TEXT_NODE) {
        return node.textContent || '';
      }
      if (node.nodeType === Node.COMMENT_NODE) {
        return `<!--${node.data}-->`;
      }
      if (node.nodeType !== Node.ELEMENT_NODE) {
        return '';
      }

      const tag = node.tagName.toLowerCase();
      let html = `<${tag}`;
      for (const attr of node.attributes) {
        html += ` ${attr.name}="${escAttr(attr.value)}"`;
      }
      html += '>';

      if (VOID_TAGS.has(tag)) {
        return html;
      }

      if (node.shadowRoot) {
        html += '<template shadowrootmode="open">';
        html += serializeChildren(node.shadowRoot);
        html += '</template>';
      }

      html += serializeChildren(node);
      html += `</${tag}>`;
      return html;
    }

    const doctype = document.doctype
      ? `<!DOCTYPE ${document.doctype.name}${document.doctype.publicId ? ` PUBLIC "${document.doctype.publicId}"` : ''}${document.doctype.systemId ? ` "${document.doctype.systemId}"` : ''}>\n`
      : '<!DOCTYPE html>\n';

    return doctype + serializeNode(document.documentElement);
  });
}

export async function detectAuthExpired(page) {
  const url = page.url();
  if (/login\.1688\.com|signin|login\.alibaba\.com/i.test(url)) {
    return true;
  }

  return page.evaluate(() => {
    const bodyText = document.body?.innerText || '';
    return /请登录|登录后查看|账号登录|扫码登录/.test(bodyText);
  }).catch(() => false);
}

export async function extractAccountMeta(page) {
  return page.evaluate(() => {
    const pickText = (selectors) => {
      for (const selector of selectors) {
        const node = document.querySelector(selector);
        const text = node?.textContent?.trim();
        if (text) return text;
      }
      return '';
    };

    const accountNick = pickText([
      '.user-info-name',
      '.account-name',
      '.member-nick',
      '[class*="nickname"]',
      '[class*="account"] [class*="name"]',
    ]);

    const memberId = pickText([
      '[data-member-id]',
      '[class*="member-id"]',
      '[class*="account-id"]',
    ]);

    return {
      accountNick: accountNick || null,
      memberId: memberId || null,
      homeUrl: location.href,
    };
  }).catch(() => ({
    accountNick: null,
    memberId: null,
    homeUrl: page.url(),
  }));
}

export async function extractStructuredData(page) {
  return page.evaluate(() => {
    function escapeRegExp(value) {
      return String(value || '').replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }

    function normalizeUrl(raw) {
      if (!raw) return '';
      try {
        return new URL(raw, location.href).href;
      } catch {
        return String(raw || '').trim();
      }
    }

    function pickMeta(name) {
      return (
        document.querySelector(`meta[name="${name}"]`)?.getAttribute('content')?.trim()
        || document.querySelector(`meta[property="${name}"]`)?.getAttribute('content')?.trim()
        || ''
      );
    }

    function collectImageUrlsFromRoot(root, output) {
      if (!root || typeof root.querySelectorAll !== 'function') return;
      const attrs = ['src', 'data-src', 'data-lazy-src', 'data-ks-lazyload', 'data-original'];
      const images = root.querySelectorAll('img');
      for (const img of images) {
        for (const attr of attrs) {
          const value = img.getAttribute?.(attr) || '';
          const url = normalizeUrl(value);
          if (/^https?:\/\//i.test(url)) {
            output.add(url);
          }
        }
      }
      const elements = root.querySelectorAll('*');
      for (const el of elements) {
        if (el.shadowRoot) {
          collectImageUrlsFromRoot(el.shadowRoot, output);
        }
      }
    }

    function extractDetailImages() {
      const output = new Set();
      const containers = document.querySelectorAll('.html-description, #detail, [class*="detail"]');
      for (const container of containers) {
        collectImageUrlsFromRoot(container, output);
        if (container.shadowRoot) {
          collectImageUrlsFromRoot(container.shadowRoot, output);
        }
        const templates = container.querySelectorAll('template[shadowrootmode]');
        for (const tpl of templates) {
          if (tpl.content) {
            collectImageUrlsFromRoot(tpl.content, output);
          }
        }
      }
      return Array.from(output).slice(0, 300);
    }

    function collectCommonImages(selectors) {
      const output = new Set();
      for (const selector of selectors) {
        const nodes = document.querySelectorAll(selector);
        for (const node of nodes) {
          const url = normalizeUrl(
            node.getAttribute?.('src')
            || node.getAttribute?.('data-src')
            || node.getAttribute?.('href')
            || ''
          );
          if (/^https?:\/\//i.test(url)) {
            output.add(url);
          }
        }
      }
      return Array.from(output);
    }

    function getBodyTextLines() {
      return (document.body?.innerText || '')
        .replace(/\u00a0/g, ' ')
        .split('\n')
        .map((line) => line.trim())
        .filter(Boolean);
    }

    function pickBodyLabelValue(labels) {
      const lines = getBodyTextLines();
      const normalizedLabels = Array.isArray(labels) ? labels.filter(Boolean) : [labels].filter(Boolean);
      for (let i = 0; i < lines.length; i += 1) {
        const current = lines[i];
        for (const label of normalizedLabels) {
          if (current === label) {
            return lines[i + 1] || '';
          }

          const inlinePattern = new RegExp(`^${escapeRegExp(label)}[:：]?\\s*(.+)$`);
          const inlineMatch = current.match(inlinePattern);
          if (inlineMatch?.[1]) {
            return inlineMatch[1].trim();
          }
        }
      }
      return '';
    }

    function buildPurchaseAssistantMetrics() {
      const bodyText = document.body?.innerText || '';
      const extensionDetected = bodyText.includes('1688采购助手');
      return {
        extensionDetected,
        listingDateText: pickBodyLabelValue(['上架时间', '发布时间']) || '',
        categoryText: pickBodyLabelValue(['类目']) || '',
        monthlySoldText: pickBodyLabelValue(['月成交']) || '',
        monthlyConsignmentText: pickBodyLabelValue(['月代销']) || '',
        yearlySoldQuantityText: pickBodyLabelValue(['年成交件数']) || '',
        yearlyOrderCountText: pickBodyLabelValue(['年成交笔数']) || '',
        reviewCountText: pickBodyLabelValue(['评论数']) || '',
        positiveRateText: pickBodyLabelValue(['好评率']) || '',
        collectionRateText: pickBodyLabelValue(['揽收率']) || '',
      };
    }

    const purchaseAssistant = buildPurchaseAssistantMetrics();

    return {
      site: '1688',
      pageUrl: location.href,
      canonicalUrl: normalizeUrl(document.querySelector('link[rel="canonical"]')?.getAttribute('href') || pickMeta('og:url')),
      title: document.querySelector('#productTitle h1')?.textContent?.trim() || document.title || '',
      seller: document.querySelector('#shopNavigation h1')?.textContent?.trim() || '',
      price: pickMeta('product:price:amount') || '',
      carouselImages: collectCommonImages([
        '#dt-tab li img',
        '.detail-gallery img',
        '[class*="gallery"] img',
      ]).slice(0, 120),
      detailImages: extractDetailImages(),
      allImages: collectCommonImages(['img']).slice(0, 600),
      pageTitle: document.title || '',
      listingDateText: purchaseAssistant.listingDateText || '',
      listingDateSource: purchaseAssistant.listingDateText ? '1688_purchase_assistant_extension' : '',
      purchaseAssistant,
    };
  });
}
