package com.feng.system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan({
        "com.feng.system.module.system.mapper",
        "com.feng.system.common.log.mapper",
        "com.feng.system.module.tool.mapper"
        ,"com.feng.system.module.image.mapper",
        "com.feng.system.module.video.mapper"
})
@SpringBootApplication
@EnableScheduling
public class FengAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(FengAdminApplication.class, args);
    }
}
