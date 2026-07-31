package com.feng.system.config;

import com.feng.system.FengAdminApplication;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchemaGuardTest {

    @Test
    void requiresCurrentMigrationVersion() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(19, 20);
        SchemaGuard guard = new SchemaGuard(jdbc);

        assertThrows(IllegalStateException.class, guard::verify);
        assertDoesNotThrow(guard::verify);
    }

    @Test
    void applicationScansVideoMappers() {
        MapperScan scan = FengAdminApplication.class.getAnnotation(MapperScan.class);
        assertDoesNotThrow(() -> java.util.Arrays.stream(scan.value())
                .filter("com.feng.system.module.video.mapper"::equals).findFirst().orElseThrow());
    }

    @Test
    void applicationScansSharedMediaMappers() {
        MapperScan scan = FengAdminApplication.class.getAnnotation(MapperScan.class);
        assertDoesNotThrow(() -> java.util.Arrays.stream(scan.value())
                .filter("com.feng.system.module.media.mapper"::equals).findFirst().orElseThrow());
    }

    @Test
    void applicationScansGptAccountMappers() {
        MapperScan scan = FengAdminApplication.class.getAnnotation(MapperScan.class);
        assertDoesNotThrow(() -> java.util.Arrays.stream(scan.value())
                .filter("com.feng.system.module.gpt.mapper"::equals).findFirst().orElseThrow());
    }
}
