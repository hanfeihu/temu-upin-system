import { Alert, App, Empty, Flex, Spin, Tabs, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { temuOrdersApi } from '@/api/temuOrders';
import { temuShopsApi } from '@/api/temuShops';
import type {
  TemuOrderDashboardDailyCompareValueVO,
  TemuOrderDashboardDailyValueVO,
  TemuOrderDashboardResponseVO,
  TemuShopVO,
} from '@/types/api';
import './OrderDashboardPage.css';

type TrendSeries = {
  key: string;
  name: string;
  color: string;
  values: number[];
};

type OrderTrendChartProps = {
  title: string;
  subtitle: string;
  dates: string[];
  series: TrendSeries[];
};

type SummaryCardItem = {
  key: string;
  label: string;
  value?: number | null;
  kind?: 'count' | 'currency';
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

const formatCompactCurrency = (value?: number | null) => {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return '-';
  }

  const yuan = numeric / 100;
  const absolute = Math.abs(yuan);
  if (absolute >= 10000) {
    const compact = `${(absolute / 10000).toFixed(absolute >= 100000 ? 0 : 1)}万`;
    return `${yuan < 0 ? '-' : ''}¥${compact}`;
  }

  return yuan.toLocaleString('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: absolute >= 1000 ? 0 : 2,
    maximumFractionDigits: 2,
  });
};

const formatSummaryValue = (item: SummaryCardItem) => (
  item.kind === 'currency' ? formatCompactCurrency(item.value) : formatCompactNumber(item.value)
);

const resolveSummaryTone = (item: SummaryCardItem) => {
  if (item.kind !== 'currency') {
    return '';
  }
  const numeric = Number(item.value ?? 0);
  if (numeric > 0) {
    return 'is-positive';
  }
  if (numeric < 0) {
    return 'is-negative';
  }
  return 'is-neutral';
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

const formatAxisDate = (value?: string) => {
  if (!value) {
    return '';
  }
  return formatTooltipDate(value);
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

const OrderTrendChart = ({ title, subtitle, dates, series }: OrderTrendChartProps) => {
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

  const getY = (value: number) => {
    return paddingTop + chartInnerHeight - (Math.max(value, 0) / maxValue) * chartInnerHeight;
  };

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
      <div className="order-dashboard-chart-card">
        <div className="order-dashboard-chart-header">
          <div>
            <Typography.Title level={4}>{title}</Typography.Title>
            <Typography.Text type="secondary">{subtitle}</Typography.Text>
          </div>
        </div>
        <div className="order-dashboard-empty">
          <Empty description="暂无统计数据" />
        </div>
      </div>
    );
  }

  return (
    <div className="order-dashboard-chart-card">
      <div className="order-dashboard-chart-header">
        <div>
          <Typography.Title level={4}>{title}</Typography.Title>
          <Typography.Text type="secondary">{subtitle}</Typography.Text>
        </div>
        <Flex gap={14} wrap="wrap" className="order-dashboard-chart-legend">
          {series.map((item) => (
            <span key={item.key} className="order-dashboard-chart-legend-item">
              <span className="order-dashboard-chart-legend-dot" style={{ backgroundColor: item.color }} />
              <span>{item.name}</span>
            </span>
          ))}
        </Flex>
      </div>

      <div ref={containerRef} className="order-dashboard-chart-svg-wrap">
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
                  className="order-dashboard-grid-line"
                />
                <text x={paddingLeft - 10} y={y + 4} textAnchor="end" className="order-dashboard-axis-text">
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
              className="order-dashboard-axis-text"
            >
              {formatAxisDate(dates[index])}
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
              className="order-dashboard-hover-line"
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
          <div className="order-dashboard-tooltip">
            <div className="order-dashboard-tooltip-date">{formatTooltipDate(dates[hoverIndex])}</div>
            {series.map((item) => (
              <div key={`${item.key}-tooltip`} className="order-dashboard-tooltip-row">
                <span className="order-dashboard-chart-legend-dot" style={{ backgroundColor: item.color }} />
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

const OrderDashboardPage = () => {
  const { message } = App.useApp();
  const [shops, setShops] = useState<TemuShopVO[]>([]);
  const [activeShopRecordId, setActiveShopRecordId] = useState<number>();
  const [dashboard, setDashboard] = useState<TemuOrderDashboardResponseVO | null>(null);
  const [shopLoading, setShopLoading] = useState(true);
  const [dashboardLoading, setDashboardLoading] = useState(false);

  useEffect(() => {
    let mounted = true;

    async function loadShops() {
      setShopLoading(true);
      try {
        const response = await temuShopsApi.list({ enabled: true });
        const rows = Array.isArray(response.data) ? response.data.filter((item: TemuShopVO) => item.id && item.shopName) : [];
        if (!mounted) {
          return;
        }
        setShops(rows);
        setActiveShopRecordId((current) => {
          if (current && rows.some((item) => item.id === current)) {
            return current;
          }
          return rows[0]?.id;
        });
      } catch (error) {
        if (mounted) {
          message.error(error instanceof Error ? error.message : '加载店铺失败');
        }
      } finally {
        if (mounted) {
          setShopLoading(false);
        }
      }
    }

    void loadShops();
    return () => {
      mounted = false;
    };
  }, [message]);

  useEffect(() => {
    if (!activeShopRecordId) {
      setDashboard(null);
      return;
    }

    let mounted = true;

    async function loadDashboard() {
      setDashboardLoading(true);
      try {
        const response = await temuOrdersApi.dashboard({ shopRecordId: activeShopRecordId });
        if (!mounted) {
          return;
        }
        setDashboard(response.data);
      } catch (error) {
        if (mounted) {
          setDashboard(null);
          message.error(error instanceof Error ? error.message : '加载订单统计失败');
        }
      } finally {
        if (mounted) {
          setDashboardLoading(false);
        }
      }
    }

    void loadDashboard();
    return () => {
      mounted = false;
    };
  }, [activeShopRecordId, message]);

  const activeShop = shops.find((item) => item.id === activeShopRecordId) ?? null;

  const recentOrderCountRows = dashboard?.recentOrderCountSeries ?? [];
  const recentQuantityRows = dashboard?.recentQuantitySeries ?? [];
  const recentDistinctSkuRows = dashboard?.recentDistinctSkuSeries ?? [];
  const historicalCompareRows = dashboard?.historicalSignedAftersaleSeries ?? [];

  const recentDates = recentOrderCountRows.map((item: TemuOrderDashboardDailyValueVO) => item.date);
  const recentOrderSeries = recentOrderCountRows.map((item: TemuOrderDashboardDailyValueVO) => item.value);
  const recentQuantitySeries = recentQuantityRows.map((item: TemuOrderDashboardDailyValueVO) => item.value);
  const recentDistinctSkuSeries = recentDistinctSkuRows.map((item: TemuOrderDashboardDailyValueVO) => item.value);
  const historicalDates = historicalCompareRows.map((item: TemuOrderDashboardDailyCompareValueVO) => item.date);
  const historicalSignedSeries = historicalCompareRows.map((item: TemuOrderDashboardDailyCompareValueVO) => item.signedValue);
  const historicalAftersaleSeries = historicalCompareRows.map((item: TemuOrderDashboardDailyCompareValueVO) => item.aftersaleValue);
  const summaryCards: SummaryCardItem[] = dashboard
    ? [
        {
          key: 'recent-child-order-count',
          label: '最近30天子单数',
          value: dashboard.totals?.recentChildOrderCount,
        },
        {
          key: 'recent-quantity',
          label: '最近30天出货数量',
          value: dashboard.totals?.recentQuantity,
        },
        {
          key: 'historical-signed-parent-count',
          label: '15天前30天签收父单数',
          value: dashboard.totals?.historicalSignedParentCount,
        },
        {
          key: 'historical-aftersale-parent-count',
          label: '15天前30天售后父单数',
          value: dashboard.totals?.historicalAftersaleParentCount,
        },
        {
          key: 'today-profit',
          label: '今日利润（跳过缺数）',
          value: dashboard.totals?.todayProfit,
          kind: 'currency',
        },
        {
          key: 'today-sales-amount',
          label: '今日销售额',
          value: dashboard.totals?.todaySalesAmount,
          kind: 'currency',
        },
        {
          key: 'today-quantity',
          label: '今日出货数（数量维度）',
          value: dashboard.totals?.todayQuantity,
        },
        {
          key: 'today-distinct-sku-count',
          label: '今日出货SKU数',
          value: dashboard.totals?.todayDistinctSkuCount,
        },
        {
          key: 'today-parent-order-count',
          label: '今日订单数（父订单维度）',
          value: dashboard.totals?.todayParentOrderCount,
        },
      ]
    : [];

  const tabItems = shops.map((item) => ({
    key: String(item.id),
    label: item.shopName,
    children: null,
  }));

  return (
    <div className="order-dashboard-page">
      <div className="order-dashboard-hero">
        <div>
          <Typography.Title>订单统计大屏</Typography.Title>
          <Typography.Paragraph>
            聚焦最近 30 天子单趋势、15 天前 30 天父单签收/售后对比，以及最近 30 天出货数量变化。
          </Typography.Paragraph>
        </div>
        <div className="order-dashboard-hero-meta">
          <span>{dashboard?.recent30Window?.label ?? '最近30天'}</span>
          <span>{dashboard?.historicalWindow?.label ?? '15天前的30天'}</span>
        </div>
      </div>

      <div className="order-dashboard-tabs-card">
        <Tabs
          type="card"
          size="large"
          activeKey={activeShopRecordId ? String(activeShopRecordId) : undefined}
          items={tabItems}
          onChange={(key) => setActiveShopRecordId(Number(key))}
        />
        {activeShop ? (
          <Typography.Text type="secondary">
            当前店铺：{activeShop.shopName}（{activeShop.shopId}）
          </Typography.Text>
        ) : null}
      </div>

      {shopLoading ? (
        <div className="order-dashboard-loading">
          <Spin size="large" />
        </div>
      ) : !shops.length ? (
        <div className="order-dashboard-empty">
          <Empty description="暂无启用中的店铺" />
        </div>
      ) : (
        <>
          {dashboard ? (
            <div className="order-dashboard-summary-grid">
              {summaryCards.map((item) => (
                <div key={item.key} className={`order-dashboard-summary-card ${resolveSummaryTone(item)}`.trim()}>
                  <span className="summary-label">{item.label}</span>
                  <strong>{formatSummaryValue(item)}</strong>
                </div>
              ))}
            </div>
          ) : null}

          {dashboardLoading ? (
            <div className="order-dashboard-loading">
              <Spin size="large" />
            </div>
          ) : dashboard ? (
            <div className="order-dashboard-grid">
              <div className="order-dashboard-grid-full">
                <OrderTrendChart
                  title="最近30天订单数量曲线"
                  subtitle="按子订单维度统计，不包含已取消订单，按订单创建时间聚合"
                  dates={recentDates}
                  series={[
                    {
                      key: 'recent-order-count',
                      name: '子单数量',
                      color: '#2563eb',
                      values: recentOrderSeries,
                    },
                  ]}
                />
              </div>

              <OrderTrendChart
                title="15天前30天签收 / 售后对比"
                subtitle="按父订单维度统计，按订单创建时间聚合，方便对比成熟订单的售后表现"
                dates={historicalDates}
                series={[
                  {
                    key: 'historical-signed',
                    name: '已签收父单数',
                    color: '#059669',
                    values: historicalSignedSeries,
                  },
                  {
                    key: 'historical-aftersale',
                    name: '售后父单数',
                    color: '#f97316',
                    values: historicalAftersaleSeries,
                  },
                ]}
              />

              <OrderTrendChart
                title="最近30天出货数量曲线"
                subtitle="按子订单数量汇总，同时展示每天出货 SKU 种类数；若单个订单某 SKU 数量为 2，则按 2 计入出货数量"
                dates={recentDates}
                series={[
                  {
                    key: 'recent-quantity',
                    name: '出货数量',
                    color: '#7c3aed',
                    values: recentQuantitySeries,
                  },
                  {
                    key: 'recent-distinct-sku-count',
                    name: '出货SKU数',
                    color: '#14b8a6',
                    values: recentDistinctSkuSeries,
                  },
                ]}
              />
            </div>
          ) : (
            <Alert type="warning" showIcon message="当前店铺暂无可展示的订单统计数据" />
          )}
        </>
      )}
    </div>
  );
};

export default OrderDashboardPage;
