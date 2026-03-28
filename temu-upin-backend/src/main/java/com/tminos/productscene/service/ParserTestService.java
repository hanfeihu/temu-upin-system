package com.tminos.productscene.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.dto.ParserTestDTO;
import com.tminos.productscene.entity.ParserTestRun;
import com.tminos.productscene.repository.ParserTestRunRepository;
import com.tminos.productscene.service.pull.parser.Alibaba1688HtmlParser;
import com.tminos.productscene.service.pull.parser.TemuHtmlParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ParserTestService {

    private final ObjectMapper objectMapper;
    private final ParserTestRunRepository parserTestRunRepository;

    public ParserTestService(ObjectMapper objectMapper, ParserTestRunRepository parserTestRunRepository) {
        this.objectMapper = objectMapper;
        this.parserTestRunRepository = parserTestRunRepository;
    }

    @Transactional
    public ParserTestDTO.ParseResponse parseAlibaba1688(String html) {
        return execute("1688", html, () -> new Alibaba1688HtmlParser(objectMapper).parse(html));
    }

    @Transactional
    public ParserTestDTO.ParseResponse parseTemu(String html) {
        return execute("TEMU", html, () -> new TemuHtmlParser(objectMapper).parse(html));
    }

    @Transactional(readOnly = true)
    public ParserTestDTO.ParseResponse getRun(String runId) {
        ParserTestRun run = parserTestRunRepository.findByRunId(runId)
                .orElseThrow(() -> new IllegalArgumentException("未找到对应 runId: " + runId));

        ParserTestDTO.ParseResponse response = new ParserTestDTO.ParseResponse();
        response.setRunId(run.getRunId());
        response.setParserType(run.getParserType());
        response.setSuccess("SUCCEEDED".equalsIgnoreCase(run.getStatus()));
        response.setErrorMessage(run.getErrorMessage());
        response.setCreatedAt(formatDateTime(run.getCreatedAt()));
        response.setHtml(run.getInputHtml());
        response.setResult(readJsonMap(run.getParseResultJson()));
        return response;
    }

    private ParserTestDTO.ParseResponse execute(String parserType, String html, ParserExecutor executor) {
        ParserTestRun run = new ParserTestRun();
        run.setRunId(buildRunId());
        run.setParserType(parserType);
        run.setStatus("STARTED");
        run.setInputHtml(html);
        parserTestRunRepository.save(run);

        ParserTestDTO.ParseResponse response = new ParserTestDTO.ParseResponse();
        response.setRunId(run.getRunId());
        response.setParserType(parserType);
        response.setCreatedAt(formatDateTime(run.getCreatedAt()));
        response.setHtml(html);

        try {
            Alibaba1688HtmlParser.ParsedProduct parsed = executor.execute();
            Map<String, Object> result = toMap(parsed, parserType);
            run.setStatus("SUCCEEDED");
            run.setParseResultJson(writeJson(result));
            run.setFinishedAt(LocalDateTime.now());
            parserTestRunRepository.save(run);

            response.setSuccess(true);
            response.setResult(result);
            response.setCreatedAt(formatDateTime(run.getCreatedAt()));
            return response;
        } catch (Exception e) {
            run.setStatus("FAILED");
            run.setErrorMessage(e.getMessage());
            run.setFinishedAt(LocalDateTime.now());
            parserTestRunRepository.save(run);

            response.setSuccess(false);
            response.setErrorMessage(e.getMessage());
            response.setCreatedAt(formatDateTime(run.getCreatedAt()));
            return response;
        }
    }

    private Map<String, Object> toMap(Alibaba1688HtmlParser.ParsedProduct parsed, String parserType) {
        Map<String, Object> out = objectMapper.convertValue(parsed, new TypeReference<LinkedHashMap<String, Object>>() {});
        out.put("parserType", parserType);
        return out;
    }

    private String buildRunId() {
        return "PTR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> readJsonMap(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @FunctionalInterface
    private interface ParserExecutor {
        Alibaba1688HtmlParser.ParsedProduct execute() throws Exception;
    }
}
