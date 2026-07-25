package com.feng.system.module.video.service;

import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.service.ImageGateway;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class VideoMaterialUploadService {
    private static final long MAX_SIZE = 30L * 1024 * 1024;
    private static final List<String> EXTENSIONS = List.of("mp4", "webm", "mov", "m4v", "mp3", "wav", "m4a", "aac");
    private final Path root;
    private final String publicBaseUrl;

    public VideoMaterialUploadService(@Value("${image.upload-dir:./uploads}") String uploadDir,
                                      @Value("${video.upload-public-base-url:}") String publicBaseUrl) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    }

    public Uploaded upload(MultipartFile file, HttpServletRequest request) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_SIZE) throw new ImageApiException(422, "Invalid upload file.");
        String ext = extension(file.getOriginalFilename());
        String mime = file.getContentType() == null ? "application/octet-stream" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(ext) || !(mime.startsWith("video/") || mime.startsWith("audio/") || "application/octet-stream".equals(mime)))
            throw new ImageApiException(422, "Only video or audio files are supported.");
        String relative = "video-materials/" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) throw new ImageApiException(422, "Invalid upload file.");
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (Exception e) {
            throw new ImageApiException(500, "Upload file failed.");
        }
        String url = baseUrl(request) + "/uploads/" + relative.replace('\\', '/');
        return new Uploaded(url, target.toString(), mime, file.getSize());
    }

    public Uploaded uploadImage(ImageGateway.Upload image) {
        String ext = switch (image.mimeType()) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> throw new ImageApiException(422, "Unsupported or invalid reference image.");
        };
        return store(image.bytes(), "video-materials/" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext, image.mimeType());
    }

    public Uploaded storeGenerated(String jobId, byte[] bytes, String contentType) {
        String mime = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        String ext = switch (mime) { case "video/mp4" -> "mp4"; case "video/webm" -> "webm"; default -> null; };
        boolean signature = "mp4".equals(ext) && bytes != null && bytes.length >= 8
                && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p'
                || "webm".equals(ext) && bytes != null && bytes.length >= 4
                && bytes[0] == 0x1a && bytes[1] == 0x45 && bytes[2] == (byte) 0xdf && bytes[3] == (byte) 0xa3;
        if (!StringUtils.hasText(publicBaseUrl) || !StringUtils.hasText(jobId) || !jobId.matches("[A-Za-z0-9_-]+") || !signature)
            throw new ImageApiException(502, "Invalid generated video content.");
        String relative = "generated-videos/" + jobId + "." + ext;
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) throw new ImageApiException(502, "Invalid generated video content.");
        try {
            Files.createDirectories(target.getParent());
            if (!Files.exists(target)) Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        } catch (Exception e) {
            throw new ImageApiException(502, "Generated video storage failed.");
        }
        return new Uploaded(publicBaseUrl + "/uploads/" + relative, target.toString(), mime, Files.exists(target) ? size(target) : bytes.length);
    }

    public void deleteGenerated(String jobId) {
        if (!StringUtils.hasText(jobId) || !jobId.matches("[A-Za-z0-9_-]+")) return;
        try {
            Files.deleteIfExists(root.resolve("generated-videos/" + jobId + ".mp4"));
            Files.deleteIfExists(root.resolve("generated-videos/" + jobId + ".webm"));
        } catch (Exception e) {
            throw new ImageApiException(500, "Generated video deletion failed.");
        }
    }

    private static long size(Path path) {
        try { return Files.size(path); }
        catch (Exception e) { throw new ImageApiException(502, "Generated video storage failed."); }
    }

    private Uploaded store(byte[] bytes, String relative, String mime) {
        if (!StringUtils.hasText(publicBaseUrl)) throw new ImageApiException(500, "Video upload public base URL is not configured.");
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) throw new ImageApiException(422, "Invalid upload file.");
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        } catch (Exception e) {
            throw new ImageApiException(500, "Upload file failed.");
        }
        return new Uploaded(publicBaseUrl + "/uploads/" + relative.replace('\\', '/'), target.toString(), mime, bytes.length);
    }

    private String baseUrl(HttpServletRequest request) {
        if (StringUtils.hasText(publicBaseUrl)) return publicBaseUrl;
        String proto = header(request, "X-Forwarded-Proto", request.getScheme());
        String host = header(request, "X-Forwarded-Host", request.getHeader("Host"));
        return proto.split(",")[0].trim() + "://" + host.split(",")[0].trim();
    }

    private static String header(HttpServletRequest request, String name, String fallback) {
        String value = request.getHeader(name);
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static String extension(String name) {
        if (!StringUtils.hasText(name)) throw new ImageApiException(422, "Invalid upload file.");
        int index = name.lastIndexOf('.');
        if (index < 0 || index == name.length() - 1) throw new ImageApiException(422, "Invalid upload file.");
        return name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    public record Uploaded(String url, String filePath, String mimeType, long sizeBytes) {}
}
