import { Alert, App, Empty, Flex, Spin, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { productDashboardApi } from '@/api/productDashboard';
import type { ProductDashboardDailyValueVO, ProductDashboardResponseVO } from '@/types/api';
import './ProductDashboardPage.css';

type TrendSeries = {
  key: string;
  name: string;
  color: string;
  values: number[];
};

type ProductTrendChartProps = {
  title: string;
  subtitle: string;
  dates: string[];
  series: TrendSeries[];
};

type SummaryCardItem = {
  key: string;
  label: string;
  value?: number | null;
};

const formatCompactNumber = (value?: number | null) => {
  const numeric = Number(value ?? 0);
  if (!Number.isFinite(numeric)) {
    return '0';
  }
  if (numeric >= 10000) {
    return `${(numeric / 10000).toFixed(numeric >= 100000 ? 0 : 1)}万`;
  }
  return `${Math.round(numeric)}`;
};

const formatTooltipDate = (value?: string) => {
  if (!value) {
    return '-';
  }
  const [year, month, day] = value.split('-');
  if (!year || !month || !day) {
    return value;
  }
  return `${month}-${day}`;
};

const buildAxisLabelIndexes = (count: number) => {
  if (count <= 0) {
    return [];
  }
  if (count <= 6) {
    return Array.from({ length: count }, (_, index) => index);
  }
  const indexes = new Set<number>();
  for (let index = 0; index < 6; index += 1) {
    indexes.add(Math.round((index * (count - 1)) / 5));
  }
  return Array.from(indexes).sort((left, right) => left - right);
};

const resolveNiceMax = (maxValue: number) => {
  if (maxValue <= 0) {
    return 4;
  }
  const roughStep = maxValue / 4;
  const magnitude = 10 ** Math.floor(Math.log10(roughStep));
  const normalized = roughStep / magnitude;
  let step = 1;
  if (normalized > 5) {
    step = 10;
  } else if (normalized > 2) {
    step = 5;
  } else if (normalized > 1) {
    step = 2;
  }
  return step * magnitude * 4;
};

const ProductTrendChart = ({ title, subtitle, dates, series }: ProductTrendChartProps) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [containerWidth, setContainerWidth] = useState(920);
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);
  const chartHeight = 280;
  const paddingTop = 14;
  const paddingRight = 18;
  const paddingBottom = 34;
  const paddingLeft = 42;

  useEffect(() => {
    const element = containerRef.current;
    if (!element) {
      return undefined;
    }

    const observer = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (!entry) {
        return;
      }
      setContainerWidth(Math.max(Math.floor(entry.contentRect.width), 320));
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const width = Math.max(containerWidth, 320);
  const chartWidth = Math.max(width - paddingLeft - paddingRight, 1);
  const chartInnerHeight = Math.max(chartHeight - paddingTop - paddingBottom, 1);
  const maxValue = resolveNiceMax(
    Math.max(
      0,
      ...series.flatMap((item) => item.values.map((value) => Number(value || 0))),
    ),
  );
  const labelIndexes = buildAxisLabelIndexes(dates.length);
  const hoverIndex = hoveredIndex !== null ? Math.max(0, Math.min(hoveredIndex, dates.length - 1)) : null;

  const getX = (index: number) => {
    if (dates.length <= 1) {
      return paddingLeft + chartWidth / 2;
    }
    return paddingLeft + (index / (dates.length - 1)) * chartWidth;
  };

  const getY = (value: number) => (
    paddingTop + chartInnerHeight - (Math.max(value, 0) / maxValue) * chartInnerHeight
  );

  const handlePointerMove = (event: React.MouseEvent<SVGRectElement, MouseEvent>) => {
    if (dates.length <= 0) {
      return;
    }
    const rect = event.currentTarget.getBoundingClientRect();
    const relativeX = event.clientX - rect.left - paddingLeft;
    const ratio = dates.length <= 1 ? 0 : relativeX / chartWidth;
    const nextIndex = dates.length <= 1 ? 0 : Math.round(ratio * (dates.length - 1));
    setHoveredIndex(Math.max(0, Math.min(nextIndex, dates.length - 1)));
  };

  if (!dates.length || !series.length) {
    return (
      <div className="product-dashboard-chart-card">
        <div className="product-dashboard-chart-header">
          <div>
            <Typography.Title level={4}>{title}</Typography.Title>
            <Typography.Text type="secondary">{subtitle}</Typography.Text>
          </div>
        </div>
        <div className="product-dashboard-empty">
          <Empty description="暂无统计数据" />
        </div>
      </div>
    );
  }

  return (
    <div className="product-dashboard-chart-card">
      <div className="product-dashboard-chart-header">
        <div>
          <Typography.Title level={4}>{title}</Typography.Title>
          <Typography.Text type="secondary">{subtitle}</Typography.Text>
        </div>
        <Flex gap={14} wrap="wrap" className="product-dashboard-chart-legend">
          {series.map((item) => (
            <span key={item.key} className="product-dashboard-chart-legend-item">
              <span className="product-dashboard-chart-legend-dot" style={{ backgroundColor: item.color }} />
              <span>{item.name}</span>
            </span>
          ))}
        </Flex>
      </div>

      <div ref={containerRef} className="product-dashboard-chart-svg-wrap">
        <svg width="100%" height={chartHeight} viewBox={`0 0 ${width} ${chartHeight}`} preserveAspectRatio="none">
          {Array.from({ length: 5 }, (_, index) => {
            const value = (maxValue / 4) * (4 - index);
            const y = paddingTop + (chartInnerHeight / 4) * index;
            return (
              <g key={`grid-${index}`}>
                <line
                  x1={paddingLeft}
                  y1={y}
                  x2={width - paddingRight}
                  y2={y}
                  className="product-dashboard-grid-line"
                />
                <text x={paddingLeft - 10} y={y + 4} textAnchor="end" className="product-dashboard-axis-text">
                  {formatCompactNumber(value)}
                </text>
              </g>
            );
          })}

          {labelIndexes.map((index) => (
            <text
              key={`label-${index}`}
              x={getX(index)}
              y={chartHeight - 10}
              textAnchor="middle"
              className="product-dashboard-axis-text"
            >
              {formatTooltipDate(dates[index])}
            </text>
          ))}

          {series.map((item) => {
            const points = item.values.map((value, index) => `${getX(index)},${getY(Number(value || 0))}`).join(' ');
            return (
              <g key={item.key}>
                <polyline
                  points={points}
                  fill="none"
                  stroke={item.color}
                  strokeWidth="3"
                  strokeLinejoin="round"
                  strokeLinecap="round"
                />
                {item.values.map((value, index) => (
                  <circle
                    key={`${item.key}-${dates[index]}`}
                    cx={getX(index)}
                    cy={getY(Number(value || 0))}
                    r={hoverIndex === index ? 4 : 2.5}
                    fill={item.color}
                    stroke="#ffffff"
                    strokeWidth="1.5"
                  />
                ))}
              </g>
            );
          })}

          {hoverIndex !== null ? (
            <line
              x1={getX(hoverIndex)}
              y1={paddingTop}
              x2={getX(hoverIndex)}
              y2={paddingTop + chartInnerHeight}
              className="product-dashboard-hover-line"
            />
          ) : null}

          <rect
            x={paddingLeft}
            y={paddingTop}
            width={chartWidth}
            height={chartInnerHeight}
            fill="transparent"
            onMouseMove={handlePointerMove}
            onMouseLeave={() => setHoveredIndex(null)}
          />
        </svg>

        {hoverIndex !== null ? (
          <div className="product-dashboard-tooltip">
            <div className="product-dashboard-tooltip-date">{formatTooltipDate(dates[hoverIndex])}</div>
            {series.map((item) => (
              <div key={`${item.key}-tooltip`} className="product-dashboard-tooltip-row">
                <span className="product-dashboard-chart-legend-dot" style={{ backgroundColor: item.color }} />
                <span>{item.name}</span>
                <strong>{formatCompactNumber(item.values[hoverIndex])}</strong>
              </div>
            ))}
          </div>
        ) : null}
      </div>
    </div>
  );
};

const ProductDashboardPage = () => {
  const { message } = App.useApp();
  const [dashboard, setDashboard] = useState<ProductDashboardResponseVO | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;

    async function loadDashboard() {
      setLoading(true);
      try {
        const response = await productDashboardApi.dashboard({ days: 30 });
        if (!mounted) {
          return;
        }
        setDashboard(response.data);
      } catch (error) {
        if (mounted) {
          setDashboard(null);
          message.error(error instanceof Error ? error.message : '加载商品数据看板失败');
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    }

    void loadDashboard();
    return () => {
      mounted = false;
    };
  }, [message]);

  const rows = dashboard?.recentPublishSuccessSeries ?? [];
  const dates = rows.map((item: ProductDashboardDailyValueVO) => item.date);
  const publishSeries = rows.map((item: ProductDashboardDailyValueVO) => item.value);
  const publishedProductReviewApprovedSeries = (dashboard?.recentPublishedProductPriceReviewApprovedSeries ?? []).map(
    (item: ProductDashboardDailyValueVO) => item.value,
  );
  const publishedProductReviewRejectedSeries = (dashboard?.recentPublishedProductPriceReviewRejectedSeries ?? []).map(
    (item: ProductDashboardDailyValueVO) => item.value,
  );
  const publishedProductReviewUnreviewedSeries = (dashboard?.recentPublishedProductPriceReviewUnreviewedSeries ?? []).map(
    (item: ProductDashboardDailyValueVO) => item.value,
  );
  const publishedProductAddedSiteSeries = (dashboard?.recentPublishedProductAddedSiteSeries ?? []).map(
    (item: ProductDashboardDailyValueVO) => item.value,
  );
  const summaryCards: SummaryCardItem[] = dashboard
    ? [
        {
          key: 'total-collected-count',
          label: '采集商品总数',
          value: dashboard.totals?.totalCollectedCount,
        },
        {
          key: 'total-published-count',
          label: '累计发布成功',
          value: dashboard.totals?.totalPublishedCount,
        },
        {
          key: 'total-added-site-count',
          label: '累计已加站',
          value: dashboard.totals?.totalAddedSiteCount,
        },
        {
          key: 'recent-published-count',
          label: '最近30日发布成功',
          value: dashboard.totals?.recentPublishedCount,
        },
        {
          key: 'recent-added-site-count',
          label: '最近30日已加站',
          value: dashboard.totals?.recentAddedSiteCount,
        },
        {
          key: 'today-published-count',
          label: '今日发布成功',
          value: dashboard.totals?.todayPublishedCount,
        },
        {
          key: 'today-added-site-count',
          label: '今日已加站',
          value: dashboard.totals?.todayAddedSiteCount,
        },
      ]
    : [];

  return (
    <div className="product-dashboard-page">
      <div className="product-dashboard-hero">
        <div>
          <Typography.Title>商品数据看板</Typography.Title>
          <Typography.Paragraph>
            按商品发布时间展示发布成功，以及发布商品后续核价、加站趋势。
          </Typography.Paragraph>
        </div>
        <div className="product-dashboard-hero-meta">
          <span>{dashboard?.recent30Window?.label ?? '最近30天'}</span>
          <span>数据源：采集商品库</span>
        </div>
      </div>

      {loading ? (
        <div className="product-dashboard-loading">
          <Spin size="large" />
        </div>
      ) : dashboard ? (
        <>
          <div className="product-dashboard-summary-grid">
            {summaryCards.map((item) => (
              <div key={item.key} className="product-dashboard-summary-card">
                <span className="summary-label">{item.label}</span>
                <strong>{formatCompactNumber(item.value)}</strong>
              </div>
            ))}
          </div>

          <ProductTrendChart
            title="最近30日发布成功产品数据"
            subtitle="按商品发布时间归属：有任一 SKU 通过算核价通过，全部 SKU 拒绝算核价拒绝，已加站才算在售中"
            dates={dates}
            series={[
              {
                key: 'publish-success',
                name: '发布成功产品数',
                color: '#0f766e',
                values: publishSeries,
              },
              {
                key: 'published-product-review-approved',
                name: '核价通过产品数',
                color: '#2563eb',
                values: publishedProductReviewApprovedSeries,
              },
              {
                key: 'published-product-review-rejected',
                name: '核价拒绝产品数',
                color: '#dc2626',
                values: publishedProductReviewRejectedSeries,
              },
              {
                key: 'published-product-review-unreviewed',
                name: '未核价产品数',
                color: '#f59e0b',
                values: publishedProductReviewUnreviewedSeries,
              },
              {
                key: 'published-product-added-site',
                name: '已加站产品数',
                color: '#16a34a',
                values: publishedProductAddedSiteSeries,
              },
            ]}
          />
        </>
      ) : (
        <Alert type="warning" showIcon message="暂无可展示的商品统计数据" />
      )}
    </div>
  );
};

export default ProductDashboardPage;
