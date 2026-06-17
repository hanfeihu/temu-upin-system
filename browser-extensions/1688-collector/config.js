// 环境配置
// baseUrl 保留为兼容字段，默认取 defaultEnvironmentKey 对应环境的地址。

globalThis.TMINOS_COLLECTOR_DEFAULTS = {
  environments: {
    hanfeihu: {
      label: '韩飞虎',
      baseUrl: 'https://youyou.tminos.com'
    },
    dev: {
      label: '开发环境',
      baseUrl: 'https://dev.tminos.com'
    },
    chenqi: {
      label: 'chenqi服务器',
      baseUrl: 'http://chenqi.tminos.com:20080'
    },
    local: {
      label: '本地环境',
      baseUrl: 'http://127.0.0.1:8080'
    }
  },
  defaultEnvironmentKey: 'dev',
  baseUrl: 'https://dev.tminos.com',
  authLoginPath: '/api/auth/login',
  shopListPath: '/api/platform/temu-shops',
  endpointPath: '/api/platform/product-drafts/import',
  cardLinkEndpointPath: '/api/platform/alibaba1688-card-links/import',
  siteProfiles: {
    generic: {
      label: '商品采集助手',
      hostPatterns: [],
      titleSelectors: [
        'h1',
        '[data-testid="product-title"]',
        '[class*="title"]'
      ],
      priceSelectors: [
        '[data-testid="price"]',
        '[class*="price"]',
        '[data-role*="price"]'
      ],
      sellerSelectors: [
        '[data-testid="seller"]',
        '[class*="seller"]',
        '[class*="shop"]',
        '[class*="brand"]'
      ],
      salesSelectors: [
        '[data-testid="sales"]',
        '[class*="sale"]',
        '[class*="deal"]',
        '[class*="review"]'
      ],
      thumbnailImageSelectors: [
        '.thumb img',
        '.thumbnail img',
        '.sku-img img',
        '.small-img img',
        '[class*="thumb"] img'
      ],
      carouselImageSelectors: [
        '.carousel img',
        '.banner img',
        '.swiper img',
        '.slick-slide img',
        '[class*="carousel"] img',
        '[class*="swiper"] img'
      ],
      detailImageContainers: ['.html-description', '#detail', '[data-testid="product-detail"]'],
      productIdPatterns: []
    },
    '1688': {
      label: '1688 采集助手',
      hostPatterns: ['1688.com'],
      titleSelectors: [
        'h1',
        '.d-title',
        '.title-text',
        '[data-spm-anchor-id*="title"]'
      ],
      priceSelectors: [
        '.price-now',
        '.price',
        '.price-text',
        '[class*="price"]',
        '[data-role*="price"]'
      ],
      sellerSelectors: [
        '.company-name',
        '.shop-name',
        '.seller-name',
        '[class*="seller"]',
        '[class*="shop"]'
      ],
      salesSelectors: [
        '.sale-count',
        '.deal-cnt',
        '[class*="sale"]',
        '[class*="deal"]'
      ],
      thumbnailImageSelectors: [
        '.thumb img',
        '.thumbnail img',
        '.sku-img img',
        '.small-img img',
        '[class*="thumb"] img'
      ],
      carouselImageSelectors: [
        '.carousel img',
        '.banner img',
        '.swiper img',
        '.slick-slide img',
        '[class*="carousel"] img',
        '[class*="swiper"] img'
      ],
      detailImageContainers: ['.html-description', '#detail'],
      productIdPatterns: [
        '(?:offer|product)/(\\d+)\\.html',
        '[?&]id=(\\d+)'
      ]
    },
    temu: {
      label: 'Temu 采集助手',
      hostPatterns: ['temu.com'],
      titleSelectors: [
        'h1',
        '[data-testid="goods-title"]',
        '[class*="goods-title"]',
        '[class*="product-title"]'
      ],
      priceSelectors: [
        '[data-testid="price"]',
        '[class*="_price"]',
        '[class*="price"]'
      ],
      sellerSelectors: [
        '[data-testid="store-name"]',
        '[class*="store-name"]',
        '[class*="seller"]',
        '[class*="shop"]',
        '[class*="brand"]'
      ],
      salesSelectors: [
        '[class*="review"]',
        '[class*="sold"]',
        '[class*="sales"]'
      ],
      thumbnailImageSelectors: [
        '[class*="thumb"] img',
        '[class*="gallery"] img',
        '[class*="sku"] img'
      ],
      carouselImageSelectors: [
        '[class*="swiper"] img',
        '[class*="carousel"] img',
        '[class*="gallery"] img'
      ],
      detailImageContainers: ['[class*="detail"]', '[class*="description"]', '[data-testid="product-detail"]'],
      productIdPatterns: [
        '[?&]goods_id=(\\d+)',
        '[?&]sku_id=(\\d+)',
        '-g-(\\d+)\\.html',
        '/(?:goods|item|product)/[^/?#]*?(\\d{6,})'
      ]
    },
    amazon: {
      label: 'Amazon 采集助手',
      hostPatterns: ['amazon.com', 'amazon.co.uk', 'amazon.de', 'amazon.fr', 'amazon.it', 'amazon.es', 'amazon.co.jp', 'amazon.ca'],
      titleSelectors: [
        '#productTitle',
        'h1',
        '[data-feature-name="title"] h1',
        '[id*="title"]'
      ],
      priceSelectors: [
        '#corePriceDisplay_desktop_feature_div .a-offscreen',
        '#corePrice_feature_div .a-offscreen',
        '.apexPriceToPay .a-offscreen',
        '.a-price .a-offscreen',
        '[class*="price"] .a-offscreen'
      ],
      sellerSelectors: [
        '#bylineInfo',
        '#sellerProfileTriggerId',
        '#merchantInfo',
        '[class*="brand"]',
        '[class*="seller"]'
      ],
      salesSelectors: [
        '#acrCustomerReviewText',
        '[data-hook="total-review-count"]',
        '[class*="review"]',
        '[class*="rating"]'
      ],
      thumbnailImageSelectors: [
        '#altImages img',
        '#imageBlockThumbs img',
        '[data-testid="image-thumbnail"] img'
      ],
      carouselImageSelectors: [
        '#main-image-container img',
        '#imgTagWrapperId img',
        '[data-a-dynamic-image]',
        '[class*="image"] img'
      ],
      detailImageContainers: ['#aplus', '#productDescription', '#feature-bullets'],
      productIdPatterns: [
        '/dp/([A-Z0-9]{10})(?:[/?]|$)',
        '/gp/product/([A-Z0-9]{10})(?:[/?]|$)',
        '[?&]asin=([A-Z0-9]{10})(?:[&#]|$)'
      ]
    }
  }
};
