package com.feng.system.module.image;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.entity.ApiProfile;
import com.feng.system.module.image.entity.GenerationJob;
import com.feng.system.module.image.mapper.GenerationJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ImageGenerationService {
    private final GenerationJobMapper jobs;
    private final ImageGateway gateway;
    private final ImageGenerationWorker worker;
    private final Sub2apiBillingService billing;
    private final ImageModelConfigService modelConfigs;
    private final ObjectMapper json;
    @Value("${image.charge-on-failure:false}") private boolean chargeOnFailure;

    public Accepted generate(ApiProfile profile, Map<String, Object> raw, List<ImageGateway.Upload> uploads, ImageGateway.Upload uploadedMask) {
        String modelKey = text(raw.get("model"));
        if (modelKey == null) throw new ImageApiException(422, "Invalid request.");
        Input input = parse(raw, uploads, uploadedMask, modelConfigs.requireImage(modelKey));
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        if (Integer.valueOf(1).equals(input.runtime.model().getAsyncMode())) {
            for (int index = 1; index <= input.count; index++) {
                try { createAsync(profile, input, requestId, index, input.count); }
                catch (Exception ignored) { /* failed child is already persisted; continue the batch */ }
            }
        }
        else createBackground(profile, input, requestId);
        return new Accepted(requestId, input.count);
    }

    private void createAsync(ApiProfile profile, Input input, String requestId, int index, int total) {
        GenerationJob job = newJob(profile, input, requestId, index, total, 1);
        jobs.insert(job);
        Sub2apiBillingService.Reservation reservation = null;
        try {
            reservation = billing.reserve(profile.getEncryptedKey(), 1, input.runtime.model().getUnitPriceUsd());
            job.setBillingStatus("RESERVED"); job.setBillingAmount(reservation.amountUsd()); job.setBillingApiKeyId(reservation.apiKeyId());
            job.setBillingUserId(reservation.userId()); job.setBillingAccountId(reservation.accountId()); job.setBillingReservedAt(ImageTime.now());
            jobs.updateById(job);
            if (chargeOnFailure) {
                String usageId = billing.settle(job.getId(), reservation.apiKeyId(), reservation.userId(), reservation.accountId(),
                        reservation.amountUsd(), 1, input.size, input.images.isEmpty() ? "generations" : "edits", null);
                job.setBillingStatus("CHARGED"); job.setBillingUsageLogId(usageId); job.setBillingSettledAt(ImageTime.now()); jobs.updateById(job);
            }
            Map<String, Object> body = new LinkedHashMap<>(input.requestParams);
            body.put("model", input.runtime.model().getModelKey()); body.put("prompt", prompt(input)); body.put("n", 1);
            ImageGateway.GatewayResponse response = gateway.create(input.runtime.provider().getBaseUrl(), input.runtime.provider().getImageApiKey(),
                    input.runtime.model().getGenerationPath(), input.runtime.model().getEditPath(), body, input.images, input.mask);
            ImageGateway.Task task = gateway.parseTask(response.payload());
            if (task.id() == null) throw new ImageApiException(502, "Upstream image task response is missing id.", null, response.payload());
            job.setUpstreamTaskId(task.id()); job.setUpstreamOperation(input.images.isEmpty() ? "generations" : "edits");
            job.setUpstreamStatus(task.status() == null ? "queued" : task.status()); job.setProgress(task.progress());
            job.setNextPollAt(ImageTime.now()); job.setUpdatedAt(ImageTime.now()); jobs.updateById(job);
        } catch (Exception e) {
            failReservation(job, reservation, input, e);
            throw e;
        }
    }

    private void createBackground(ApiProfile profile, Input input, String requestId) {
        for (int i = 1; i <= input.count; i++) {
            GenerationJob job = newJob(profile, input, requestId, i, input.count, 1);
            jobs.insert(job);
            Map<String, Object> body = new LinkedHashMap<>(input.requestParams);
            body.put("model", input.runtime.model().getModelKey()); body.put("prompt", prompt(input)); body.put("n", 1);
            worker.run(job.getId(), input.runtime.provider().getBaseUrl(), input.runtime.provider().getImageApiKey(),
                    input.runtime.model().getGenerationPath(), input.runtime.model().getEditPath(), body, input.images, input.mask);
        }
    }

    private void failReservation(GenerationJob job, Sub2apiBillingService.Reservation reservation, Input input, Exception failure) {
        String billingStatus = reservation == null ? "FAILED" : job.getBillingStatus();
        try {
            if (reservation != null && !"CHARGED".equals(job.getBillingStatus())) {
                if (chargeOnFailure) {
                    String usage = billing.settle(job.getId(), reservation.apiKeyId(), reservation.userId(), reservation.accountId(), reservation.amountUsd(),
                            1, input.size, input.images.isEmpty() ? "generations" : "edits", null);
                    billingStatus = "CHARGED"; job.setBillingUsageLogId(usage); job.setBillingSettledAt(ImageTime.now());
                } else {
                    billing.release(job.getId(), reservation.apiKeyId(), reservation.userId(), reservation.amountUsd()); billingStatus = "RELEASED";
                }
            }
        } catch (Exception billingFailure) { billingStatus = chargeOnFailure ? "CHARGE_FAILED" : "RELEASE_FAILED"; job.setBillingError(message(billingFailure)); }
        job.setStatus("FAILED"); job.setUpstreamStatus("create_failed"); job.setErrorMessage(message(failure)); job.setBillingStatus(billingStatus);
        job.setCompletedAt(ImageTime.now()); job.setUpdatedAt(ImageTime.now()); jobs.updateById(job);
    }

    private GenerationJob newJob(ApiProfile profile, Input input, String requestId, int index, int total, int count) {
        GenerationJob job = new GenerationJob();
        job.setModelConfigId(input.runtime.model().getId());
        job.setId(id()); job.setProfileId(profile.getId()); job.setPrompt(input.prompt); job.setNegativePrompt(input.negativePrompt);
        job.setModel(input.model); job.setSize(input.size); job.setQuality(input.quality); job.setStyle(input.style); job.setCount(count);
        job.setResponseFormat(input.responseFormat); job.setStatus("PENDING"); job.setProgress(0); job.setPollErrorCount(0);
        Map<String, Object> params = new LinkedHashMap<>(input.requestParams); params.put("request_id", requestId);
        params.put("request_index", index); params.put("request_total", total); params.put("response_format", input.responseFormat);
        params.put("output_format", input.outputFormat); params.put("reference_image_count", input.images.size()); params.put("has_mask", input.mask != null);
        job.setParams(stringify(params)); job.setCreatedAt(ImageTime.now()); job.setUpdatedAt(ImageTime.now()); return job;
    }

    @SuppressWarnings("unchecked")
    private Input parse(Map<String, Object> raw, List<ImageGateway.Upload> uploaded, ImageGateway.Upload uploadedMask,
                        ImageModelConfigService.RuntimeModel runtime) {
        String prompt = text(raw.get("prompt")); String model = runtime.model().getModelKey();
        int count = number(raw.get("count"), 1);
        if (prompt == null || count < 1 || count > runtime.model().getMaxCount()) throw new ImageApiException(422, "Invalid request.");
        List<ImageGateway.Upload> images = new ArrayList<>(uploaded);
        if (images.isEmpty() && raw.get("referenceImages") instanceof List<?> refs) for (Object ref : refs) images.add(base64Upload(ref));
        ImageGateway.Upload mask = uploadedMask;
        if (mask == null && raw.get("mask") != null) mask = base64Upload(raw.get("mask"));
        if (images.size() > runtime.model().getMaxReferenceImages() || images.stream().anyMatch(file -> file.bytes().length > 10 * 1024 * 1024 || !validImage(file.bytes())))
            throw new ImageApiException(422, "Unsupported or invalid image.");
        if (mask != null && (!Integer.valueOf(1).equals(runtime.model().getSupportsMask()) || images.isEmpty()
                || !"image/png".equals(mask.mimeType()) || !isPng(mask.bytes())))
            throw new ImageApiException(422, "A mask requires a reference image and must be PNG.");
        if (mask != null && !sameDimensions(images.get(0), mask)) throw new ImageApiException(422, "Mask dimensions must match the first reference image.");
        Map<String, Object> supplied = new LinkedHashMap<>();
        if (raw.get("parameters") instanceof Map<?, ?> map) supplied.putAll(stringMap(map));
        if (raw.get("extraParams") instanceof Map<?, ?> map) supplied.putAll(stringMap(map));
        put(supplied, "size", raw.get("size")); put(supplied, "quality", raw.get("quality"));
        put(supplied, "aspect_ratio", raw.get("aspectRatio"));
        Map<String, Object> requestParams = ImageModelRules.merge(runtime.model().getParameterSchema(), runtime.model().getDefaultParams(), supplied);
        String size = value(requestParams.get("size"), "auto");
        String responseFormat = value(requestParams.get("response_format"), "url");
        String outputFormat = text(requestParams.get("output_format"));
        return new Input(prompt, text(raw.get("negativePrompt")), model, size, text(requestParams.get("quality")),
                text(raw.get("style")), count, responseFormat, outputFormat, requestParams, images, mask, runtime);
    }

    private ImageGateway.Upload base64Upload(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new ImageApiException(422, "Invalid image payload.");
        try { return new ImageGateway.Upload(value(map.get("name"), "image"), value(map.get("mimeType"), "application/octet-stream"),
                Base64.getDecoder().decode(value(map.get("data"), ""))); }
        catch (Exception e) { throw new ImageApiException(422, "Invalid image payload."); }
    }
    private boolean sameDimensions(ImageGateway.Upload a, ImageGateway.Upload b) {
        try { var one = ImageIO.read(new ByteArrayInputStream(a.bytes())); var two = ImageIO.read(new ByteArrayInputStream(b.bytes()));
            return one != null && two != null && one.getWidth() == two.getWidth() && one.getHeight() == two.getHeight(); }
        catch (Exception e) { return false; }
    }
    private boolean validImage(byte[] bytes) {
        if (isPng(bytes)) return true;
        if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff) return true;
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') return true;
        return bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }
    private boolean isPng(byte[] bytes) {
        return bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
    }
    private String prompt(Input input) { return input.negativePrompt == null ? input.prompt : input.prompt + "\n\nNegative prompt: " + input.negativePrompt; }
    private String stringify(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new ImageApiException(422, "Invalid request."); } }
    private static Map<String, Object> stringMap(Map<?, ?> map) { Map<String,Object> result=new LinkedHashMap<>(); map.forEach((k,v)->result.put(String.valueOf(k),v)); return result; }
    private static void put(Map<String, Object> map, String key, Object value) { if (value != null) map.put(key, value); }
    private static String text(Object value) { return value instanceof String text && !text.isBlank() ? text : null; }
    private static String value(Object value, String fallback) { String text = text(value); return text == null ? fallback : text; }
    private static int number(Object value, int fallback) { return value instanceof Number n ? n.intValue() : fallback; }
    private static String id() { return UUID.randomUUID().toString().replace("-", ""); }
    private static String message(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }

    private record Input(String prompt, String negativePrompt, String model, String size, String quality, String style, int count,
                         String responseFormat, String outputFormat, Map<String, Object> requestParams,
                         List<ImageGateway.Upload> images, ImageGateway.Upload mask, ImageModelConfigService.RuntimeModel runtime) {}
    public record Accepted(String requestId, int count) {}
}
