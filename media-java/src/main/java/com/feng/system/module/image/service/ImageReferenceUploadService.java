package com.feng.system.module.image.service;

import com.feng.system.module.image.exception.ImageApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageReferenceUploadService {
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");
    private final Path root;
    private final String publicBaseUrl;

    public ImageReferenceUploadService(@Value("${image.upload-dir:./uploads}") String uploadDir,
                                       @Value("${image.upload-public-base-url:${video.upload-public-base-url:}}") String publicBaseUrl) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    }

    public Uploaded upload(MultipartFile file, HttpServletRequest request) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_SIZE) throw new ImageApiException(422, "Invalid upload file.");
        String ext = extension(file.getOriginalFilename());
        String mime = file.getContentType() == null ? "application/octet-stream" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(ext) || !validMime(mime)) throw new ImageApiException(422, "Only image files are supported.");
        String relative = "image-references/" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "/"
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

    private String baseUrl(HttpServletRequest request) {
        if (StringUtils.hasText(publicBaseUrl)) return publicBaseUrl;
        String proto = header(request, "X-Forwarded-Proto", request.getScheme());
        String host = header(request, "X-Forwarded-Host", request.getHeader("Host"));
        return proto.split(",")[0].trim() + "://" + host.split(",")[0].trim();
    }

    private static boolean validMime(String mime) {
        return mime.startsWith("image/") || "application/octet-stream".equals(mime);
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
