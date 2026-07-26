package com.feng.system.module.image.dto;

/**
 * 参考图上传成功的响应。
 *
 * @param url       文件的内部访问地址
 * @param publicUrl 文件的对外公开地址(默认与 url 相同)
 * @param mimeType  文件的 MIME 类型
 * @param sizeBytes 文件大小(字节)
 */
public record UploadResponse(String url, String publicUrl, String mimeType, long sizeBytes) {
    /**
     * 便捷构造:未区分内外部地址时,publicUrl 复用 url。
     */
    public UploadResponse(String url, String mimeType, long sizeBytes) {
        this(url, url, mimeType, sizeBytes);
    }
}
