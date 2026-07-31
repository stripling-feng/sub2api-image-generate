package com.feng.system.module.gpt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feng.system.common.api.PageResult;
import com.feng.system.common.exception.BusinessException;
import com.feng.system.module.gpt.dto.GptAccountQueryDTO;
import com.feng.system.module.gpt.entity.GptAccount;
import com.feng.system.module.gpt.mapper.GptAccountMapper;
import com.feng.system.module.gpt.vo.GptAccountImportResultVO;
import com.feng.system.module.gpt.vo.GptAccountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class GptAccountService {
    private final GptAccountMapper mapper;
    private final ChatGptAccountClient client;
    private final GptTokenStore tokenStore;
    private final Object saveLock = new Object();

    @Value("${gpt.account.parallelism:5}")
    private int parallelism = 5;

    public PageResult<GptAccountVO> page(GptAccountQueryDTO query) {
        long pageNum = Math.max(query.getPageNum(), 1);
        long pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        LambdaQueryWrapper<GptAccount> wrapper = new LambdaQueryWrapper<GptAccount>()
                .eq(GptAccount::getDeleted, 0)
                .and(StringUtils.hasText(query.getKeyword()), condition -> condition
                        .like(GptAccount::getEmail, query.getKeyword())
                        .or().like(GptAccount::getAccountId, query.getKeyword())
                        .or().like(GptAccount::getDisplayName, query.getKeyword()))
                .eq(StringUtils.hasText(query.getPlanType()), GptAccount::getPlanType, query.getPlanType())
                .eq(StringUtils.hasText(query.getAccountStatus()), GptAccount::getAccountStatus, query.getAccountStatus())
                .eq(StringUtils.hasText(query.getPlusEligibility()), GptAccount::getPlusEligibility, query.getPlusEligibility())
                .eq(query.getUsed() != null, GptAccount::getUsed, query.getUsed())
                .orderByDesc(GptAccount::getLastCheckedAt)
                .orderByDesc(GptAccount::getId);
        Page<GptAccount> page = mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(this::toVO).toList());
    }

    public GptAccountImportResultVO importTokens(List<String> tokens) {
        List<GptAccountImportResultVO.Item> items = runParallel(tokens.stream()
                .map(rawToken -> (Supplier<GptAccountImportResultVO.Item>) () -> importOne(rawToken))
                .toList());
        int succeeded = (int) items.stream().filter(GptAccountImportResultVO.Item::success).count();
        return new GptAccountImportResultVO(items.size(), succeeded, items.size() - succeeded, items);
    }

    private GptAccountImportResultVO.Item importOne(String rawToken) {
        try {
            GptAccount account = saveCheckedSafely(client.check(rawToken));
            return new GptAccountImportResultVO.Item(true, account.getEmail(), "导入成功", toVO(account));
        } catch (BusinessException ex) {
            return new GptAccountImportResultVO.Item(false, "", ex.getMessage(), null);
        }
    }

    public GptAccountVO refresh(Long id) {
        GptAccount account = requireAccount(id);
        try {
            return toVO(saveCheckedSafely(client.check(tokenStore.read(account.getAccessToken()))));
        } catch (BusinessException ex) {
            account.setAccountStatus(isExpired(account) ? "EXPIRED" : "ERROR");
            account.setLastError(ex.getMessage());
            account.setLastCheckedAt(LocalDateTime.now());
            mapper.updateById(account);
            return toVO(account);
        }
    }

    public List<GptAccountVO> refreshBatch(List<Long> ids) {
        return runParallel(ids.stream().distinct()
                .map(id -> (Supplier<GptAccountVO>) () -> refresh(id))
                .toList());
    }

    public void delete(Long id) {
        requireAccount(id);
        mapper.deleteById(id);
    }

    public int deleteBatch(List<Long> ids) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        for (Long id : distinctIds) {
            requireAccount(id);
        }
        for (Long id : distinctIds) {
            mapper.deleteById(id);
        }
        return distinctIds.size();
    }

    public GptAccountVO updateUsed(Long id, Boolean used) {
        GptAccount account = requireAccount(id);
        account.setUsed(Boolean.TRUE.equals(used));
        mapper.updateById(account);
        return toVO(account);
    }

    private GptAccount saveCheckedSafely(ChatGptAccountClient.CheckedAccount checked) {
        synchronized (saveLock) {
            return saveChecked(checked);
        }
    }

    private GptAccount saveChecked(ChatGptAccountClient.CheckedAccount checked) {
        ChatGptAccountStatusParser.ParsedAccount status = checked.status();
        String tokenHash = tokenStore.hash(checked.token());
        GptAccount account = mapper.selectOne(new LambdaQueryWrapper<GptAccount>()
                .eq(GptAccount::getDeleted, 0)
                .and(condition -> condition.eq(GptAccount::getAccountId, status.accountId())
                        .or().eq(GptAccount::getTokenHash, tokenHash))
                .last("limit 1"));
        if (account == null) account = new GptAccount();
        account.setAccountId(status.accountId());
        account.setUserId(status.userId());
        account.setEmail(status.email());
        account.setDisplayName(status.displayName());
        account.setAccessToken(checked.token());
        account.setTokenHash(tokenHash);
        account.setTokenExpiresAt(status.tokenExpiresAt());
        account.setPlanType(status.planType());
        account.setSubscriptionPlan(status.subscriptionPlan());
        account.setActiveSubscription(status.activeSubscription());
        account.setActiveSubscriptionGratis(status.activeSubscriptionGratis());
        if (account.getUsed() == null) account.setUsed(false);
        account.setAccountStatus(status.accountStatus());
        account.setPlusEligibility(status.plusEligibility());
        account.setEligibilityReason(status.eligibilityReason());
        account.setEligibleOffers(status.eligibleOffers());
        account.setEligiblePromoCampaigns(status.eligiblePromoCampaigns());
        account.setRawResponse(status.rawResponse());
        account.setLastCheckedAt(LocalDateTime.now());
        account.setLastError(null);
        if (account.getId() == null) mapper.insert(account); else mapper.updateById(account);
        return account;
    }

    private <T> List<T> runParallel(List<Supplier<T>> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        int workerCount = Math.min(Math.max(parallelism, 1), tasks.size());
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        try {
            List<CompletableFuture<T>> futures = new ArrayList<>(tasks.size());
            for (Supplier<T> task : tasks) {
                futures.add(CompletableFuture.supplyAsync(task, executor));
            }
            return futures.stream().map(CompletableFuture::join).toList();
        } finally {
            executor.shutdown();
        }
    }

    private GptAccount requireAccount(Long id) {
        GptAccount account = mapper.selectById(id);
        if (account == null || Integer.valueOf(1).equals(account.getDeleted())) {
            throw new BusinessException("GPT 账号不存在");
        }
        return account;
    }

    private boolean isExpired(GptAccount account) {
        return account.getTokenExpiresAt() != null && account.getTokenExpiresAt().isBefore(LocalDateTime.now());
    }

    private GptAccountVO toVO(GptAccount account) {
        GptAccountVO vo = new GptAccountVO();
        vo.setId(account.getId());
        vo.setAccountId(account.getAccountId());
        vo.setEmail(account.getEmail());
        vo.setDisplayName(account.getDisplayName());
        vo.setTokenFingerprint(account.getTokenHash() == null ? "" : account.getTokenHash().substring(0, Math.min(12, account.getTokenHash().length())));
        vo.setTokenExpiresAt(account.getTokenExpiresAt());
        vo.setPlanType(account.getPlanType());
        vo.setSubscriptionPlan(account.getSubscriptionPlan());
        vo.setActiveSubscription(account.getActiveSubscription());
        vo.setActiveSubscriptionGratis(account.getActiveSubscriptionGratis());
        vo.setUsed(Boolean.TRUE.equals(account.getUsed()));
        vo.setAccountStatus(account.getAccountStatus());
        vo.setPlusEligibility(account.getPlusEligibility());
        vo.setEligibilityReason(account.getEligibilityReason());
        vo.setEligibleOffers(account.getEligibleOffers());
        vo.setEligiblePromoCampaigns(account.getEligiblePromoCampaigns());
        vo.setLastCheckedAt(account.getLastCheckedAt());
        vo.setLastError(account.getLastError());
        vo.setCreateTime(account.getCreateTime());
        return vo;
    }
}
