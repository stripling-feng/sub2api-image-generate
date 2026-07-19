package com.feng.system.module.image;

import com.feng.system.module.image.entity.GeneratedImage;
import com.feng.system.module.image.mapper.GeneratedImageMapper;
import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageStorageService {
    private final GeneratedImageMapper imageMapper;
    private final ImageGateway gateway;
    @Value("${image.upload-dir:./uploads}") private String uploadDir;

    public GeneratedImage saveBase64(String jobId, int index, String value) {
        String raw = value.contains(",") ? value.substring(value.indexOf(',') + 1) : value;
        return save(jobId, index, Base64.getDecoder().decode(raw), null);
    }

    public GeneratedImage saveUrl(String jobId, int index, String url) {
        ImageGateway.Download download = gateway.download(url);
        return save(jobId, index, download.bytes(), download.mimeType());
    }

    public GeneratedImage save(String jobId, int index, byte[] bytes, String declaredMime) {
        if (bytes.length == 0) throw new ImageApiException(502, "Generated image is empty.");
        String mime = mime(bytes, declaredMime);
        String extension = extension(mime);
        String date = LocalDate.now(ZoneOffset.UTC).toString();
        String name = "tcboys.de_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + extension;
        Path dir = Path.of(uploadDir).toAbsolutePath().normalize().resolve("images").resolve(date);
        Path file = dir.resolve(name);
        try { Files.createDirectories(dir); Files.write(file, bytes); }
        catch (Exception e) { throw new ImageApiException(500, "Unable to persist generated image."); }
        int[] dimensions = dimensions(bytes);
        GeneratedImage image = new GeneratedImage();
        image.setId(id()); image.setJobId(jobId); image.setFilePath(file.toString()); image.setPublicUrl("/img/" + date + "/" + name);
        image.setMimeType(mime); image.setWidth(dimensions[0] == 0 ? null : dimensions[0]); image.setHeight(dimensions[1] == 0 ? null : dimensions[1]);
        image.setSizeBytes(bytes.length); image.setSourceIndex(index); image.setCreatedAt(ImageTime.now());
        imageMapper.insert(image); return image;
    }

    public boolean exists(String jobId, int index) {
        return imageMapper.selectCount(new LambdaQueryWrapper<GeneratedImage>()
                .eq(GeneratedImage::getJobId, jobId).eq(GeneratedImage::getSourceIndex, index)) > 0;
    }

    private int[] dimensions(byte[] bytes) {
        try { BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes)); return image == null ? new int[2] : new int[]{image.getWidth(), image.getHeight()}; }
        catch (Exception e) { return new int[2]; }
    }
    private String mime(byte[] bytes, String declared) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P') return "image/png";
        if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8) return "image/jpeg";
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I') return "image/gif";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[8] == 'W') return "image/webp";
        if (declared != null && declared.toLowerCase(Locale.ROOT).startsWith("image/")) return declared.split(";")[0];
        throw new ImageApiException(502, "Unsupported generated image format.");
    }
    private String extension(String mime) { return switch (mime) { case "image/jpeg" -> "jpg"; case "image/webp" -> "webp"; case "image/gif" -> "gif"; default -> "png"; }; }
    private static String id() { return UUID.randomUUID().toString().replace("-", ""); }
}
