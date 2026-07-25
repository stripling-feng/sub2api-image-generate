package com.feng.system.module.system.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feng.system.common.api.PageResult;
import com.feng.system.common.exception.BusinessException;
import com.feng.system.module.system.dto.UploadFileQueryDTO;
import com.feng.system.module.system.entity.SysUploadFile;
import com.feng.system.module.system.mapper.SysUploadFileMapper;
import com.feng.system.module.system.service.SystemConfigService;
import com.feng.system.module.system.service.UploadFileService;
import com.feng.system.module.system.vo.UploadFileVO;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadFileServiceImpl implements UploadFileService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "csv", "zip", "rar", "7z",
            "mp3", "mp4", "avi", "mov", "wmv", "flv", "webm",
            "html", "css", "js", "json", "xml", "yml", "yaml", "md"
    );

    private final SysUploadFileMapper uploadFileMapper;
    private final SystemConfigService systemConfigService;

    private volatile MinioClient cachedMinioClient;
    private volatile String cachedMinioKey;

    private volatile OSS cachedOssClient;
    private volatile String cachedOssKey;

    @Override
    public PageResult<UploadFileVO> page(UploadFileQueryDTO queryDTO) {
        LambdaQueryWrapper<SysUploadFile> wrapper = new LambdaQueryWrapper<SysUploadFile>()
                .eq(SysUploadFile::getDeleted, 0)
                .like(StringUtils.hasText(queryDTO.getOriginalName()), SysUploadFile::getOriginalName, queryDTO.getOriginalName())
                .like(StringUtils.hasText(queryDTO.getCurrentName()), SysUploadFile::getCurrentName, queryDTO.getCurrentName())
                .eq(StringUtils.hasText(queryDTO.getFileType()), SysUploadFile::getFileType, queryDTO.getFileType())
                .orderByDesc(SysUploadFile::getId);
        Page<SysUploadFile> page = uploadFileMapper.selectPage(new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize()), wrapper);
        return PageResult.of(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getRecords().stream().map(this::toVO).toList()
        );
    }

    @Override
    public UploadFileVO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String originalName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "unknown";
        String extension = getExtension(originalName).toLowerCase();
        if (StringUtils.hasText(extension) && !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("不支持的文件类型: ." + extension);
        }

        try {
            // Streaming MD5: don't load entire file into memory
            String md5Value;
            try (InputStream is = file.getInputStream()) {
                md5Value = DigestUtils.md5DigestAsHex(is);
            }
            SysUploadFile existingFile = findByMd5(md5Value);
            if (existingFile != null) {
                return toVO(existingFile);
            }

            String currentName = UUID.randomUUID().toString().replace("-", "");
            if (StringUtils.hasText(extension)) {
                currentName = currentName + "." + extension;
            }
            String objectKey = buildObjectKey(currentName);
            String provider = systemConfigService.getUploadProvider();

            // Pass InputStream directly to storage -- no byte[] intermediate
            StoredFileResult storedFile = switch (provider) {
                case "server" -> storeOnServer(file.getInputStream(), currentName);
                case "minio" -> storeOnMinio(file.getInputStream(), objectKey, file.getContentType(), file.getSize());
                case "aliyun-oss" -> storeOnOss(file.getInputStream(), objectKey, file.getContentType());
                default -> throw new BusinessException("不支持的上传存储方式: " + provider);
            };

            SysUploadFile uploadFile = new SysUploadFile();
            uploadFile.setOriginalName(originalName);
            uploadFile.setCurrentName(currentName);
            uploadFile.setFileSize(file.getSize());
            uploadFile.setFileType(resolveFileType(file, extension));
            uploadFile.setMd5Value(md5Value);
            uploadFile.setFilePath(storedFile.filePath());
            uploadFileMapper.insert(uploadFile);
            return toVO(uploadFile);
        } catch (IOException ex) {
            throw new BusinessException("文件上传失败: " + ex.getMessage());
        }
    }

    @Override
    public ResponseEntity<Resource> getContent(Long id, boolean download) {
        SysUploadFile uploadFile = uploadFileMapper.selectById(id);
        if (uploadFile == null || uploadFile.getDeleted() != null && uploadFile.getDeleted() == 1) {
            throw new BusinessException("文件不存在");
        }
        if (isAccessibleUrl(uploadFile.getFilePath())) {
            return ResponseEntity.status(302).location(URI.create(uploadFile.getFilePath())).build();
        }

        try {
            Path path = Path.of(uploadFile.getFilePath());
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                throw new BusinessException("文件不存在或已被删除");
            }
            Path uploadDir = Path.of(System.getProperty("user.dir"), systemConfigService.getUploadServerBasePath()).normalize();
            if (!path.normalize().startsWith(uploadDir)) {
                throw new BusinessException("非法文件路径");
            }

            Resource resource = new UrlResource(path.toUri());
            String fileName = StringUtils.hasText(uploadFile.getOriginalName()) ? uploadFile.getOriginalName() : uploadFile.getCurrentName();
            MediaType mediaType = resolveMediaType(uploadFile, path);
            ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                    .filename(fileName, StandardCharsets.UTF_8)
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .contentType(mediaType)
                    .contentLength(Files.size(path))
                    .body(resource);
        } catch (IOException ex) {
            throw new BusinessException("读取文件失败: " + ex.getMessage());
        }
    }

    private MinioClient getMinioClient() {
        String endpoint = requiredConfig("upload.minio.endpoint", "MinIO Endpoint");
        String accessKey = requiredConfig("upload.minio.access-key", "MinIO AccessKey");
        String secretKey = requiredConfig("upload.minio.secret-key", "MinIO SecretKey");
        String key = endpoint + "|" + accessKey;

        if (cachedMinioClient != null && key.equals(cachedMinioKey)) {
            return cachedMinioClient;
        }
        synchronized (this) {
            if (cachedMinioClient != null && key.equals(cachedMinioKey)) {
                return cachedMinioClient;
            }
            cachedMinioClient = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
            cachedMinioKey = key;
            return cachedMinioClient;
        }
    }

    private OSS getOssClient() {
        String endpoint = requiredConfig("upload.oss.endpoint", "阿里云 OSS Endpoint");
        String accessKeyId = requiredConfig("upload.oss.access-key-id", "阿里云 OSS AccessKeyId");
        String accessKeySecret = requiredConfig("upload.oss.access-key-secret", "阿里云 OSS AccessKeySecret");
        String key = endpoint + "|" + accessKeyId;

        if (cachedOssClient != null && key.equals(cachedOssKey)) {
            return cachedOssClient;
        }
        synchronized (this) {
            if (cachedOssClient != null && key.equals(cachedOssKey)) {
                return cachedOssClient;
            }
            if (cachedOssClient != null) {
                cachedOssClient.shutdown();
            }
            cachedOssClient = new OSSClientBuilder()
                    .build(normalizeHttpEndpoint(endpoint), accessKeyId, accessKeySecret);
            cachedOssKey = key;
            return cachedOssClient;
        }
    }

    private StoredFileResult storeOnServer(InputStream inputStream, String currentName) {
        Path uploadDir = Path.of(System.getProperty("user.dir"), systemConfigService.getUploadServerBasePath());
        Path targetPath = uploadDir.resolve(currentName);
        try {
            Files.createDirectories(uploadDir);
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            String baseUrl = systemConfigService.getConfigValue("upload.server.base-url");
            if (StringUtils.hasText(baseUrl)) {
                return new StoredFileResult(trimTrailingSlash(normalizeServerBaseUrl(baseUrl)) + "/" + currentName);
            }
            return new StoredFileResult(targetPath.toString());
        } catch (IOException ex) {
            throw new BusinessException("服务器存储上传失败: " + ex.getMessage());
        }
    }

    private StoredFileResult storeOnMinio(InputStream inputStream, String objectKey, String contentType, long size) {
        String endpoint = requiredConfig("upload.minio.endpoint", "MinIO Endpoint");
        String bucket = requiredConfig("upload.minio.bucket", "MinIO Bucket");
        String domain = systemConfigService.getConfigValue("upload.minio.domain");

        try {
            MinioClient client = getMinioClient();
            boolean bucketExists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!bucketExists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(StringUtils.hasText(contentType) ? contentType : "application/octet-stream")
                    .build());
            return new StoredFileResult(buildObjectUrl(domain, endpoint, bucket, objectKey));
        } catch (Exception ex) {
            throw new BusinessException("MinIO 上传失败: " + ex.getMessage());
        }
    }

    private StoredFileResult storeOnOss(InputStream inputStream, String objectKey, String contentType) {
        String endpoint = requiredConfig("upload.oss.endpoint", "阿里云 OSS Endpoint");
        String bucket = requiredConfig("upload.oss.bucket", "阿里云 OSS Bucket");
        String domain = systemConfigService.getConfigValue("upload.oss.domain");

        OSS ossClient = getOssClient();
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            if (StringUtils.hasText(contentType)) {
                metadata.setContentType(contentType);
            }
            PutObjectRequest request = new PutObjectRequest(bucket, objectKey, inputStream, metadata);
            ossClient.putObject(request);
            return new StoredFileResult(buildObjectUrl(domain, endpoint, bucket, objectKey));
        } catch (Exception ex) {
            throw new BusinessException("阿里云 OSS 上传失败: " + ex.getMessage());
        }
    }

    private String requiredConfig(String key, String label) {
        String value = systemConfigService.getConfigValue(key);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(label + " 未配置");
        }
        return value.trim();
    }

    private String buildObjectKey(String currentName) {
        LocalDate today = LocalDate.now();
        return today.getYear() + "/" + today.getMonthValue() + "/" + today.getDayOfMonth() + "/" + currentName;
    }

    private String buildObjectUrl(String domain, String endpoint, String bucket, String objectKey) {
        if (StringUtils.hasText(domain)) {
            return trimTrailingSlash(normalizeHttpEndpoint(domain)) + "/" + objectKey;
        }
        return trimTrailingSlash(normalizeHttpEndpoint(endpoint)) + "/" + bucket + "/" + objectKey;
    }

    private MediaType resolveMediaType(SysUploadFile uploadFile, Path path) throws IOException {
        if (StringUtils.hasText(uploadFile.getFileType()) && uploadFile.getFileType().contains("/")) {
            return MediaType.parseMediaType(uploadFile.getFileType());
        }
        String probeType = Files.probeContentType(path);
        if (StringUtils.hasText(probeType)) {
            return MediaType.parseMediaType(probeType);
        }
        return MediaTypeFactory.getMediaType(path.getFileName().toString()).orElse(MediaType.APPLICATION_OCTET_STREAM);
    }

    private boolean isAccessibleUrl(String filePath) {
        return StringUtils.hasText(filePath) && (filePath.startsWith("/") || filePath.startsWith("http://") || filePath.startsWith("https://"));
    }

    private String normalizeHttpEndpoint(String endpoint) {
        String trimmed = endpoint.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    private String normalizeServerBaseUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        if (trimmed.startsWith("/")) {
            return trimmed;
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private SysUploadFile findByMd5(String md5Value) {
        return uploadFileMapper.selectOne(new LambdaQueryWrapper<SysUploadFile>()
                .eq(SysUploadFile::getDeleted, 0)
                .eq(SysUploadFile::getMd5Value, md5Value)
                .last("LIMIT 1"));
    }

    private UploadFileVO toVO(SysUploadFile entity) {
        UploadFileVO vo = new UploadFileVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    private String resolveFileType(MultipartFile file, String extension) {
        if (StringUtils.hasText(extension)) {
            return extension.toLowerCase();
        }
        if (StringUtils.hasText(file.getContentType())) {
            return file.getContentType().toLowerCase();
        }
        return "unknown";
    }

    private String getExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1);
    }

    private record StoredFileResult(String filePath) {
    }
}
