package com.feng.system.module.image.service;

import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.support.ImageTime;
import com.feng.system.module.media.service.MediaTaskResultService;
import com.feng.system.module.media.entity.MediaTaskResult;
import com.feng.system.module.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 图片存储服务:将上游生成的图片(Base64、URL 或字节流)落盘到上传目录,
 * 识别真实图片格式与尺寸,并写入任务结果记录。
 */
@Service
@RequiredArgsConstructor
public class ImageStorageService {
    private final MediaTaskResultService results;
    private final ImageGateway gateway;
    private final SystemConfigService systemConfigService;
    @Value("${image.upload-dir:./uploads}") private String uploadDir;
    private volatile S3Client cachedR2Client;
    private volatile String cachedR2Key;

    /**
     * 保存 Base64 编码的图片,兼容带 data URI 前缀(data:image/...;base64,)的形式。
     *
     * @param index 同一任务内的图片序号(从 0 开始)
     */
    public MediaTaskResult saveBase64(String taskId, int index, String value) {
        String raw = value.contains(",") ? value.substring(value.indexOf(',') + 1) : value;
        return save(taskId, index, Base64.getDecoder().decode(raw), null);
    }

    /**
     * 从上游 URL 下载图片并保存。
     */
    public MediaTaskResult saveUrl(String taskId, int index, String url) {
        ImageGateway.Download download = gateway.download(url);
        return save(taskId, index, download.bytes(), download.mimeType());
    }

    /**
     * 将图片字节写入按日期分目录的上传路径,并登记结果记录(含尺寸、大小等元数据)。
     *
     * @param declaredMime 上游声明的 MIME 类型,仅在无法通过魔数识别时作为兜底
     */
    public MediaTaskResult save(String taskId, int index, byte[] bytes, String declaredMime) {
        if (bytes == null || bytes.length == 0) throw new ImageApiException(502, "Generated image is empty.");
        String mime = mime(bytes, declaredMime);
        String extension = extension(mime);
        String date = LocalDate.now(ZoneOffset.UTC).toString();
        String name = "tcboys.de_" + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().substring(0, 8) + "." + extension;
        if ("cloudflare-r2".equals(systemConfigService.getUploadProvider())) {
            return saveR2(taskId, index, bytes, mime, date, name);
        }
        Path dir = Path.of(uploadDir).toAbsolutePath().normalize().resolve("images").resolve(date);
        Path file = dir.resolve(name);
        try {
            Files.createDirectories(dir);
            Files.write(file, bytes);
        } catch (Exception e) {
            throw new ImageApiException(500, "Unable to persist generated image.");
        }
        int[] dimensions = dimensions(bytes);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("filePath", file.toString());
        metadata.put("mimeType", mime);
        metadata.put("width", dimensions[0] == 0 ? null : dimensions[0]);
        metadata.put("height", dimensions[1] == 0 ? null : dimensions[1]);
        metadata.put("sizeBytes", bytes.length);
        String address = "/img/" + date + "/" + name;
        try {
            // saveIfAbsent 保证同一 (taskId, index) 只登记一次;若已有记录则删除本次重复写入的文件
            MediaTaskResult result = results.saveIfAbsent(taskId, index, address, metadata);
            if (!address.equals(result.getAddress())) deleteQuietly(file);
            return result;
        } catch (RuntimeException error) {
            // 入库失败时回滚已写入的文件,避免孤儿文件
            deleteQuietly(file);
            throw error;
        }
    }

    private MediaTaskResult saveR2(String taskId, int index, byte[] bytes, String mime, String date, String name) {
        MediaTaskResult existing = results.find(taskId, index);
        if (existing != null) return existing;
        String objectKey = "images/" + date + "/" + name;
        putR2Object(objectKey, bytes, mime);
        int[] dimensions = dimensions(bytes);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("storageProvider", "cloudflare-r2");
        metadata.put("objectKey", objectKey);
        metadata.put("mimeType", mime);
        metadata.put("width", dimensions[0] == 0 ? null : dimensions[0]);
        metadata.put("height", dimensions[1] == 0 ? null : dimensions[1]);
        metadata.put("sizeBytes", bytes.length);
        String address = buildObjectUrl(r2Domain(), r2Endpoint(), r2Bucket(), objectKey);
        try {
            MediaTaskResult result = results.saveIfAbsent(taskId, index, address, metadata);
            if (!address.equals(result.getAddress())) deleteR2Object(objectKey);
            return result;
        } catch (RuntimeException error) {
            deleteR2Object(objectKey);
            throw error;
        }
    }

    public void putR2Object(String objectKey, byte[] bytes, String mime) {
        try {
            getR2Client().putObject(PutObjectRequest.builder()
                            .bucket(r2Bucket())
                            .key(objectKey)
                            .contentType(StringUtils.hasText(mime) ? mime : "application/octet-stream")
                            .build(),
                    RequestBody.fromBytes(bytes));
        } catch (Exception error) {
            throw new ImageApiException(500, "Cloudflare R2 image upload failed.");
        }
    }

    /**
     * 判断指定任务序号的图片结果是否已存在(用于轮询时避免重复下载保存)。
     */
    public boolean exists(String taskId, int index) {
        return results.exists(taskId, index);
    }

    public ImageGateway.Download download(MediaTaskResult result) {
        if (result == null) throw new ImageApiException(404, "Image not found.");
        Map<String, Object> metadata = results.metadata(result);
        Object filePath = metadata.get("filePath");
        if (filePath != null) {
            try {
                Path root = Path.of(uploadDir).toAbsolutePath().normalize();
                Path file = Path.of(String.valueOf(filePath)).toAbsolutePath().normalize();
                if (!file.startsWith(root)) throw new ImageApiException(403, "Image path is not allowed.");
                String mime = String.valueOf(metadata.getOrDefault("mimeType", "image/png"));
                return new ImageGateway.Download(Files.readAllBytes(file), mime);
            } catch (ImageApiException error) {
                throw error;
            } catch (Exception error) {
                throw new ImageApiException(404, "Image file not found.");
            }
        }
        return gateway.download(result.getAddress());
    }

    public void delete(MediaTaskResult result) {
        if (result == null) return;
        Map<String, Object> metadata = results.metadata(result);
        if ("cloudflare-r2".equals(metadata.get("storageProvider"))) {
            Object objectKey = metadata.get("objectKey");
            if (objectKey != null) deleteR2Object(String.valueOf(objectKey));
            return;
        }
        Object value = metadata.get("filePath");
        if (value == null) return;
        try {
            Path root = Path.of(uploadDir).toAbsolutePath().normalize();
            Path file = Path.of(String.valueOf(value)).toAbsolutePath().normalize();
            if (file.startsWith(root)) Files.deleteIfExists(file);
        } catch (Exception ignored) { }
    }

    public void deleteR2Object(String objectKey) {
        if (!StringUtils.hasText(objectKey)) return;
        try {
            getR2Client().deleteObject(DeleteObjectRequest.builder()
                    .bucket(r2Bucket())
                    .key(objectKey)
                    .build());
        } catch (Exception ignored) { }
    }

    private int[] dimensions(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            return image == null ? new int[2] : new int[]{image.getWidth(), image.getHeight()};
        } catch (Exception e) {
            return new int[2];
        }
    }

    private String mime(byte[] bytes, String declared) {
        // 通过文件头魔数识别真实格式,优先于上游声明的 MIME 类型
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P') return "image/png";
        if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8) return "image/jpeg";
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I') return "image/gif";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[8] == 'W') return "image/webp";
        if (declared != null && declared.toLowerCase(Locale.ROOT).startsWith("image/")) return declared.split(";", 2)[0];
        throw new ImageApiException(502, "Unsupported generated image format.");
    }

    private String extension(String mime) {
        return switch (mime) {
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "png";
        };
    }

    private S3Client getR2Client() {
        String bucket = r2Bucket();
        String endpoint = r2ApiEndpoint(r2Endpoint(), bucket);
        String accessKeyId = requiredConfig("upload.r2.access-key-id", "Cloudflare R2 AccessKeyId");
        String accessKeySecret = requiredConfig("upload.r2.access-key-secret", "Cloudflare R2 AccessKeySecret");
        String key = endpoint + "|" + accessKeyId;
        if (cachedR2Client != null && key.equals(cachedR2Key)) return cachedR2Client;
        synchronized (this) {
            if (cachedR2Client != null && key.equals(cachedR2Key)) return cachedR2Client;
            if (cachedR2Client != null) cachedR2Client.close();
            cachedR2Client = S3Client.builder()
                    .endpointOverride(URI.create(normalizeHttpEndpoint(endpoint)))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKeyId, accessKeySecret)))
                    .region(Region.of("auto"))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .chunkedEncodingEnabled(false)
                            .build())
                    .build();
            cachedR2Key = key;
            return cachedR2Client;
        }
    }

    private String r2Endpoint() {
        return requiredConfig("upload.r2.endpoint", "Cloudflare R2 Endpoint");
    }

    private String r2Bucket() {
        return requiredConfig("upload.r2.bucket", "Cloudflare R2 Bucket");
    }

    private String r2Domain() {
        return systemConfigService.getConfigValue("upload.r2.domain");
    }

    private String requiredConfig(String key, String label) {
        String value = systemConfigService.getConfigValue(key);
        if (!StringUtils.hasText(value)) throw new ImageApiException(500, label + " is not configured.");
        return value.trim();
    }

    private String buildObjectUrl(String domain, String endpoint, String bucket, String objectKey) {
        if (StringUtils.hasText(domain)) {
            return trimTrailingSlash(normalizeHttpEndpoint(domain)) + "/" + objectKey;
        }
        return trimTrailingSlash(normalizeHttpEndpoint(endpoint)) + "/" + objectKey;
    }

    private String r2ApiEndpoint(String endpoint, String bucket) {
        String normalized = trimTrailingSlash(normalizeHttpEndpoint(endpoint));
        if (StringUtils.hasText(bucket) && normalized.endsWith("/" + bucket)) {
            return normalized.substring(0, normalized.length() - bucket.length() - 1);
        }
        return normalized;
    }

    private String normalizeHttpEndpoint(String endpoint) {
        String trimmed = endpoint.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed;
        return "https://" + trimmed;
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private void deleteQuietly(Path file) {
        try { Files.deleteIfExists(file); }
        catch (Exception ignored) { }
    }
}
