package com.feng.system.module.image.service;

import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.media.service.MediaUploadUrl;
import com.feng.system.module.system.service.UploadFileService;
import com.feng.system.module.system.vo.UploadFileVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

/**
 * 参考图上传服务：接收用户上传的参考图片，校验大小、扩展名与 MIME 类型后
 * 存入素材目录，并返回可供生成请求引用的 HTTP 访问地址。
 */
@Service
public class ImageReferenceUploadService {
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");
    private final UploadFileService uploadFileService;

    public ImageReferenceUploadService(UploadFileService uploadFileService) {
        this.uploadFileService = uploadFileService;
    }

    /**
     * 上传一张参考图：限制 10MB 以内、扩展名为 png/jpg/jpeg/gif/webp，
     * 保存到按日期划分的素材目录后返回文件的公开 URL 与元信息。
     */
    public Uploaded upload(MultipartFile file, HttpServletRequest request) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_SIZE) throw new ImageApiException(422, "Invalid upload file.");
        String ext = extension(file.getOriginalFilename());
        String mime = file.getContentType() == null ? "application/octet-stream" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(ext) || !validMime(mime)) throw new ImageApiException(422, "Only image files are supported.");
        UploadFileVO uploaded = uploadFileService.upload(file, materialDirectory());
        String url = MediaUploadUrl.requireHttpUrl(uploaded.getFilePath());
        return new Uploaded(url, uploaded.getFilePath(), mime, file.getSize());
    }

    private static boolean validMime(String mime) {
        return mime.startsWith("image/") || "application/octet-stream".equals(mime);
    }

    private static String materialDirectory() {
        return "material/" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static String extension(String name) {
        if (!StringUtils.hasText(name)) throw new ImageApiException(422, "Invalid upload file.");
        int index = name.lastIndexOf('.');
        if (index < 0 || index == name.length() - 1) throw new ImageApiException(422, "Invalid upload file.");
        return name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    /** 上传结果：公开访问 URL、存储路径、MIME 类型与文件大小（字节）。 */
    public record Uploaded(String url, String filePath, String mimeType, long sizeBytes) {}
}
