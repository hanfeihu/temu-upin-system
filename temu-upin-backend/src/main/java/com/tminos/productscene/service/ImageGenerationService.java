package com.tminos.productscene.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.config.AIImageConfig;
import com.tminos.productscene.dto.ChannelDTO.ChannelTestRequest;
import com.tminos.productscene.dto.ChannelDTO.ChannelTestResponse;
import com.tminos.productscene.dto.ProductDTO.*;
import com.tminos.productscene.entity.*;
import com.tminos.productscene.repository.*;
import com.tminos.productscene.dto.StabilityFusionDTO;
import com.tminos.productscene.util.TextAiUrlHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.client.RestTemplate;
import com.tminos.productscene.service.OssService;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.*;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageGenerationService {
    
    private final ProductRepository productRepository;
    private final ProductSkuRepository productSkuRepository;
    private final GeneratedImageRepository generatedImageRepository;
    private final AIChannelRepository channelRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AIImageConfig aiImageConfig;
    private final OssService ossService;
    private final TextAiChannelResolver textAiChannelResolver;

    private static final String DEFAULT_TEST_IMAGE_URL = "https://raw.githubusercontent.com/github/explore/main/topics/png/png.png";

    /**
     * Experimental: fuse multiple carousel images into one using Stability (image-to-image).
     *
     * Approach:
     * - Download up to 4 images
     * - Compose into a simple 2x2 grid (white background)
     * - Send the composite as init image to Stability SD3.5 (mode=image-to-image)
     *
     * This provides a deterministic way to "combine" multiple products without requiring multi-image conditioning.
     */
    public StabilityFusionDTO.FuseResponse fuseImagesWithStability(StabilityFusionDTO.FuseRequest req) {
        if (req == null) throw new IllegalArgumentException("request is required");
        List<String> urls = req.getImageUrls();
        if (urls == null) urls = Collections.emptyList();
        urls = urls.stream().filter(StringUtils::hasText).map(String::trim).distinct().limit(4).toList();
        if (urls.size() < 2) {
            throw new IllegalArgumentException("At least 2 imageUrls are required");
        }

        AIChannel channel = pickFirstEnabledStabilityChannel();
        if (channel == null) {
            throw new IllegalStateException("No enabled stability channel found");
        }

        log.info("Stability fusion request: images={}, model={}, strength={}, size={}x{}",
                urls.size(), req.getModel(), req.getStrength(), req.getWidth(), req.getHeight());

        int outW = req.getWidth() != null ? req.getWidth() : 800;
        int outH = req.getHeight() != null ? req.getHeight() : 800;
        outW = Math.max(256, Math.min(2048, outW));
        outH = Math.max(256, Math.min(2048, outH));

        double strength = req.getStrength() != null ? req.getStrength() : 0.1;
        strength = Math.max(0.0, Math.min(1.0, strength));

        // Stability SD3.5 API models (see openapi.json). Keep default stable.
        String model = StringUtils.hasText(req.getModel()) ? req.getModel().trim() : "sd3.5-large";
        String outputFormat = StringUtils.hasText(req.getOutputFormat()) ? req.getOutputFormat().trim().toLowerCase(Locale.ROOT) : "png";
        if (!Set.of("png", "jpeg", "webp").contains(outputFormat)) {
            outputFormat = "png";
        }

        String prompt = StringUtils.hasText(req.getPrompt())
                ? req.getPrompt().trim()
                : "Two products composed in one image, clean e-commerce layout, studio lighting, photorealistic";
        String negative = StringUtils.hasText(req.getNegativePrompt())
                ? req.getNegativePrompt().trim()
                : "blurry, low quality, distorted, watermark, text";

        byte[] compositePng;
        try {
            compositePng = buildSimpleGridCompositePng(urls, outW, outH);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compose input images: " + e.getMessage(), e);
        }

        log.info("Stability fusion composite ready: {} bytes", compositePng == null ? 0 : compositePng.length);

        byte[] outBytes = callStabilityImageToImage(channel, compositePng, prompt, negative, strength, model, req.getSeed(), req.getCfgScale(), outputFormat);

        log.info("Stability fusion response image: {} bytes", outBytes == null ? 0 : outBytes.length);

        String contentType = "image/" + ("jpeg".equals(outputFormat) ? "jpeg" : outputFormat);
        String outUrl;
        if (ossService != null && ossService.isEnabled()) {
            outUrl = ossService.uploadBytes("generated/stability/fusion", outBytes, contentType);
        } else {
            outUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(outBytes);
        }
        return new StabilityFusionDTO.FuseResponse(outUrl, "stability", model);
    }

    private AIChannel pickFirstEnabledStabilityChannel() {
        List<AIChannel> list = channelRepository.findByPlatformOrderBySortOrderAsc("stability");
        if (list == null || list.isEmpty()) return null;
        for (AIChannel c : list) {
            if (c != null && Boolean.TRUE.equals(c.getEnabled())) {
                return c;
            }
        }
        return null;
    }

    private byte[] buildSimpleGridCompositePng(List<String> urls, int outW, int outH) throws Exception {
        int n = urls.size();
        int cols = (n <= 2) ? 2 : 2;
        int rows = (n <= 2) ? 1 : 2;

        BufferedImage canvas = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g.setColor(Color.WHITE);
            g.fillRect(0, 0, outW, outH);

            int pad = Math.max(8, Math.min(outW, outH) / 40);
            int cellW = (outW - pad * (cols + 1)) / cols;
            int cellH = (outH - pad * (rows + 1)) / rows;

            for (int i = 0; i < urls.size(); i++) {
                int r = (rows == 1) ? 0 : (i / cols);
                int c = (i % cols);
                if (r >= rows) break;

                BufferedImage img = downloadAndDecodeImage(urls.get(i));
                if (img == null) continue;

                // Fit image into cell with contain
                double sx = (double) cellW / img.getWidth();
                double sy = (double) cellH / img.getHeight();
                double s = Math.min(sx, sy);
                int dw = Math.max(1, (int) Math.round(img.getWidth() * s));
                int dh = Math.max(1, (int) Math.round(img.getHeight() * s));
                int x = pad + c * (cellW + pad) + (cellW - dw) / 2;
                int y = pad + r * (cellH + pad) + (cellH - dh) / 2;
                g.drawImage(img, x, y, dw, dh, null);
            }
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(canvas, "png", baos);
        return baos.toByteArray();
    }

    private BufferedImage downloadAndDecodeImage(String url) {
        if (!StringUtils.hasText(url)) return null;
        byte[] bytes;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(toSafeUri(url))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return null;
            }
            bytes = resp.body();
        } catch (Exception e) {
            return null;
        }

        try {
            return ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    private URI toSafeUri(String raw) {
        if (!StringUtils.hasText(raw)) throw new IllegalArgumentException("url is required");
        String s = raw.trim();
        try {
            return URI.create(s);
        } catch (Exception ignored) {
        }
        try {
            URL u = new URL(s);
            return new URI(
                    u.getProtocol(),
                    u.getUserInfo(),
                    u.getHost(),
                    u.getPort(),
                    u.getPath(),
                    u.getQuery(),
                    u.getRef()
            );
        } catch (Exception e) {
            return URI.create(s.replace(" ", "%20"));
        }
    }

    private byte[] callStabilityImageToImage(AIChannel channel,
                                            byte[] initPng,
                                            String prompt,
                                            String negativePrompt,
                                            double strength,
                                            String model,
                                            Long seed,
                                            Double cfgScale,
                                            String outputFormat) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + requireNonBlank(channel.getApiKey(), "Missing STABILITY_API_KEY in ai_channels"));
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Accept", "image/*");

            org.springframework.util.MultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("mode", "image-to-image");
            body.add("prompt", prompt);
            if (StringUtils.hasText(negativePrompt)) {
                body.add("negative_prompt", negativePrompt);
            }
            body.add("strength", String.valueOf(strength));
            body.add("model", model);
            body.add("output_format", outputFormat);

            if (seed != null && seed > 0) {
                body.add("seed", String.valueOf(seed));
            }
            if (cfgScale != null) {
                body.add("cfg_scale", String.valueOf(cfgScale));
            }

            ByteArrayResource res = new ByteArrayResource(initPng) {
                @Override
                public String getFilename() {
                    return "init.png";
                }
            };
            body.add("image", res);

            HttpEntity<org.springframework.util.MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    requireNonBlank(channel.getBaseUrl(), "Missing baseUrl for stability channel") + "/v2beta/stable-image/generate/sd3",
                    HttpMethod.POST,
                    request,
                    byte[].class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }
            throw new RuntimeException("Stability API request failed: " + response.getStatusCode());
        } catch (Exception e) {
            log.error("Stability fusion call failed: {}", e.getMessage(), e);
            throw new RuntimeException("Stability API call failed: " + e.getMessage(), e);
        }
    }
    
    @Transactional
    public List<GeneratedImageResponse> generateImages(Long productId, BatchGenerationRequest request) {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        
        product.setStatus(Product.ProductStatus.PROCESSING);
        productRepository.save(product);
        
        List<ProductSku> skus = productSkuRepository.findByProductId(productId);
        if (skus.isEmpty()) {
            throw new IllegalArgumentException("No SKU found for product");
        }
        
        List<GeneratedImage> allGeneratedImages = new ArrayList<>();
        boolean anyFailed = false;
        
        try {
            for (ProductSku sku : skus) {
                List<GeneratedImage> skuImages = new ArrayList<>();
                
                skuImages.addAll(generateImageType(product, sku, request.getThumbnail(), GeneratedImage.ImageType.THUMBNAIL));
                skuImages.addAll(generateImageType(product, sku, request.getCarousel(), GeneratedImage.ImageType.CAROUSEL));
                skuImages.addAll(generateImageType(product, sku, request.getDetail(), GeneratedImage.ImageType.DETAIL));
                
                allGeneratedImages.addAll(skuImages);
            }
            
            generatedImageRepository.saveAll(allGeneratedImages);

            for (GeneratedImage img : allGeneratedImages) {
                if (img != null && Boolean.FALSE.equals(img.getSuccess())) {
                    anyFailed = true;
                    break;
                }
            }
            
            product.setStatus(anyFailed ? Product.ProductStatus.FAILED : Product.ProductStatus.COMPLETED);
            productRepository.save(product);
            
        } catch (Exception e) {
            log.error("Image generation failed", e);
            product.setStatus(Product.ProductStatus.FAILED);
            productRepository.save(product);
            throw new RuntimeException("Image generation failed: " + e.getMessage(), e);
        }
        
        return allGeneratedImages.stream()
                .map(this::toGeneratedImageResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ChannelTestResponse testChannel(Long channelId, ChannelTestRequest request) {
        Objects.requireNonNull(channelId, "channelId must not be null");

        long start = System.currentTimeMillis();

        AIChannel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));

        if (!Boolean.TRUE.equals(channel.getEnabled())) {
            return ChannelTestResponse.builder()
                    .channelId(channel.getId())
                    .channelName(channel.getName())
                    .platform(channel.getPlatform())
                    .model(channel.getModel())
                    .success(false)
                    .message("Channel is disabled")
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
        }

        String platform = channel.getPlatform();
        String model = channel.getModel();

        String prompt = request != null ? request.getPrompt() : null;
        String negativePrompt = request != null ? request.getNegativePrompt() : null;
        String sourceImageUrl = request != null ? request.getSourceImageUrl() : null;
        Integer width = request != null ? request.getWidth() : null;
        Integer height = request != null ? request.getHeight() : null;

        if (!StringUtils.hasText(negativePrompt)) {
            negativePrompt = "blurry, low quality, distorted";
        }
        if (width == null) {
            width = 1024;
        }
        if (height == null) {
            height = 1024;
        }

        if (!StringUtils.hasText(prompt)) {
            prompt = "E-commerce product photo, clean background, studio lighting, sharp focus";
        }
        if (!StringUtils.hasText(sourceImageUrl)) {
            sourceImageUrl = DEFAULT_TEST_IMAGE_URL;
        }

        try {
            // For volcengine, return the upstream code/message directly to help debugging.
            if ("volcengine".equals(platform)) {
                String ak = requireNonBlank(channel.getApiKey(), "Missing VOLCENGINE_API_KEY in ai_channels");
                String sk = requireNonBlank(channel.getApiSecret(), "Missing VOLCENGINE_API_SECRET in ai_channels");

                if (!StringUtils.hasText(sourceImageUrl)) {
                    throw new IllegalArgumentException("sourceImageUrl is required for volcengine test");
                }

                String reqKey = mapModelToReqKey(model);
                String bodyStr = String.format(
                        "{\"req_key\":\"%s\",\"image_urls\":[\"%s\"],\"return_url\":true}",
                        reqKey,
                        sourceImageUrl
                );

                String respStr = callVolcengineWithSignature("CVProcess", bodyStr, ak, sk, channel.getBaseUrl());
                Map<String, Object> respMap = readJsonToMap(respStr);
                Object codeObj = respMap.get("code");
                Integer code = null;
                if (codeObj instanceof Number n) {
                    code = n.intValue();
                } else if (codeObj != null) {
                    try {
                        code = Integer.parseInt(String.valueOf(codeObj));
                    } catch (Exception ignored) {
                        code = null;
                    }
                }

                String upstreamMessage = respMap.get("message") != null ? String.valueOf(respMap.get("message")) : null;
                String requestId = respMap.get("request_id") != null ? String.valueOf(respMap.get("request_id")) : null;

                if (code == null || code != 10000) {
                    String msg = "volcengine failed";
                    if (code != null) {
                        msg += ", code=" + code;
                    }
                    if (StringUtils.hasText(upstreamMessage)) {
                        msg += ", message=" + upstreamMessage;
                    }
                    if (StringUtils.hasText(requestId)) {
                        msg += ", request_id=" + requestId;
                    }

                    return ChannelTestResponse.builder()
                            .channelId(channel.getId())
                            .channelName(channel.getName())
                            .platform(platform)
                            .model(model)
                            .success(false)
                            .message(msg)
                            .durationMs(System.currentTimeMillis() - start)
                            .build();
                }

                Map<String, Object> data = asMap(respMap.get("data"));
                if (data != null) {
                    List<String> imageUrls = asStringList(data.get("image_urls"));
                    if (imageUrls != null && !imageUrls.isEmpty()) {
                        return ChannelTestResponse.builder()
                                .channelId(channel.getId())
                                .channelName(channel.getName())
                                .platform(platform)
                                .model(model)
                                .success(true)
                                .imageUrl(imageUrls.get(0))
                                .message("OK")
                                .durationMs(System.currentTimeMillis() - start)
                                .build();
                    }
                    List<String> base64Images = asStringList(data.get("binary_data_base64"));
                    if (base64Images != null && !base64Images.isEmpty()) {
                        String saved = saveBase64Image(base64Images.get(0));
                        return ChannelTestResponse.builder()
                                .channelId(channel.getId())
                                .channelName(channel.getName())
                                .platform(platform)
                                .model(model)
                                .success(true)
                                .imageUrl(saved)
                                .message("OK")
                                .durationMs(System.currentTimeMillis() - start)
                                .build();
                    }
                }

                return ChannelTestResponse.builder()
                        .channelId(channel.getId())
                        .channelName(channel.getName())
                        .platform(platform)
                        .model(model)
                        .success(false)
                        .message("volcengine response had no image (request_id=" + requestId + ")")
                        .durationMs(System.currentTimeMillis() - start)
                        .build();
            }

            String imageUrl = callAIImageAPI(channel, prompt, negativePrompt, width, height, sourceImageUrl, model);
            boolean ok = StringUtils.hasText(imageUrl) && !imageUrl.contains("via.placeholder.com");
            String msg = ok ? "OK" : "Provider returned placeholder (treat as failure)";

            return ChannelTestResponse.builder()
                    .channelId(channel.getId())
                    .channelName(channel.getName())
                    .platform(platform)
                    .model(model)
                    .success(ok)
                    .imageUrl(imageUrl)
                    .message(msg)
                    .durationMs(System.currentTimeMillis() - start)
                    .build();

        } catch (Exception e) {
            return ChannelTestResponse.builder()
                    .channelId(channel.getId())
                    .channelName(channel.getName())
                    .platform(platform)
                    .model(model)
                    .success(false)
                    .message(e.getMessage())
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
        }
    }
    
    private List<GeneratedImage> generateImageType(Product product, ProductSku sku, 
            ImageGenerationRequest config, GeneratedImage.ImageType imageType) {

        if (config.getChannelId() == null) {
            throw new IllegalArgumentException("channelId is required");
        }

        AIChannel channel = channelRepository.findById(config.getChannelId())
                .orElseThrow(() -> new IllegalArgumentException("AI channel not found: " + config.getChannelId()));
        if (!Boolean.TRUE.equals(channel.getEnabled())) {
            throw new IllegalArgumentException("AI channel not enabled: " + channel.getId());
        }

        String provider = channel.getPlatform();
        String model = channel.getModel();
        
        String prompt = buildPrompt(sku, config.getPrompt(), imageType);
        String negativePrompt = config.getNegativePrompt() != null ? 
                config.getNegativePrompt() : "blurry, low quality, distorted";
        
        List<GeneratedImage> images = new ArrayList<>();
        
        for (int i = 0; i < config.getCount(); i++) {
            String imageUrl = null;
            boolean success = false;
            String errorMessage = null;

            try {
                imageUrl = callAIImageAPI(channel, prompt, negativePrompt,
                        config.getWidth(), config.getHeight(), sku.getOriginalImageUrl(), model);
                // With strict provider error handling, callAIImageAPI should throw on failure.
                success = StringUtils.hasText(imageUrl);
            } catch (Exception e) {
                success = false;
                errorMessage = e.getMessage();
            }

            if (!StringUtils.hasText(imageUrl)) {
                imageUrl = "";
            }

            GeneratedImage image = GeneratedImage.builder()
                    .product(product)
                    .sku(sku)
                    .channelId(channel.getId())
                    .imageType(imageType)
                    .imageUrl(imageUrl)
                    .success(success)
                    .errorMessage(trimToMax(errorMessage, 2000))
                    .width(config.getWidth())
                    .height(config.getHeight())
                    .prompt(prompt)
                    .aiProvider(provider)
                    .aiModel(model)
                    .sortOrder(i)
                    .build();
            
            images.add(image);
        }
        
        return images;
    }

    private AIChannel selectEnabledChannel(List<AIChannel> enabledChannels, String requestedModel) {
        if (!StringUtils.hasText(requestedModel)) {
            return enabledChannels.get(0);
        }

        for (AIChannel channel : enabledChannels) {
            if (channel != null && StringUtils.hasText(channel.getModel()) && requestedModel.equalsIgnoreCase(channel.getModel())) {
                return channel;
            }
        }

        AIChannel fallback = enabledChannels.get(0);
        log.warn("No enabled channel matched model={}, fallback to channel id={}, model={} (platform={})",
                requestedModel,
                fallback.getId(),
                fallback.getModel(),
                fallback.getPlatform());
        return fallback;
    }

    public String generateSupplierProductImage(String prompt, String sourceImageUrl, Integer width, Integer height) {
        if (!StringUtils.hasText(prompt)) {
            throw new IllegalArgumentException("prompt is required");
        }
        List<AIChannel> channels = pickSupplierImageChannels(sourceImageUrl);
        if (channels.isEmpty()) {
            throw new IllegalStateException("没有可用的 AI 图片渠道，请先在 AI 渠道管理启用图片渠道");
        }
        RuntimeException lastError = null;
        for (AIChannel channel : channels) {
            try {
                return callAIImageAPI(
                        channel,
                        prompt.trim(),
                        "blurry, low quality, distorted product, wrong product shape, watermark, text, logo, brand name, extra objects",
                        width == null ? 800 : width,
                        height == null ? 800 : height,
                        sourceImageUrl,
                        channel.getModel()
                );
            } catch (RuntimeException e) {
                lastError = e;
                log.warn("Supplier product image channel failed, try next: channelId={}, platform={}, model={}, error={}",
                        channel.getId(), channel.getPlatform(), channel.getModel(), e.getMessage());
            }
        }
        throw lastError == null ? new IllegalStateException("没有可用的 AI 图片渠道") : lastError;
    }

    private List<AIChannel> pickSupplierImageChannels(String sourceImageUrl) {
        List<AIChannel> result = new ArrayList<>();
        List<AIChannel> gptImageChannels = channelRepository.findByModelOrderBySortOrderAsc("gpt-image-2");
        for (AIChannel channel : gptImageChannels) {
            if (channel != null && Boolean.TRUE.equals(channel.getEnabled())) {
                addImageChannel(result, channel);
            }
        }
        List<String> preferredPlatforms = StringUtils.hasText(sourceImageUrl)
                ? List.of("jimeng_i2i", "jimeng_t2i", "stability", "volcengine", "runway")
                : List.of("jimeng_t2i", "stability", "runway");
        for (String platform : preferredPlatforms) {
            List<AIChannel> channels = channelRepository.findByPlatformOrderBySortOrderAsc(platform);
            if (channels == null) {
                continue;
            }
            for (AIChannel channel : channels) {
                if (channel != null && Boolean.TRUE.equals(channel.getEnabled())) {
                    addImageChannel(result, channel);
                }
            }
        }
        channelRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                .filter(channel -> channel != null && StringUtils.hasText(channel.getPlatform()))
                .filter(channel -> preferredPlatforms.contains(channel.getPlatform()))
                .forEach(channel -> addImageChannel(result, channel));
        return result;
    }

    private void addImageChannel(List<AIChannel> channels, AIChannel channel) {
        if (channel == null || channel.getId() == null) {
            return;
        }
        boolean exists = channels.stream().anyMatch(item -> channel.getId().equals(item.getId()));
        if (!exists) {
            channels.add(channel);
        }
    }
    
    private String buildPrompt(ProductSku sku, String customPrompt, GeneratedImage.ImageType imageType) {
        String basePrompt;
        
        switch (imageType) {
            case THUMBNAIL:
                basePrompt = "E-commerce product thumbnail, clean white background, professional lighting, ";
                break;
            case CAROUSEL:
                basePrompt = "E-commerce product carousel image, lifestyle scene, natural lighting, ";
                break;
            case DETAIL:
                basePrompt = "E-commerce product detail image, close-up shot, high detail, ";
                break;
            default:
                basePrompt = "E-commerce product image, ";
        }
        
        StringBuilder prompt = new StringBuilder(basePrompt);
        
        if (customPrompt != null && !customPrompt.isEmpty()) {
            prompt.append(customPrompt).append(", ");
        }
        
        if (sku.getColor() != null) {
            prompt.append(sku.getColor()).append(" color, ");
        }
        
        if (sku.getProduct() != null && StringUtils.hasText(sku.getProduct().getMaterial())) {
            prompt.append(sku.getProduct().getMaterial()).append(" material, ");
        }
        
        if (sku.getProduct().getCategory() != null) {
            prompt.append(sku.getProduct().getCategory()).append(", ");
        }
        
        prompt.append("high quality, photorealistic, 8k");
        
        return prompt.toString();
    }
    
    private String callAIImageAPI(AIChannel channel, String prompt,
            String negativePrompt, Integer width, Integer height, String sourceImageUrl, String model) {

        String provider = channel.getPlatform();
        log.info("Calling AI image API: provider={}, model={}, size={}x{}",
                provider, model, width, height);

        if ("stability".equals(provider)) {
            return callStabilityAPI(channel, prompt, negativePrompt, width, height, model);
        } else if ("openai_image".equals(provider)
                || "openai_images".equals(provider)
                || "openai_compatible_image".equals(provider)
                || "openai".equals(provider)
                || "gpt-image-2".equalsIgnoreCase(model)) {
            return callOpenAIImageAPI(channel, prompt, width, height, sourceImageUrl, model);
        } else if ("volcengine".equals(provider)) {
            return callVolcengineAPI(channel, prompt, negativePrompt, width, height, sourceImageUrl, model);
        } else if ("jimeng_i2i".equals(provider)) {
            return callJimengI2IAPI(channel, prompt, width, height, sourceImageUrl, model);
        } else if ("jimeng_t2i".equals(provider)) {
            return callJimengT2IAPI(channel, prompt, width, height, model);
        } else if ("runway".equals(provider)) {
            return callRunwayAPI(channel, prompt, negativePrompt, width, height);
        }

        throw new UnsupportedOperationException("Unsupported AI provider: " + provider);
    }

    private String callOpenAIImageAPI(AIChannel channel,
                                      String prompt,
                                      Integer width,
                                      Integer height,
                                      String sourceImageUrl,
                                      String model) {
        try {
            String apiKey = requireNonBlank(channel.getApiKey(), "Missing OpenAI image API key in ai_channels");
            String resolvedModel = StringUtils.hasText(model) ? model.trim() : "gpt-image-2";
            int outW = width == null ? 800 : width;
            int outH = height == null ? 800 : height;

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setBearerAuth(apiKey);

            ResponseEntity<String> response;
            if (StringUtils.hasText(sourceImageUrl)) {
                ImageBytes sourceImage = downloadImageBytes(sourceImageUrl.trim());
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("model", resolvedModel);
                body.add("prompt", prompt);
                body.add("size", "1024x1024");
                body.add("quality", "medium");
                body.add("output_format", "png");
                body.add("image", new ByteArrayResource(sourceImage.bytes()) {
                    @Override
                    public String getFilename() {
                        return sourceImage.filename();
                    }
                });
                response = restTemplate.exchange(
                        TextAiUrlHelper.imageEditsUrl(channel.getBaseUrl(), null),
                        HttpMethod.POST,
                        new HttpEntity<>(body, headers),
                        String.class
                );
            } else {
                headers.setContentType(MediaType.APPLICATION_JSON);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", resolvedModel);
                body.put("prompt", prompt);
                body.put("size", "1024x1024");
                body.put("quality", "medium");
                body.put("output_format", "png");
                response = restTemplate.exchange(
                        TextAiUrlHelper.imageGenerationsUrl(channel.getBaseUrl(), null),
                        HttpMethod.POST,
                        new HttpEntity<>(objectMapper.writeValueAsString(body), headers),
                        String.class
                );
            }

            if (!response.getStatusCode().is2xxSuccessful() || !StringUtils.hasText(response.getBody())) {
                throw new IllegalStateException("OpenAI image request failed: HTTP " + response.getStatusCode().value());
            }
            byte[] imageBytes = parseOpenAIImageBytes(response.getBody());
            byte[] finalPng = resizeToPng(imageBytes, outW, outH);
            return ossService.uploadBytes("generated/openai/supplier-product", finalPng, "image/png");
        } catch (Exception e) {
            log.error("OpenAI image API call failed: {}", e.getMessage(), e);
            throw new RuntimeException("OpenAI image API call failed: " + e.getMessage(), e);
        }
    }

    private byte[] parseOpenAIImageBytes(String body) throws Exception {
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(body);
        com.fasterxml.jackson.databind.JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new IllegalStateException("OpenAI image response has no data");
        }
        com.fasterxml.jackson.databind.JsonNode first = data.get(0);
        String b64 = first.path("b64_json").asText(null);
        if (StringUtils.hasText(b64)) {
            return Base64.getDecoder().decode(b64);
        }
        String url = first.path("url").asText(null);
        if (StringUtils.hasText(url)) {
            return downloadImageBytes(url.trim()).bytes();
        }
        String error = root.path("error").path("message").asText(null);
        throw new IllegalStateException(StringUtils.hasText(error) ? error : "OpenAI image response has no image");
    }

    private ImageBytes downloadImageBytes(String imageUrl) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(toSafeUri(imageUrl))
                .timeout(Duration.ofSeconds(120))
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().length == 0) {
            throw new IllegalStateException("Download image failed: HTTP " + response.statusCode());
        }
        String filename = "source.png";
        try {
            String path = toSafeUri(imageUrl).getPath();
            if (StringUtils.hasText(path) && path.contains("/")) {
                String raw = path.substring(path.lastIndexOf('/') + 1);
                if (StringUtils.hasText(raw)) {
                    filename = raw;
                }
            }
        } catch (Exception ignored) {
        }
        return new ImageBytes(response.body(), filename);
    }

    private byte[] resizeToPng(byte[] bytes, int width, int height) throws Exception {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
        if (source == null) {
            throw new IllegalStateException("Generated image decode failed");
        }
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            double scale = Math.min((double) width / source.getWidth(), (double) height / source.getHeight());
            int drawW = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int drawH = Math.max(1, (int) Math.round(source.getHeight() * scale));
            int x = (width - drawW) / 2;
            int y = (height - drawH) / 2;
            g.drawImage(source, x, y, drawW, drawH, null);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(canvas, "png", out);
        return out.toByteArray();
    }

    private record ImageBytes(byte[] bytes, String filename) {}
    
    private String callJimengI2IAPI(AIChannel channel, String prompt,
            Integer width, Integer height, String sourceImageUrl, String model) {
        try {
            String ak = requireNonBlank(channel.getApiKey(), "Missing VOLCENGINE_API_KEY in ai_channels for platform jimeng_i2i");
            String sk = requireNonBlank(channel.getApiSecret(), "Missing VOLCENGINE_API_SECRET in ai_channels for platform jimeng_i2i");
            String reqKey = model != null ? model : "jimeng_i2i_v30";
            
            if (sourceImageUrl == null || sourceImageUrl.isEmpty()) {
                throw new IllegalArgumentException("Source image URL is required for Jimeng I2I");
            }
            
            Map<String, Object> reqBody = new HashMap<>();
            reqBody.put("req_key", reqKey);
            reqBody.put("image_urls", Collections.singletonList(sourceImageUrl));
            reqBody.put("prompt", prompt != null ? prompt : "");
            reqBody.put("width", width != null ? width : 1024);
            reqBody.put("height", height != null ? height : 1024);
            reqBody.put("scale", 0.5);
            
            String bodyStr = objectMapper.writeValueAsString(reqBody);
            String submitResponse = callVolcengineWithSignature("CVSync2AsyncSubmitTask", bodyStr, ak, sk, channel.getBaseUrl());
            
            log.info("Jimeng I2I submit response: {}", submitResponse);
            
            Map<String, Object> submitRespMap = readJsonToMap(submitResponse);
            
            if (!Integer.valueOf(10000).equals(submitRespMap.get("code"))) {
                throw new RuntimeException("Jimeng I2I submit failed: " + submitRespMap.get("message"));
            }
            
            Map<String, Object> data = asMap(submitRespMap.get("data"));
            String taskId = (String) data.get("task_id");
            
            return pollJimengResult(ak, sk, reqKey, taskId, channel.getBaseUrl());
            
        } catch (Exception e) {
            log.error("Jimeng I2I API call failed: {}", e.getMessage(), e);
            throw new RuntimeException("Jimeng I2I API call failed: " + e.getMessage(), e);
        }
    }
    
    private String callJimengT2IAPI(AIChannel channel, String prompt,
            Integer width, Integer height, String model) {
        try {
            String ak = requireNonBlank(channel.getApiKey(), "Missing VOLCENGINE_API_KEY in ai_channels for platform jimeng_t2i");
            String sk = requireNonBlank(channel.getApiSecret(), "Missing VOLCENGINE_API_SECRET in ai_channels for platform jimeng_t2i");
            String reqKey = model != null ? model : "jimeng_t2i_v31";
            
            if (prompt == null || prompt.isEmpty()) {
                throw new IllegalArgumentException("Prompt is required for Jimeng T2I");
            }
            
            Map<String, Object> reqBody = new HashMap<>();
            reqBody.put("req_key", reqKey);
            reqBody.put("prompt", prompt);
            reqBody.put("width", width != null ? width : 1024);
            reqBody.put("height", height != null ? height : 1024);
            reqBody.put("use_pre_llm", true);
            
            String bodyStr = objectMapper.writeValueAsString(reqBody);
            String submitResponse = callVolcengineWithSignature("CVSync2AsyncSubmitTask", bodyStr, ak, sk, channel.getBaseUrl());
            
            log.info("Jimeng T2I submit response: {}", submitResponse);
            
            Map<String, Object> submitRespMap = readJsonToMap(submitResponse);
            
            if (!Integer.valueOf(10000).equals(submitRespMap.get("code"))) {
                throw new RuntimeException("Jimeng T2I submit failed: " + submitRespMap.get("message"));
            }
            
            Map<String, Object> data = asMap(submitRespMap.get("data"));
            String taskId = (String) data.get("task_id");
            
            return pollJimengResult(ak, sk, reqKey, taskId, channel.getBaseUrl());
            
        } catch (Exception e) {
            log.error("Jimeng T2I API call failed: {}", e.getMessage(), e);
            throw new RuntimeException("Jimeng T2I API call failed: " + e.getMessage(), e);
        }
    }
    
    private String pollJimengResult(String ak, String sk, String reqKey, String taskId, String baseUrl) throws Exception {
        int maxRetries = 30;
        int retryDelay = 2000;
        
        for (int i = 0; i < maxRetries; i++) {
            Thread.sleep(retryDelay);
            
            Map<String, Object> reqBody = new HashMap<>();
            reqBody.put("req_key", reqKey);
            reqBody.put("task_id", taskId);
            
            String bodyStr = objectMapper.writeValueAsString(reqBody);
            String queryResponse = callVolcengineWithSignature("CVSync2AsyncGetResult", bodyStr, ak, sk, baseUrl);
            
            log.info("Jimeng poll response: {}", queryResponse);
            
            Map<String, Object> respMap = readJsonToMap(queryResponse);
            
            if (!Integer.valueOf(10000).equals(respMap.get("code"))) {
                throw new RuntimeException("Jimeng poll failed: " + respMap.get("message"));
            }
            
            Map<String, Object> data = asMap(respMap.get("data"));
            String status = (String) data.get("status");
            
            if ("done".equals(status)) {
                if (data.containsKey("image_urls")) {
                    List<String> imageUrls = asStringList(data.get("image_urls"));
                    if (imageUrls != null && !imageUrls.isEmpty()) {
                        return imageUrls.get(0);
                    }
                }
                if (data.containsKey("binary_data_base64")) {
                    List<String> base64Images = asStringList(data.get("binary_data_base64"));
                    if (base64Images != null && !base64Images.isEmpty()) {
                        return saveBase64Image(base64Images.get(0));
                    }
                }
                throw new RuntimeException("No image in done response");
            } else if ("generating".equals(status) || "in_queue".equals(status)) {
                log.info("Jimeng task status: {}, retry {}/{}", status, i + 1, maxRetries);
            } else {
                throw new RuntimeException("Jimeng task failed with status: " + status);
            }
        }
        
        throw new RuntimeException("Jimeng task polling timeout");
    }
    
    public String optimizePrompt(String originalPrompt) {
        if (aiImageConfig.getPromptOptimizer() == null || 
            !aiImageConfig.getPromptOptimizer().getEnabled()) {
            return originalPrompt;
        }
        
        try {
            AIImageConfig.PromptOptimizerConfig config = aiImageConfig.getPromptOptimizer();
            TextAiChannelResolver.ResolvedChannel aiChannel = textAiChannelResolver.resolve(
                    TextAiBusinessCodes.PROMPT_OPTIMIZER,
                    config.getBaseUrl(),
                    config.getApiKey(),
                    config.getModel()
            );
            if (!StringUtils.hasText(aiChannel.getApiKey())) {
                return originalPrompt;
            }
            
            String optimizationPrompt = "你是一个电商图片提示词优化专家。请将以下提示词优化为更适合AI图像生成的英文提示词，使其更详细、更专业、更适合电商场景。\n\n" +
                "原始提示词: " + originalPrompt + "\n\n" +
                "请直接输出优化后的英文提示词，不要包含任何解释或其他内容。";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", StringUtils.hasText(aiChannel.getModel()) ? aiChannel.getModel() : config.getModel());
            requestBody.put("messages", new Object[]{
                Map.of("role", "user", "content", optimizationPrompt)
            });
            requestBody.put("max_tokens", 500);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + aiChannel.getApiKey());
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String url = TextAiUrlHelper.chatCompletionsUrl(aiChannel.getBaseUrl(), config.getBaseUrl());
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    request,
                    String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && StringUtils.hasText(response.getBody())) {
                Map<String, Object> body = readJsonToMap(response.getBody());
                List<Map<String, Object>> choices = asListOfMaps(body.get("choices"));
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = asMap(choices.get(0).get("message"));
                    String optimizedPrompt = message != null ? (String) message.get("content") : null;
                    if (!StringUtils.hasText(optimizedPrompt)) {
                        return originalPrompt;
                    }
                    log.info("Prompt optimized: {} -> {}", originalPrompt, optimizedPrompt);
                    return optimizedPrompt;
                }
            }
            
        } catch (Exception e) {
            log.warn("Prompt optimization failed, using original: {}", e.getMessage());
        }
        
        return originalPrompt;
    }
    
    private String callStabilityAPI(AIChannel channel, String prompt,
            String negativePrompt, Integer width, Integer height, String model) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + requireNonBlank(channel.getApiKey(), "Missing STABILITY_API_KEY in ai_channels") );
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Accept", "image/*");
            
            org.springframework.util.MultiValueMap<String, Object> body = 
                    new org.springframework.util.LinkedMultiValueMap<>();
            body.add("prompt", prompt);
                body.add("model", model != null ? model : (channel.getModel() != null ? channel.getModel() : "sd3"));
            body.add("output_format", "png");
            body.add("size", width + "x" + height);
            
            HttpEntity<org.springframework.util.MultiValueMap<String, Object>> request = 
                    new HttpEntity<>(body, headers);

                HttpMethod method = Objects.requireNonNull(HttpMethod.POST);
            
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    requireNonBlank(channel.getBaseUrl(), "Missing baseUrl for stability channel") + "/v2beta/stable-image/generate/sd3",
                    method,
                    request,
                    byte[].class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return ossService.uploadBytes("generated/stability", response.getBody(), "image/png");
            }
            
            throw new RuntimeException("Stability API request failed: " + response.getStatusCode());
            
        } catch (Exception e) {
            log.error("Stability API call failed: {}", e.getMessage(), e);
            throw new RuntimeException("Stability API call failed: " + e.getMessage(), e);
        }
    }
    
    private String callVolcengineAPI(AIChannel channel, String prompt,
            String negativePrompt, Integer width, Integer height, String sourceImageUrl, String model) {
        try {
            String ak = requireNonBlank(channel.getApiKey(), "Missing VOLCENGINE_API_KEY in ai_channels");
            String sk = requireNonBlank(channel.getApiSecret(), "Missing VOLCENGINE_API_SECRET in ai_channels");
            
            String reqKey = mapModelToReqKey(model);
            
            if (sourceImageUrl == null || sourceImageUrl.isEmpty()) {
                throw new IllegalArgumentException("Source image URL is required for Volcano Engine");
            }
            
            String bodyStr = String.format(
                "{\"req_key\":\"%s\",\"image_urls\":[\"%s\"],\"return_url\":true}",
                reqKey, sourceImageUrl
            );
            
            log.info("Volcengine request: req_key={}, source_image={}", reqKey, sourceImageUrl);
            
            String response = callVolcengineWithSignature("CVProcess", bodyStr, ak, sk, channel.getBaseUrl());
            
            log.info("Volcengine response: {}", response);
            
            Map<String, Object> respMap = readJsonToMap(response);

            Object codeObj = respMap.get("code");
            Integer code = null;
            if (codeObj instanceof Number n) {
                code = n.intValue();
            } else if (codeObj != null) {
                try {
                    code = Integer.parseInt(String.valueOf(codeObj));
                } catch (Exception ignored) {
                    code = null;
                }
            }

            if (code == null || code != 10000) {
                String upstreamMessage = respMap.get("message") != null ? String.valueOf(respMap.get("message")) : null;
                String requestId = respMap.get("request_id") != null ? String.valueOf(respMap.get("request_id")) : null;
                String msg = "Volcengine API error";
                if (code != null) {
                    msg += ", code=" + code;
                }
                if (StringUtils.hasText(upstreamMessage)) {
                    msg += ", message=" + upstreamMessage;
                }
                if (StringUtils.hasText(requestId)) {
                    msg += ", request_id=" + requestId;
                }
                throw new RuntimeException(msg);
            }
            
            if (respMap.containsKey("data")) {
                Map<String, Object> data = asMap(respMap.get("data"));
                if (data == null) {
                    throw new RuntimeException("Volcengine API error: data is null");
                }
                
                if (data.containsKey("image_urls")) {
                    List<String> imageUrls = asStringList(data.get("image_urls"));
                    if (imageUrls != null && !imageUrls.isEmpty()) {
                        return imageUrls.get(0);
                    }
                }
                
                if (data.containsKey("binary_data_base64")) {
                    List<String> base64Images = asStringList(data.get("binary_data_base64"));
                    if (base64Images != null && !base64Images.isEmpty()) {
                        String base64Data = base64Images.get(0);
                        return saveBase64Image(base64Data);
                    }
                }
            }
            
            throw new RuntimeException("No image returned from Volcano Engine API");
            
        } catch (Exception e) {
            log.error("Volcengine API call failed: {}", e.getMessage(), e);
            throw new RuntimeException("Volcengine API call failed: " + e.getMessage(), e);
        }
    }
    
    private String mapModelToReqKey(String model) {
        if (model == null) {
            return "img2img_cartoon_style";
        }
        
        switch (model.toLowerCase()) {
            case "img2img_disney_3d_style":
            case "3d":
                return "img2img_disney_3d_style";
            case "img2img_real_mix_style":
            case "realistic":
                return "img2img_real_mix_style";
            case "img2img_pastel_boys_style":
            case "angel":
                return "img2img_pastel_boys_style";
            case "img2img_cartoon_style":
            case "cartoon":
                return "img2img_cartoon_style";
            case "img2img_makoto_style":
            case "anime":
                return "img2img_makoto_style";
            case "img2img_rev_animated_style":
            case "princess":
                return "img2img_rev_animated_style";
            case "img2img_blueline_style":
            case "fantasy":
                return "img2img_blueline_style";
            case "img2img_water_ink_style":
            case "ink":
                return "img2img_water_ink_style";
            case "i2i_ai_create_monet":
            case "monet_new":
                return "i2i_ai_create_monet";
            case "img2img_water_paint_style":
            case "watercolor":
                return "img2img_water_paint_style";
            case "img2img_comic_style":
            case "comic":
                return "img2img_comic_style";
            case "img2img_ceramics_style":
            case "ceramics":
                return "img2img_ceramics_style";
            case "img2img_chinese_style":
            case "chinese_red":
                return "img2img_chinese_style";
            case "img2img_clay_style":
            case "clay":
                return "img2img_clay_style";
            case "img2img_3d_style":
            case "3d_game":
                return "img2img_3d_style";
            default:
                return "img2img_cartoon_style";
        }
    }
    
    private String saveBase64Image(String base64Data) throws Exception {
        byte[] imageBytes = Base64.getDecoder().decode(base64Data);
        return ossService.uploadBytes("generated/volcengine", imageBytes, "image/png");
    }
    
    private String callVolcengineWithSignature(String action, String bodyStr, String ak, String sk, String baseUrl) throws Exception {
        String host = resolveHostFromBaseUrl(baseUrl);
        String region = "cn-north-1";
        String service = "cv";
        String version = "2022-08-31";
        
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        String xDate = sdf.format(new Date());
        String shortDate = xDate.substring(0, 8);
        
        byte[] payload = bodyStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String contentType = "application/json";
        
        String xContentSha256 = org.apache.commons.codec.digest.DigestUtils.sha256Hex(payload);
        
        String signHeader = "host;x-date;x-content-sha256;content-type";
        String canonical = "POST\n/\n" +
                "Action=" + action + "&Version=" + version + "\n" +
                "host:" + host + "\n" +
                "x-date:" + xDate + "\n" +
                "x-content-sha256:" + xContentSha256 + "\n" +
                "content-type:" + contentType + "\n\n" +
                signHeader + "\n" +
                xContentSha256;
        
        String hashCanonical = org.apache.commons.codec.digest.DigestUtils.sha256Hex(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String credentialScope = shortDate + "/" + region + "/" + service + "/request";
        String stringToSign = "HMAC-SHA256\n" + xDate + "\n" + credentialScope + "\n" + hashCanonical;
        
        byte[] signKey = deriveSignKey(sk, shortDate, region, service);
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(signKey, "HmacSHA256"));
        byte[] signatureBytes = mac.doFinal(stringToSign.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String signature = bytesToHex(signatureBytes);
        
        String auth = "HMAC-SHA256 Credential=" + ak + "/" + credentialScope +
                ", SignedHeaders=" + signHeader +
                ", Signature=" + signature;
        
        java.net.URL url = new java.net.URL("https://" + host + "/?Action=" + action + "&Version=" + version);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Host", host);
        conn.setRequestProperty("X-Date", xDate);
        conn.setRequestProperty("X-Content-Sha256", xContentSha256);
        conn.setRequestProperty("Content-Type", contentType);
        conn.setRequestProperty("Authorization", auth);
        conn.setDoOutput(true);
        
        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(payload);
            os.flush();
        }
        
        int code = conn.getResponseCode();
        java.io.InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
        byte[] respBytes = org.apache.commons.io.IOUtils.toByteArray(is);
        is.close();
        
        return new String(respBytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String resolveHostFromBaseUrl(String baseUrl) {
        // Default to Volcano Engine Visual host.
        if (!StringUtils.hasText(baseUrl)) {
            return "visual.volcengineapi.com";
        }

        try {
            java.net.URI uri = java.net.URI.create(baseUrl);
            if (StringUtils.hasText(uri.getHost())) {
                return uri.getHost();
            }
        } catch (Exception ignored) {
            // Fall through to best-effort parsing.
        }

        String trimmed = baseUrl.trim();
        trimmed = trimmed.replace("https://", "").replace("http://", "");
        int slash = trimmed.indexOf('/');
        if (slash > 0) {
            trimmed = trimmed.substring(0, slash);
        }
        return StringUtils.hasText(trimmed) ? trimmed : "visual.volcengineapi.com";
    }
    
    private byte[] deriveSignKey(String sk, String date, String region, String service) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(sk.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] kDate = mac.doFinal(date.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mac.init(new javax.crypto.spec.SecretKeySpec(kDate, "HmacSHA256"));
        byte[] kRegion = mac.doFinal(region.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mac.init(new javax.crypto.spec.SecretKeySpec(kRegion, "HmacSHA256"));
        byte[] kService = mac.doFinal(service.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mac.init(new javax.crypto.spec.SecretKeySpec(kService, "HmacSHA256"));
        return mac.doFinal("request".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
    
    private String callRunwayAPI(AIChannel channel, String prompt,
            String negativePrompt, Integer width, Integer height) {
        throw new UnsupportedOperationException("Runway API not implemented");
    }

    private Map<String, Object> readJsonToMap(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Expected object to be a map but got: " + value.getClass().getName());
    }

    private List<String> asStringList(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        throw new IllegalArgumentException("Expected object to be a list but got: " + value.getClass().getName());
    }

    private List<Map<String, Object>> asListOfMaps(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                result.add(asMap(item));
            }
            return result;
        }
        throw new IllegalArgumentException("Expected object to be a list but got: " + value.getClass().getName());
    }

    private String requireNonBlank(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
    
    @Transactional(readOnly = true)
    public List<GeneratedImageResponse> getGeneratedImages(Long productId) {
        return generatedImageRepository.findByProductId(productId).stream()
                .map(this::toGeneratedImageResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<GeneratedImageResponse> getImagesByType(Long productId, GeneratedImage.ImageType imageType) {
        return generatedImageRepository.findByProductIdAndImageType(productId, imageType).stream()
                .map(this::toGeneratedImageResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public void deleteGeneratedImages(Long productId) {
        List<GeneratedImage> images = generatedImageRepository.findByProductId(productId);
        for (GeneratedImage img : images) {
            deleteOssIfNeeded(img);
        }
        generatedImageRepository.deleteByProductId(productId);
    }

    @Transactional
    public void deleteGeneratedImage(Long productId, Long imageId) {
        GeneratedImage img = generatedImageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Generated image not found: " + imageId));
        if (img.getProduct() == null || !Objects.equals(img.getProduct().getId(), productId)) {
            throw new IllegalArgumentException("Image does not belong to product");
        }
        deleteOssIfNeeded(img);
        generatedImageRepository.delete(img);
    }

    @Transactional
    public int deleteFailedGeneratedImages(Long productId, GeneratedImage.ImageType type) {
        List<GeneratedImage> images = generatedImageRepository.findByProductId(productId);
        int deleted = 0;
        for (GeneratedImage img : images) {
            if (type != null && img.getImageType() != type) {
                continue;
            }
            boolean failed = Boolean.FALSE.equals(img.getSuccess())
                    || (StringUtils.hasText(img.getErrorMessage()) && !Boolean.TRUE.equals(img.getSuccess()));
            if (!failed) {
                continue;
            }
            // Failed images should not have OSS objects (imageUrl empty), but if they do, attempt cleanup.
            deleteOssIfNeeded(img);
            generatedImageRepository.delete(img);
            deleted++;
        }
        return deleted;
    }

    private void deleteOssIfNeeded(GeneratedImage img) {
        if (img == null) {
            return;
        }
        // Only attempt OSS deletion when we have a URL.
        if (!StringUtils.hasText(img.getImageUrl())) {
            return;
        }
        // Only delete real generated assets. Placeholder/third-party URLs are skipped by ossService.
        OssService.OssDeleteResult result = ossService.deleteByUrl(img.getImageUrl());
        if (!result.skipped() && !result.deleted()) {
            throw new RuntimeException("Failed to delete OSS object: " + result.message());
        }
    }
    
    private GeneratedImageResponse toGeneratedImageResponse(GeneratedImage image) {
        boolean success = !Boolean.FALSE.equals(image.getSuccess());
        return GeneratedImageResponse.builder()
                .id(image.getId())
                .imageType(image.getImageType().name())
                .imageUrl(image.getImageUrl())
                .width(image.getWidth())
                .height(image.getHeight())
                .prompt(image.getPrompt())
                .aiProvider(image.getAiProvider())
                .aiModel(image.getAiModel())
                .channelId(image.getChannelId())
                .success(success)
                .errorMessage(success ? null : image.getErrorMessage())
                .sortOrder(image.getSortOrder())
                .createdAt(image.getCreatedAt().toString())
                .build();
    }

    private String trimToMax(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }
}
