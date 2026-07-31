package com.feng.system.module.image.service;

import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.support.SafeUpstreamUrl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.common.api.PageResult;
import com.feng.system.common.exception.BusinessException;
import com.feng.system.module.image.entity.AiModel;
import com.feng.system.module.image.entity.ModelProvider;
import com.feng.system.module.image.mapper.AiModelMapper;
import com.feng.system.module.image.mapper.ModelProviderMapper;
import com.feng.system.module.media.entity.MediaTask;
import com.feng.system.module.media.mapper.MediaTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 模型配置服务：管理图片/视频模型及其服务商的增删改查，
 * 对外提供启用中模型的公开列表，并在生成链路中校验加载可用的模型与服务商配置。
 */
@Service
@RequiredArgsConstructor
public class ImageModelConfigService {
    private final ModelProviderMapper providers;
    private final AiModelMapper models;
    private final MediaTaskMapper tasks;
    private final ObjectMapper json;

    /**
     * 分页查询服务商列表。
     *
     * @param name 名称模糊搜索关键字，可为空
     */
    public PageResult<ModelProvider> providerPage(String name, long pageNum, long pageSize) {
        Page<ModelProvider> page = providers.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<ModelProvider>()
                .eq(ModelProvider::getDeleted, 0)
                .like(name != null && !name.isBlank(), ModelProvider::getName, name)
                .orderByAsc(ModelProvider::getProviderSort).orderByDesc(ModelProvider::getId));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    /**
     * 查询全部未删除的服务商，供下拉选择使用。
     */
    public List<ModelProvider> providerOptions() {
        return providers.selectList(new LambdaQueryWrapper<ModelProvider>().eq(ModelProvider::getDeleted, 0)
                .orderByAsc(ModelProvider::getProviderSort).orderByAsc(ModelProvider::getId));
    }

    /**
     * 新增服务商（校验名称与公网 HTTPS 地址后落库）。
     */
    public void saveProvider(ModelProvider provider) {
        validateProvider(provider);
        provider.setId(null);
        providers.insert(provider);
    }

    /**
     * 更新指定服务商。
     */
    public void updateProvider(Long id, ModelProvider provider) {
        validateProvider(provider);
        provider.setId(id);
        providers.updateById(provider);
    }

    /**
     * 删除服务商；仍有关联模型时拒绝删除。
     */
    public void deleteProvider(Long id) {
        if (models.selectCount(new LambdaQueryWrapper<AiModel>().eq(AiModel::getProviderId, id).eq(AiModel::getDeleted, 0)) > 0)
            throw new BusinessException("该服务商仍有关联模型，不能删除");
        providers.deleteById(id);
    }

    /**
     * 分页查询图片模型列表。
     *
     * @param name 按模型 key 或显示名模糊搜索，可为空
     */
    public PageResult<AiModel> imagePage(String name, long pageNum, long pageSize) {
        Page<AiModel> page = models.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getDeleted, 0).eq(AiModel::getModelType, "IMAGE")
                .and(name != null && !name.isBlank(), q -> q.like(AiModel::getModelKey, name).or().like(AiModel::getDisplayName, name))
                .orderByAsc(AiModel::getModelSort).orderByAsc(AiModel::getId));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    /**
     * 新增图片模型：参数 schema、默认参数等内部字段统一由系统默认值填充，不接受外部传入。
     */
    public void saveImage(AiModel model) {
        applyInternalDefaults(model);
        validateModel(model);
        model.setId(null);
        model.setModelType("IMAGE");
        models.insert(model);
    }

    /**
     * 更新图片模型；schema、默认参数、数量上限等内部字段保持原值，防止被前端覆盖。
     */
    public void updateImage(Long id, AiModel model) {
        AiModel existing = models.selectById(id);
        if (existing == null || !"IMAGE".equals(existing.getModelType())) throw new BusinessException("图片模型不存在");
        // 内部受控字段以库中现有值为准
        model.setParameterSchema(existing.getParameterSchema());
        model.setDefaultParams(existing.getDefaultParams());
        model.setMaxCount(existing.getMaxCount());
        model.setMaxReferenceImages(existing.getMaxReferenceImages());
        validateModel(model);
        model.setId(id);
        model.setModelType("IMAGE");
        models.updateById(model);
    }

    /**
     * 删除图片模型；仍有进行中（PENDING）任务时拒绝删除。
     */
    public void deleteImage(Long id) {
        if (tasks.selectCount(new LambdaQueryWrapper<MediaTask>()
                .eq(MediaTask::getModelConfigId, id).eq(MediaTask::getStatus, "PENDING")) > 0)
            throw new BusinessException("该模型仍有进行中的任务，不能删除");
        models.deleteById(id);
    }

    /**
     * 分页查询视频模型列表。
     *
     * @param name 按模型 key 或显示名模糊搜索，可为空
     */
    public PageResult<AiModel> videoPage(String name, long pageNum, long pageSize) {
        Page<AiModel> page = models.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getDeleted, 0).eq(AiModel::getModelType, "VIDEO")
                .and(name != null && !name.isBlank(), q -> q.like(AiModel::getModelKey, name).or().like(AiModel::getDisplayName, name))
                .orderByAsc(AiModel::getModelSort).orderByAsc(AiModel::getId));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    /**
     * 新增视频模型：根据模型 key 套用内置模板（seedance/grok/omni 等）生成参数配置。
     */
    public void saveVideo(AiModel model) {
        applyVideoTemplate(model);
        validateModel(model);
        model.setId(null);
        model.setModelType("VIDEO");
        models.insert(model);
    }

    /**
     * 更新视频模型；模型 key 及模板派生字段不可修改，保持库中原值。
     */
    public void updateVideo(Long id, AiModel model) {
        AiModel existing = models.selectById(id);
        if (existing == null || !"VIDEO".equals(existing.getModelType())) throw new BusinessException("视频模型不存在");
        // 内部受控字段以库中现有值为准
        model.setModelKey(existing.getModelKey());
        model.setParameterSchema(existing.getParameterSchema());
        model.setDefaultParams(existing.getDefaultParams());
        model.setMaxCount(existing.getMaxCount());
        model.setMaxReferenceImages(existing.getMaxReferenceImages());
        model.setSupportsMask(0);
        validateModel(model);
        model.setId(id);
        model.setModelType("VIDEO");
        models.updateById(model);
    }

    /**
     * 删除视频模型。
     */
    public void deleteVideo(Long id) {
        AiModel existing = models.selectById(id);
        if (existing == null || !"VIDEO".equals(existing.getModelType())) throw new BusinessException("视频模型不存在");
        models.deleteById(id);
    }

    /**
     * 返回对外公开的已启用图片模型列表（含参数 schema 与默认值，不含密钥等敏感信息）。
     */
    public List<Map<String, Object>> publicImages() {
        return publicModels("IMAGE");
    }

    /**
     * 返回对外公开的已启用视频模型列表。
     */
    public List<Map<String, Object>> publicVideos() {
        return publicModels("VIDEO");
    }

    private List<Map<String, Object>> publicModels(String type) {
        List<AiModel> list = models.selectList(new LambdaQueryWrapper<AiModel>().eq(AiModel::getDeleted, 0)
                .eq(AiModel::getModelType, type).eq(AiModel::getEnabled, 1)
                .orderByAsc(AiModel::getModelSort).orderByAsc(AiModel::getId));
        if (list.isEmpty()) return List.of();
        Map<Long, ModelProvider> providerMap = new HashMap<>();
        providers.selectBatchIds(list.stream().map(AiModel::getProviderId).filter(Objects::nonNull).distinct().toList())
                .forEach(provider -> providerMap.put(provider.getId(), provider));
        return list.stream().map(model -> publicModel(model, providerMap.get(model.getProviderId())))
                .filter(Objects::nonNull).toList();
    }

    /**
     * 按模型 key 加载可用的图片模型及其服务商配置；模型未启用或服务商配置不完整时抛出业务异常。
     */
    public RuntimeModel requireImage(String modelKey) {
        AiModel model = models.selectOne(new LambdaQueryWrapper<AiModel>().eq(AiModel::getModelKey, modelKey)
                .eq(AiModel::getModelType, "IMAGE").eq(AiModel::getEnabled, 1).eq(AiModel::getDeleted, 0));
        if (model == null) throw new ImageApiException(422, "Unknown or disabled image model.");
        ModelProvider provider = providers.selectById(model.getProviderId());
        if (provider == null || provider.getDeleted() != null && provider.getDeleted() != 0 || !Integer.valueOf(1).equals(provider.getEnabled())
                || provider.getImageApiKey() == null || provider.getImageApiKey().isBlank())
            throw new ImageApiException(503, "Image model provider is not configured.");
        SafeUpstreamUrl.requirePublicHttps(provider.getBaseUrl());
        return new RuntimeModel(model, provider);
    }

    /**
     * 按模型配置 ID 加载图片模型运行时配置（用于轮询等已知任务归属的场景，不校验启用状态）。
     */
    public RuntimeModel requireImage(Long id) {
        AiModel model = models.selectById(id);
        if (model == null || !"IMAGE".equals(model.getModelType())) throw new ImageApiException(503, "Image model configuration is missing.");
        ModelProvider provider = providers.selectById(model.getProviderId());
        if (provider == null || provider.getImageApiKey() == null || provider.getImageApiKey().isBlank())
            throw new ImageApiException(503, "Image model provider is not configured.");
        SafeUpstreamUrl.requirePublicHttps(provider.getBaseUrl());
        return new RuntimeModel(model, provider);
    }

    /**
     * 按模型 key 加载可用的视频模型及其服务商配置。
     */
    public RuntimeModel requireVideo(String modelKey) {
        AiModel model = models.selectOne(new LambdaQueryWrapper<AiModel>().eq(AiModel::getModelKey, modelKey)
                .eq(AiModel::getModelType, "VIDEO").eq(AiModel::getEnabled, 1).eq(AiModel::getDeleted, 0));
        if (model == null) throw new ImageApiException(422, "Unknown or disabled video model.");
        return requireVideoModel(model);
    }

    /**
     * 按模型配置 ID 加载视频模型运行时配置。
     */
    public RuntimeModel requireVideo(Long id) {
        AiModel model = models.selectById(id);
        if (model == null || !"VIDEO".equals(model.getModelType()))
            throw new ImageApiException(503, "Video model configuration is missing.");
        return requireVideoModel(model);
    }

    private RuntimeModel requireVideoModel(AiModel model) {
        ModelProvider provider = providers.selectById(model.getProviderId());
        if (provider == null || !Integer.valueOf(1).equals(provider.getEnabled())
                || provider.getVideoApiKey() == null || provider.getVideoApiKey().isBlank())
            throw new ImageApiException(503, "Video model provider is not configured.");
        SafeUpstreamUrl.requirePublicHttps(provider.getBaseUrl());
        return new RuntimeModel(model, provider);
    }

    private Map<String, Object> publicModel(AiModel model) {
        return publicModel(model, providers.selectById(model.getProviderId()));
    }

    private Map<String, Object> publicModel(AiModel model, ModelProvider provider) {
        if (provider == null || !Integer.valueOf(1).equals(provider.getEnabled())) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", model.getId()); result.put("model", model.getModelKey()); result.put("name", model.getDisplayName());
        result.put("provider", provider.getName()); result.put("maxCount", model.getMaxCount());
        result.put("maxReferenceImages", model.getMaxReferenceImages()); result.put("supportsMask", model.getSupportsMask() == 1);
        result.put("unitPriceUsd", model.getUnitPriceUsd());
        result.put("billingMode", model.getBillingMode() == null ? "PER_REQUEST" : model.getBillingMode());
        result.put("parameters", read(model.getParameterSchema(), new TypeReference<List<Map<String, Object>>>() {}));
        result.put("defaults", read(model.getDefaultParams(), new TypeReference<Map<String, Object>>() {}));
        return result;
    }

    private void validateProvider(ModelProvider provider) {
        if (provider.getName() == null || provider.getName().isBlank()) throw new BusinessException("服务商名称不能为空");
        SafeUpstreamUrl.requirePublicHttps(provider.getBaseUrl());
        if (provider.getEnabled() == null) provider.setEnabled(1);
        if (provider.getProviderSort() == null) provider.setProviderSort(0);
        if (provider.getImageApiKey() == null) provider.setImageApiKey("");
        if (provider.getVideoApiKey() == null) provider.setVideoApiKey("");
    }

    private void validateModel(AiModel model) {
        if (model.getProviderId() == null || providers.selectById(model.getProviderId()) == null) throw new BusinessException("请选择有效的模型服务商");
        if (model.getModelKey() == null || model.getModelKey().isBlank() || model.getDisplayName() == null || model.getDisplayName().isBlank())
            throw new BusinessException("模型名称不能为空");
        if (model.getUpstreamModel() == null || model.getUpstreamModel().isBlank()) model.setUpstreamModel(model.getModelKey());
        if (model.getGenerationPath() == null || model.getGenerationPath().isBlank()) model.setGenerationPath("/v1/images/generations");
        if (model.getUnitPriceUsd() == null || model.getUnitPriceUsd().signum() < 0) throw new BusinessException("单张价格必须大于或等于 0");
        if (model.getBillingMode() == null) model.setBillingMode("PER_REQUEST");
        if (!List.of("PER_REQUEST", "PER_SECOND").contains(model.getBillingMode())) throw new BusinessException("不支持的计费方式");
        validateJson(model.getParameterSchema(), true); validateJson(model.getDefaultParams(), false);
        if (model.getAsyncMode() == null) model.setAsyncMode(1);
        if (model.getMaxCount() == null) model.setMaxCount(1);
        if (model.getMaxReferenceImages() == null) model.setMaxReferenceImages(0);
        if (model.getSupportsMask() == null) model.setSupportsMask(0);
        if (model.getEnabled() == null) model.setEnabled(1);
        if (model.getModelSort() == null) model.setModelSort(0);
    }

    // 新建图片模型时强制使用系统内置的 schema/默认参数/上限，屏蔽外部传入值
    private void applyInternalDefaults(AiModel model) {
        model.setParameterSchema("[]");
        model.setDefaultParams("{}");
        model.setMaxCount(10);
        model.setMaxReferenceImages(9);
        model.setBillingMode("PER_REQUEST");
    }

    // 按模型 key 匹配内置视频模板：设置生成路径、参考素材上限、参数 schema 与协议默认值；未匹配到模板则拒绝创建
    private void applyVideoTemplate(AiModel model) {
        model.setMaxCount(4);
        model.setSupportsMask(0);
        model.setBillingMode(model.getBillingMode() == null ? "PER_REQUEST" : model.getBillingMode());
        String seedanceParams = """
                [{"key":"duration","label":"视频时长","type":"select","default":8,"options":[4,5,6,7,8,9,10,11,12,13,14,15]},
                 {"key":"aspectRatio","label":"画面比例","type":"select","default":"16:9","options":["16:9","9:16","1:1","21:9","3:4","4:3"]},
                 {"key":"resolution","label":"清晰度","type":"select","default":"720p","options":["480p","720p"]},
                 {"key":"generateAudio","label":"生成原生音频","type":"boolean","default":true}]
                """;
        String grokParams = """
                [{"key":"duration","label":"视频时长","type":"select","default":6,"options":[4,6,8,10,12,15]},
                 {"key":"aspectRatio","label":"画面比例","type":"select","default":"16:9","options":["1:1","16:9","9:16","4:3","3:4","3:2","2:3"]},
                 {"key":"resolution","label":"清晰度","type":"select","default":"720p","options":["480p","720p"]}]
                """;
        String omniParams = """
                [{"key":"duration","label":"视频时长","type":"select","default":10,"options":[{"label":"10秒左右","value":10}]},
                 {"key":"aspectRatio","label":"画面比例","type":"select","default":"16:9","options":["16:9","9:16"]},
                 {"key":"resolution","label":"清晰度","type":"select","default":"720p","options":["720p"]}]
                """;
        switch (model.getModelKey() == null ? "" : model.getModelKey()) {
            case "seedance-2.0", "seedance-2.0-fast", "seedance-2.0-mini" -> {
                model.setGenerationPath("/v1/videos"); model.setMaxReferenceImages(4);
                model.setParameterSchema(seedanceParams);
                model.setDefaultParams("{\"protocol\":\"seedance\",\"images\":4,\"videos\":3,\"audios\":1,\"frameInputs\":true}");
            }
            case "grok-video" -> {
                model.setGenerationPath("/v1/videos"); model.setMaxReferenceImages(7);
                model.setParameterSchema(grokParams);
                model.setDefaultParams("{\"protocol\":\"grok\",\"images\":7,\"videos\":1,\"audios\":0,\"frameInputs\":false}");
            }
            case "grok-video-1.5" -> {
                model.setGenerationPath("/v1/videos"); model.setMaxReferenceImages(1);
                model.setUpstreamModel("grok-video-1.5");
                model.setParameterSchema(grokParams.replace("[\"1:1\",\"16:9\",\"9:16\",\"4:3\",\"3:4\",\"3:2\",\"2:3\"]", "[\"16:9\",\"9:16\"]"));
                model.setDefaultParams("{\"protocol\":\"grok\",\"images\":1,\"videos\":0,\"audios\":0,\"frameInputs\":false,\"requiresImage\":true}");
            }
            case "omni-fast", "omni-fast-no-water" -> {
                model.setGenerationPath("/v1/videos"); model.setMaxReferenceImages(5);
                model.setParameterSchema(omniParams);
                model.setDefaultParams("{\"protocol\":\"omni-fast\",\"images\":5,\"videos\":0,\"audios\":0,\"frameInputs\":true,\"maxImageBytes\":5242880}");
            }
            case "omni-v2v", "omni-v2v-no-water" -> {
                model.setGenerationPath("/v1/videos"); model.setMaxReferenceImages(2);
                model.setParameterSchema(omniParams);
                model.setDefaultParams("{\"protocol\":\"omni-v2v\",\"images\":2,\"videos\":2,\"audios\":0,\"frameInputs\":false,\"maxImageBytes\":8388608,\"maxVideoBytes\":8388608}");
            }
            default -> throw new BusinessException("请选择支持的视频模型模板");
        }
    }

    private void validateJson(String value, boolean list) {
        try {
            if (value == null || value.isBlank()) throw new Exception();
            if (list) json.readValue(value, new TypeReference<List<Map<String, Object>>>() {});
            else json.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) { throw new BusinessException(list ? "参数配置必须是 JSON 数组" : "默认参数必须是 JSON 对象"); }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try { return json.readValue(value, type); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** 生成链路使用的运行时配置：模型定义及其所属服务商。 */
    public record RuntimeModel(AiModel model, ModelProvider provider) {}
}
