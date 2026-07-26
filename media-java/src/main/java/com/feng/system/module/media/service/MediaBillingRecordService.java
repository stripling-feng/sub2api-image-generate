package com.feng.system.module.media.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feng.system.module.image.service.Sub2apiBillingService;
import com.feng.system.module.image.support.ImageTime;
import com.feng.system.module.media.entity.MediaBillingRecord;
import com.feng.system.module.media.entity.MediaTask;
import com.feng.system.module.media.mapper.MediaBillingRecordMapper;
import com.feng.system.module.media.mapper.MediaTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaBillingRecordService {
    private final MediaBillingRecordMapper records;
    private final MediaTaskMapper tasks;

    @Transactional
    public void begin(MediaTask task, BigDecimal fee, boolean required) {
        MediaBillingRecord existing = find(task.getId());
        if (existing != null) {
            updateTask(task.getId(), existing.getTaskFee(), existing.getDeductionStatus(), now());
            return;
        }
        BigDecimal amount = fee == null ? BigDecimal.ZERO : fee;
        String status = required ? "PENDING" : "NOT_REQUIRED";
        LocalDateTime now = now();
        task.setBillingStatus(status);
        task.setBillingAmount(amount);
        task.setUpdatedAt(now);
        tasks.insert(task);

        MediaBillingRecord record = new MediaBillingRecord();
        record.setId(id());
        record.setTaskId(task.getId());
        record.setApiKey(task.getApiKey());
        record.setTaskFee(amount);
        record.setDeductionStatus(status);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        records.insert(record);
    }

    @Transactional
    public void reserved(String taskId, Sub2apiBillingService.Reservation reservation) {
        MediaBillingRecord record = require(taskId);
        if (isTerminal(record.getDeductionStatus()) || "RESERVED".equals(record.getDeductionStatus())) return;
        LocalDateTime now = now();
        record.setDeductionStatus("RESERVED");
        record.setTaskFee(reservation.amountUsd());
        record.setApiKeyId(reservation.apiKeyId());
        record.setUserId(reservation.userId());
        record.setAccountId(reservation.accountId());
        record.setReservedAt(now);
        record.setUpdatedAt(now);
        records.updateById(record);
        updateTask(taskId, reservation.amountUsd(), "RESERVED", now);
    }

    @Transactional
    public void charged(String taskId, String usageLogId) {
        MediaBillingRecord record = require(taskId);
        if (isTerminal(record.getDeductionStatus()) || "NOT_REQUIRED".equals(record.getDeductionStatus())) return;
        LocalDateTime now = now();
        record.setDeductionStatus("CHARGED");
        record.setUsageLogId(usageLogId);
        record.setSettledAt(now);
        record.setErrorMessage(null);
        record.setUpdatedAt(now);
        records.updateById(record);
        updateTask(taskId, record.getTaskFee(), "CHARGED", now);
    }

    @Transactional
    public void released(String taskId) {
        MediaBillingRecord record = require(taskId);
        if (isTerminal(record.getDeductionStatus())) return;
        LocalDateTime now = now();
        record.setDeductionStatus("RELEASED");
        record.setSettledAt(now);
        record.setUpdatedAt(now);
        records.updateById(record);
        updateTask(taskId, record.getTaskFee(), "RELEASED", now);
    }

    @Transactional
    public void failed(String taskId, String status, String message) {
        MediaBillingRecord record = require(taskId);
        if (isTerminal(record.getDeductionStatus())) return;
        LocalDateTime now = now();
        record.setDeductionStatus(status);
        record.setErrorMessage(message);
        record.setSettledAt(now);
        record.setUpdatedAt(now);
        records.updateById(record);
        updateTask(taskId, record.getTaskFee(), status, now);
    }

    public MediaBillingRecord find(String taskId) {
        return records.selectOne(new LambdaQueryWrapper<MediaBillingRecord>()
                .eq(MediaBillingRecord::getTaskId, taskId));
    }

    private MediaBillingRecord require(String taskId) {
        MediaBillingRecord record = find(taskId);
        if (record == null) throw new IllegalStateException("Media billing record is missing for task " + taskId);
        return record;
    }

    private void updateTask(String taskId, BigDecimal amount, String status, LocalDateTime now) {
        MediaTask task = new MediaTask();
        task.setId(taskId);
        task.setBillingAmount(amount);
        task.setBillingStatus(status);
        task.setUpdatedAt(now);
        tasks.updateById(task);
    }

    private boolean isTerminal(String status) {
        return "CHARGED".equals(status) || "RELEASED".equals(status)
                || "RELEASE_FAILED".equals(status) || "CHARGE_FAILED".equals(status)
                || "FAILED".equals(status);
    }

    private static LocalDateTime now() { return ImageTime.now(); }
    private static String id() { return UUID.randomUUID().toString().replace("-", ""); }
}
