package com.feng.system.module.video.service;

import com.feng.system.module.image.exception.ImageApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 视频任务规则工具类:集中定义计费金额计算与各模型的参数校验规则(时长、比例、分辨率、素材数量等)。
 */
public final class VideoTaskRules {
    private VideoTaskRules() {}

    /**
     * 计算单个任务的计费金额:PER_SECOND 模式按 单价×时长,其它模式按固定单价,结果保留 10 位小数。
     * @param mode 计费模式(PER_SECOND 按秒计费,其余按次计费)
     */
    public static BigDecimal charge(String mode, int duration, BigDecimal unitPrice) {
        BigDecimal amount = "PER_SECOND".equals(mode)
                ? unitPrice.multiply(BigDecimal.valueOf(duration)) : unitPrice;
        return amount.setScale(10, RoundingMode.HALF_UP);
    }

    /**
     * 按模型校验生成参数,不满足限制时抛出 422 异常。
     * @param images 参考图片数量(上传 + URL)
     * @param videos 参考视频数量
     * @param audios 参考音频数量
     * @param firstFrame 是否指定首帧
     * @param lastFrame 是否指定尾帧
     */
    public static void validate(String model, int duration, String ratio, String resolution, int images,
                                int videos, int audios, boolean firstFrame, boolean lastFrame) {
        // 通用限制:时长 4~15 秒,分辨率仅支持 480p/720p
        if (duration < 4 || duration > 15 || !List.of("480p", "720p").contains(resolution)) invalid();
        if (model.startsWith("omni-fast")) {
            // omni-fast:固定 10 秒 720p,比例 16:9/9:16,最多 5 张参考图,不支持参考视频/音频,参考图与首尾帧互斥
            if (duration != 10 || !"720p".equals(resolution) || !List.of("16:9", "9:16").contains(ratio)
                    || images > 5 || videos != 0 || audios != 0 || images > 0 && (firstFrame || lastFrame)) invalid();
            return;
        }
        if (model.startsWith("omni-v2v")) {
            // omni-v2v:固定 10 秒 720p,比例 16:9/9:16,最多 2 张参考图与 2 个参考视频,不支持音频与首尾帧
            if (duration != 10 || !"720p".equals(resolution) || !List.of("16:9", "9:16").contains(ratio)
                    || images > 2 || videos > 2 || audios != 0 || firstFrame || lastFrame) invalid();
            return;
        }
        if (model.startsWith("seedance-")) {
            // seedance:比例可选 6 种,最多 4 图/3 视频/1 音频;首尾帧必须成对出现,且指定首尾帧时不能再带其他参考素材
            if (!List.of("16:9", "9:16", "1:1", "21:9", "3:4", "4:3").contains(ratio)
                    || images > 4 || videos > 3 || audios > 1 || firstFrame != lastFrame
                    || firstFrame && images + videos + audios > 0) invalid();
            return;
        }
        if ("grok-video-1.5".equals(model)) {
            // grok-video-1.5:时长限定 4/6/8/10/12/15,比例 16:9/9:16,必须且只能带 1 张图,不支持其他素材与首尾帧
            if (!List.of(4, 6, 8, 10, 12, 15).contains(duration)
                    || !List.of("16:9", "9:16").contains(ratio)
                    || images != 1 || videos != 0 || audios != 0 || firstFrame || lastFrame) invalid();
            return;
        }
        if ("grok-video".equals(model)) {
            // grok-video:时长限定 4/6/8/10/12/15,比例可选 7 种,最多 7 图/1 视频;多图时时长不能超过 10 秒
            if (!List.of(4, 6, 8, 10, 12, 15).contains(duration)
                    || !List.of("1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "2:3").contains(ratio)
                    || images > 7 || videos > 1 || audios != 0 || firstFrame || lastFrame
                    || images > 1 && duration > 10) invalid();
            return;
        }
        // 未匹配到任何已知模型
        invalid();
    }

    private static void invalid() { throw new ImageApiException(422, "Invalid video generation parameters."); }
}
