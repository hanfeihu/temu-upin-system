package com.tminos.productscene.service;

import com.tminos.productscene.entity.TemuOrder;
import com.tminos.productscene.entity.TemuOrderLogistics;
import com.tminos.productscene.entity.TemuShop;
import com.tminos.productscene.repository.TemuOrderLogisticsRepository;
import com.tminos.productscene.repository.TemuOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class DianxiaomiPackageService {

    private static final Logger log = LoggerFactory.getLogger(DianxiaomiPackageService.class);

    private final DianxiaomiPackageClient dianxiaomiPackageClient;
    private final TemuOrderRepository orderRepository;
    private final TemuOrderLogisticsRepository logisticsRepository;

    public DianxiaomiPackageService(DianxiaomiPackageClient dianxiaomiPackageClient,
                                    TemuOrderRepository orderRepository,
                                    TemuOrderLogisticsRepository logisticsRepository) {
        this.dianxiaomiPackageClient = dianxiaomiPackageClient;
        this.orderRepository = orderRepository;
        this.logisticsRepository = logisticsRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, String> resolvePackageNumbers(TemuShop shop, Collection<String> parentOrderSns) {
        LinkedHashSet<String> normalizedParents = normalizeParentOrders(parentOrderSns);
        if (shop == null || shop.getId() == null || normalizedParents.isEmpty()) {
            return Collections.emptyMap();
        }

        List<TemuOrder> orders = orderRepository.findByShopRecordIdAndParentOrderSnIn(shop.getId(), normalizedParents);
        Map<String, List<TemuOrder>> ordersByParent = new LinkedHashMap<>();
        for (TemuOrder order : orders) {
            if (order == null || !StringUtils.hasText(order.getParentOrderSn())) {
                continue;
            }
            ordersByParent.computeIfAbsent(order.getParentOrderSn().trim(), key -> new ArrayList<>()).add(order);
        }

        List<TemuOrderLogistics> logisticsRows = logisticsRepository.findByShopRecordIdAndParentOrderSnInAndProviderCode(
                shop.getId(),
                normalizedParents,
                LogisticsProviderConfigService.HAOYUAN_PROVIDER_CODE
        );
        Map<String, TemuOrderLogistics> logisticsByParent = new LinkedHashMap<>();
        for (TemuOrderLogistics logistics : logisticsRows) {
            if (logistics == null || !StringUtils.hasText(logistics.getParentOrderSn())) {
                continue;
            }
            logisticsByParent.put(logistics.getParentOrderSn().trim(), logistics);
        }

        LinkedHashMap<String, String> resolved = new LinkedHashMap<>();
        List<TemuOrder> changedOrders = new ArrayList<>();
        List<TemuOrderLogistics> changedLogistics = new ArrayList<>();

        for (String parentOrderSn : normalizedParents) {
            String existingPackageNumber = firstNonBlank(
                    extractPackageNumberFromOrders(ordersByParent.get(parentOrderSn)),
                    extractPackageNumberFromLogistics(logisticsByParent.get(parentOrderSn), parentOrderSn)
            );
            if (!StringUtils.hasText(existingPackageNumber)) {
                continue;
            }
            resolved.put(parentOrderSn, existingPackageNumber);
            applyPackageNumberSnapshot(shop, parentOrderSn, existingPackageNumber, ordersByParent, logisticsByParent, changedOrders, changedLogistics);
        }

        if (!StringUtils.hasText(shop.getDianxiaomiCookie())) {
            flushChanges(changedOrders, changedLogistics);
            return resolved;
        }

        for (String parentOrderSn : normalizedParents) {
            if (resolved.containsKey(parentOrderSn)) {
                continue;
            }
            try {
                DianxiaomiPackageClient.SearchResponse searchResponse =
                        dianxiaomiPackageClient.searchByParentOrderSn(shop.getDianxiaomiCookie(), parentOrderSn);
                String packageNumber = pickPackageNumber(searchResponse.items(), parentOrderSn);
                if (!StringUtils.hasText(packageNumber)) {
                    continue;
                }
                resolved.put(parentOrderSn, packageNumber);
                applyPackageNumberSnapshot(shop, parentOrderSn, packageNumber, ordersByParent, logisticsByParent, changedOrders, changedLogistics);
            } catch (Exception e) {
                log.warn("店小秘包裹查询失败 shopId={}, parentOrderSn={}, error={}",
                        shop.getShopId(), parentOrderSn, e.getMessage());
            }
        }

        flushChanges(changedOrders, changedLogistics);
        return resolved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void savePackageNumber(TemuShop shop, String parentOrderSn, String packageNumber) {
        String normalizedParentOrderSn = trim(parentOrderSn);
        String normalizedPackageNumber = trim(packageNumber);
        if (shop == null || shop.getId() == null || !StringUtils.hasText(normalizedParentOrderSn) || !StringUtils.hasText(normalizedPackageNumber)) {
            return;
        }

        List<TemuOrder> orders = orderRepository.findByShopRecordIdAndParentOrderSnIn(shop.getId(), List.of(normalizedParentOrderSn));
        Map<String, List<TemuOrder>> ordersByParent = Map.of(normalizedParentOrderSn, new ArrayList<>(orders));

        TemuOrderLogistics logistics = logisticsRepository
                .findByShopRecordIdAndParentOrderSnAndProviderCode(
                        shop.getId(),
                        normalizedParentOrderSn,
                        LogisticsProviderConfigService.HAOYUAN_PROVIDER_CODE
                )
                .orElse(null);
        Map<String, TemuOrderLogistics> logisticsByParent = new LinkedHashMap<>();
        if (logistics != null) {
            logisticsByParent.put(normalizedParentOrderSn, logistics);
        }

        List<TemuOrder> changedOrders = new ArrayList<>();
        List<TemuOrderLogistics> changedLogistics = new ArrayList<>();
        applyPackageNumberSnapshot(shop, normalizedParentOrderSn, normalizedPackageNumber, ordersByParent, logisticsByParent, changedOrders, changedLogistics);
        flushChanges(changedOrders, changedLogistics);
    }

    private void flushChanges(List<TemuOrder> changedOrders, List<TemuOrderLogistics> changedLogistics) {
        if (changedOrders != null && !changedOrders.isEmpty()) {
            orderRepository.saveAll(changedOrders);
        }
        if (changedLogistics != null && !changedLogistics.isEmpty()) {
            logisticsRepository.saveAll(changedLogistics);
        }
    }

    private void applyPackageNumberSnapshot(TemuShop shop,
                                            String parentOrderSn,
                                            String packageNumber,
                                            Map<String, List<TemuOrder>> ordersByParent,
                                            Map<String, TemuOrderLogistics> logisticsByParent,
                                            List<TemuOrder> changedOrders,
                                            List<TemuOrderLogistics> changedLogistics) {
        if (!StringUtils.hasText(packageNumber)) {
            return;
        }

        List<TemuOrder> parentOrders = ordersByParent.getOrDefault(parentOrderSn, Collections.emptyList());
        for (TemuOrder order : parentOrders) {
            if (order == null || Objects.equals(trim(order.getDianxiaomiPackageNumber()), packageNumber)) {
                continue;
            }
            order.setDianxiaomiPackageNumber(packageNumber);
            changedOrders.add(order);
        }

        TemuOrderLogistics logistics = logisticsByParent.get(parentOrderSn);
        if (logistics == null) {
            logistics = TemuOrderLogistics.builder()
                    .shopRecordId(shop.getId())
                    .shopId(trim(shop.getShopId()))
                    .shopName(trim(shop.getShopName()))
                    .parentOrderSn(parentOrderSn)
                    .providerCode(LogisticsProviderConfigService.HAOYUAN_PROVIDER_CODE)
                    .providerName("浩远国际")
                    .build();
            logisticsByParent.put(parentOrderSn, logistics);
            logistics.setShippingMethodNo(packageNumber);
            changedLogistics.add(logistics);
            return;
        }
        if (!Objects.equals(trim(logistics.getShippingMethodNo()), packageNumber)) {
            logistics.setShippingMethodNo(packageNumber);
            changedLogistics.add(logistics);
        }
    }

    private String extractPackageNumberFromOrders(List<TemuOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return null;
        }
        for (TemuOrder order : orders) {
            String value = trim(order == null ? null : order.getDianxiaomiPackageNumber());
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String extractPackageNumberFromLogistics(TemuOrderLogistics logistics, String parentOrderSn) {
        if (logistics == null) {
            return null;
        }
        String shippingMethodNo = trim(logistics.getShippingMethodNo());
        if (!StringUtils.hasText(shippingMethodNo)) {
            return null;
        }
        if (Objects.equals(shippingMethodNo, trim(parentOrderSn))) {
            return null;
        }
        return shippingMethodNo;
    }

    private String pickPackageNumber(List<DianxiaomiPackageClient.SearchItem> items, String parentOrderSn) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> packageNumbers = new LinkedHashSet<>();
        for (DianxiaomiPackageClient.SearchItem item : items) {
            String packageNumber = trim(item == null ? null : item.packageNumber());
            if (StringUtils.hasText(packageNumber)) {
                packageNumbers.add(packageNumber);
            }
        }
        if (packageNumbers.size() > 1) {
            log.warn("店小秘查询返回多个包裹号，按首个处理 parentOrderSn={}, packageNumbers={}", parentOrderSn, packageNumbers);
        }
        return packageNumbers.stream().findFirst().orElse(null);
    }

    private LinkedHashSet<String> normalizeParentOrders(Collection<String> parentOrderSns) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (parentOrderSns == null || parentOrderSns.isEmpty()) {
            return normalized;
        }
        for (String parentOrderSn : parentOrderSns) {
            String value = trim(parentOrderSn);
            if (StringUtils.hasText(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trim(value);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
