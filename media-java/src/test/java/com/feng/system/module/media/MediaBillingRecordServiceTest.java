package com.feng.system.module.media;

import com.feng.system.module.media.entity.MediaBillingRecord;
import com.feng.system.module.media.entity.MediaTask;
import com.feng.system.module.media.mapper.MediaBillingRecordMapper;
import com.feng.system.module.media.mapper.MediaTaskMapper;
import com.feng.system.module.media.service.MediaBillingRecordService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MediaBillingRecordServiceTest {

    @Test
    void beginPersistsTaskAndBillingRecordTogether() {
        MediaBillingRecordMapper records = mock(MediaBillingRecordMapper.class);
        MediaTaskMapper tasks = mock(MediaTaskMapper.class);
        MediaBillingRecordService service = new MediaBillingRecordService(records, tasks);
        MediaTask task = new MediaTask();
        task.setId("task-1");
        task.setApiKey("plain-key");

        service.begin(task, new BigDecimal("0.50"), true);

        verify(tasks).insert(task);
        ArgumentCaptor<MediaBillingRecord> record = ArgumentCaptor.forClass(MediaBillingRecord.class);
        verify(records).insert(record.capture());
        assertEquals("task-1", record.getValue().getTaskId());
        assertEquals("plain-key", record.getValue().getApiKey());
        assertEquals("PENDING", record.getValue().getDeductionStatus());
        assertEquals(new BigDecimal("0.50"), record.getValue().getTaskFee());
        assertEquals("PENDING", task.getBillingStatus());
        assertSame(task.getBillingAmount(), record.getValue().getTaskFee());
    }

    @Test
    void chargedDoesNotOverwriteAReleasedRecord() {
        MediaBillingRecordMapper records = mock(MediaBillingRecordMapper.class);
        MediaTaskMapper tasks = mock(MediaTaskMapper.class);
        MediaBillingRecord record = new MediaBillingRecord();
        record.setTaskId("task-1");
        record.setDeductionStatus("RELEASED");
        when(records.selectOne(any())).thenReturn(record);

        new MediaBillingRecordService(records, tasks).charged("task-1", "usage-1");

        verify(records, never()).updateById(any());
        verify(tasks, never()).updateById(any());
    }

    @Test
    void billingFailureDoesNotOverwriteGenerationErrorOnTask() {
        MediaBillingRecordMapper records = mock(MediaBillingRecordMapper.class);
        MediaTaskMapper tasks = mock(MediaTaskMapper.class);
        MediaBillingRecord record = new MediaBillingRecord();
        record.setTaskId("task-1");
        record.setTaskFee(new BigDecimal("0.50"));
        when(records.selectOne(any())).thenReturn(record);

        MediaBillingRecordService service = new MediaBillingRecordService(records, tasks);
        service.failed("task-1", "RELEASE_FAILED", "release unavailable");

        ArgumentCaptor<MediaTask> task = ArgumentCaptor.forClass(MediaTask.class);
        verify(tasks).updateById(task.capture());
        assertNull(task.getValue().getErrorMessage());
    }
}
