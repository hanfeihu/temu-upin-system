package com.tminos.productscene.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TemuTaskTableSchemaFixService {

    private static final Logger log = LoggerFactory.getLogger(TemuTaskTableSchemaFixService.class);

    @PersistenceContext
    private EntityManager em;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onReady() {
        try {
            fixIfNeeded();
        } catch (Exception e) {
            log.warn("TemuTaskTableSchemaFixService failed: {}", e.getMessage());
        }
    }

    public void fixIfNeeded() {
        Map<String, List<String>> tableCols = new LinkedHashMap<>();
        tableCols.put("temu_main_sale_spec_inference_tasks", List.of(
                "inference_id",
                "product_name",
                "product_main_image",
                "prompt_text",
                "prompt_parts_json",
                "parent_spec_list_raw",
                "response_raw",
                "response_content",
                "parsed_json",
                "result_json",
                "main_product_sku_spec_reqs",
                "product_spec_property_reqs",
                "product_sku_reqs",
                "result_summary",
                "error_msg"
        ));
        tableCols.put("temu_attr_ai_fill_tasks", List.of(
                "task_id",
                "product_name",
                "product_main_image",
                "template_raw",
                "prompt_text",
                "response_raw",
                "parsed_json",
                "result_json",
                "rule_actions",
                "result_summary",
                "error_msg"
        ));

        for (Map.Entry<String, List<String>> e : tableCols.entrySet()) {
            String table = e.getKey();
            for (String col : e.getValue()) {
                fixTextColumnIfBytea(table, col);
            }
        }

        ensureUniqueIndexIfPossible(
                "temu_main_sale_spec_inference_tasks",
                "ux_temu_ms_infer_spu_id",
                "spu_id"
        );
    }

    private void fixTextColumnIfBytea(String table, String col) {
        String dt;
        try {
            Object r = em.createNativeQuery(
                            "select data_type from information_schema.columns " +
                                    "where table_schema = 'public' and table_name = :t and column_name = :c")
                    .setParameter("t", table)
                    .setParameter("c", col)
                    .getSingleResult();
            dt = r == null ? null : String.valueOf(r);
        } catch (Exception ignored) {
            return;
        }
        if (dt == null || !"bytea".equalsIgnoreCase(dt.trim())) {
            return;
        }

        String alter = "alter table public." + table + " alter column " + col + " type text using convert_from(" + col + ", 'UTF8')";
        try {
            em.createNativeQuery(alter).executeUpdate();
            log.info("Schema fix applied: {}.{} bytea -> text", table, col);
        } catch (Exception ex) {
            String alter2 = "alter table public." + table + " alter column " + col + " type text using encode(" + col + ", 'escape')";
            try {
                em.createNativeQuery(alter2).executeUpdate();
                log.warn("Schema fix applied with escape encoding: {}.{} bytea -> text", table, col);
            } catch (Exception ex2) {
                log.warn("Schema fix failed for {}.{}: {}", table, col, ex2.getMessage());
            }
        }
    }

    private void ensureUniqueIndexIfPossible(String table, String indexName, String column) {
        try {
            Object exists = em.createNativeQuery(
                            "select 1 from pg_indexes where schemaname = 'public' and tablename = :t and indexname = :i")
                    .setParameter("t", table)
                    .setParameter("i", indexName)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
            if (exists != null) {
                return;
            }

            Object duplicate = em.createNativeQuery(
                            "select " + column + " from public." + table + " group by " + column + " having count(*) > 1 limit 1")
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
            if (duplicate != null) {
                log.warn("Skip creating unique index {} on {}.{} because duplicate value exists: {}", indexName, table, column, duplicate);
                return;
            }

            em.createNativeQuery("create unique index if not exists " + indexName + " on public." + table + " (" + column + ")")
                    .executeUpdate();
            log.info("Schema fix applied: create unique index {} on {}({})", indexName, table, column);
        } catch (Exception e) {
            log.warn("Ensure unique index failed for {}.{}: {}", table, column, e.getMessage());
        }
    }
}
