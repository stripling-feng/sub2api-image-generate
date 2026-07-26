package com.feng.system.module.video.service;

import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.service.ImageGateway;
import com.feng.system.module.media.service.MediaUploadUrl;
import com.feng.system.module.system.service.UploadFileService;
import com.feng.system.module.system.vo.UploadFileVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 视频素材与产物存储服务:负责参考素材(视频/音频/图片)的上传落盘、
 * 参考图片转公网 URL,以及 omni 系列下载回来的生成视频的存储与删除。
 */
@Service
public class VideoMaterialUploadService {
    private static final long MAX_SIZE = 30L * 1024 * 1024;
    private static final List<String> EXTENSIONS = List.of("mp4", "webm", "mov", "m4v", "mp3", "wav", "m4a", "aac",
            "png", "jpg", "jpeg", "webp");
    private final UploadFileService uploadFileService;
    private final Path root;
    private final String publicBaseUrl;

    @Autowired
    public VideoMaterialUploadService(UploadFileService uploadFileService,
                                      @Value("${image.upload-dir:./uploads}") String uploadDir,
                                      @Value("${video.upload-public-base-url:}") String publicBaseUrl) {
        this.uploadFileService = uploadFileService;
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    }

    /** 便捷构造(不接上传服务),主要用于测试场景。 */
    public VideoMaterialUploadService(String uploadDir, String publicBaseUrl) {
        this(null, uploadDir, publicBaseUrl);
    }

    /**
     * 上传参考素材:限制大小(30MB)、扩展名白名单,MIME 必须是视频/音频/图片或二进制流,
     * 通过后交由通用上传服务落盘并返回公网 URL。
     */
    public Uploaded upload(MultipartFile file, HttpServletRequest request) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_SIZE) throw new ImageApiException(422, "Invalid upload file.");
        String ext = extension(file.getOriginalFilename());
        String mime = file.getContentType() == null ? "application/octet-stream" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(ext) || !(mime.startsWith("video/") || mime.startsWith("audio/")
                || mime.startsWith("image/") || "application/octet-stream".equals(mime)))
            throw new ImageApiException(422, "Only video, audio, or image files are supported.");
        return uploadWithDefaultService(file, mime, file.getSize());
    }

    /** 将内存中的参考图片(png/jpeg/webp)转存为可公开访问的 URL,供上游按 URL 拉取。 */
    public Uploaded uploadImage(ImageGateway.Upload image) {
        String ext = switch (image.mimeType()) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> throw new ImageApiException(422, "Unsupported or invalid reference image.");
        };
        return uploadWithDefaultService(new BytesMultipartFile("file", image.name(), image.mimeType(), image.bytes()),
                image.mimeType(), image.bytes().length);
    }

    /**
     * 存储下载回来的生成视频:校验 jobId 合法性、mp4/webm 文件头签名与公网基础 URL 配置,
     * 以 jobId 命名写入 generated-videos 目录(已存在则跳过写入,保证幂等)。
     */
    public Uploaded storeGenerated(String jobId, byte[] bytes, String contentType) {
        String mime = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        String ext = switch (mime) { case "video/mp4" -> "mp4"; case "video/webm" -> "webm"; default -> null; };
        // 校验文件头魔数:mp4 需含 ftyp,webm 需为 EBML 头,防止存入伪装内容
        boolean signature = "mp4".equals(ext) && bytes != null && bytes.length >= 8
                && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p'
                || "webm".equals(ext) && bytes != null && bytes.length >= 4
                && bytes[0] == 0x1a && bytes[1] == 0x45 && bytes[2] == (byte) 0xdf && bytes[3] == (byte) 0xa3;
        if (!StringUtils.hasText(publicBaseUrl) || !StringUtils.hasText(jobId) || !jobId.matches("[A-Za-z0-9_-]+") || !signature)
            throw new ImageApiException(502, "Invalid generated video content.");
        String relative = "generated-videos/" + jobId + "." + ext;
        Path target = root.resolve(relative).normalize();
        // 防止路径穿越:目标必须落在上传根目录内
        if (!target.startsWith(root)) throw new ImageApiException(502, "Invalid generated video content.");
        try {
            Files.createDirectories(target.getParent());
            if (!Files.exists(target)) Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        } catch (Exception e) {
            throw new ImageApiException(502, "Generated video storage failed.");
        }
        return new Uploaded(publicBaseUrl + "/uploads/" + relative, target.toString(), mime, Files.exists(target) ? size(target) : bytes.length);
    }

    /** 删除某任务已落盘的生成视频文件(mp4/webm 均尝试删除),jobId 非法时静默忽略。 */
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

    private Uploaded uploadWithDefaultService(MultipartFile file, String mime, long sizeBytes) {
        if (uploadFileService == null) throw new ImageApiException(500, "Upload service is not configured.");
        UploadFileVO uploaded = uploadFileService.upload(file, materialDirectory());
        String url = MediaUploadUrl.requireHttpUrl(uploaded.getFilePath());
        return new Uploaded(url, uploaded.getFilePath(), mime, sizeBytes);
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

    /** 上传/存储结果:公网 URL、本地文件路径、MIME 类型与文件大小。 */
    public record Uploaded(String url, String filePath, String mimeType, long sizeBytes) {}

    /** 内存字节数组的 MultipartFile 适配器,用于把参考图片交给通用上传服务。 */
    private record BytesMultipartFile(String name, String originalFilename, String contentType, byte[] bytes)
            implements MultipartFile {
        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return bytes == null || bytes.length == 0; }
        @Override public long getSize() { return bytes == null ? 0 : bytes.length; }
        @Override public byte[] getBytes() { return bytes == null ? new byte[0] : bytes; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(getBytes()); }
        @Override public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), getBytes()); }
        @Override public void transferTo(Path dest) throws IOException { Files.write(dest, getBytes()); }
    }
}
