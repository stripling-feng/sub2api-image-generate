package com.feng.system.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void expiredTokenReturnsUnauthorizedResponse() throws Exception {
        mvc.perform(get("/expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录或登录已过期"));
    }

    @Test
    void missingPermissionReturnsForbiddenResponse() throws Exception {
        mvc.perform(get("/missing-permission"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无访问权限"));
    }

    @RestController
    static class TestController {
        @GetMapping("/expired-token")
        void expiredToken() {
            throw new NotLoginException("token 已过期", "login", NotLoginException.TOKEN_TIMEOUT);
        }

        @GetMapping("/missing-permission")
        void missingPermission() {
            throw new NotPermissionException("system:user:list", "login");
        }
    }
}
