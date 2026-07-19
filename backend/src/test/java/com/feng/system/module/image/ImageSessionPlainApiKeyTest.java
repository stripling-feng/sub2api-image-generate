package com.feng.system.module.image;

import com.feng.system.module.image.entity.ApiProfile;
import com.feng.system.module.image.mapper.ApiProfileMapper;
import com.feng.system.module.image.mapper.ApiSessionMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageSessionPlainApiKeyTest {

    @Test
    void storesBoundApiKeyAsPlainText() {
        ApiProfileMapper profiles = mock(ApiProfileMapper.class);
        ApiSessionMapper sessions = mock(ApiSessionMapper.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        when(billing.validateApiKey("sk-plain-text")).thenReturn(
                new Sub2apiBillingService.BillingAccount("key", "user", "10", "10"));
        when(profiles.selectOne(any())).thenReturn(null);
        when(profiles.selectById(any())).thenReturn(null);

        ImageSessionService service = new ImageSessionService(profiles, sessions, billing);
        service.bind("https://example.com", "sk-plain-text", mock(HttpServletResponse.class));

        ArgumentCaptor<ApiProfile> saved = ArgumentCaptor.forClass(ApiProfile.class);
        verify(profiles).insert(saved.capture());
        assertEquals("sk-plain-text", saved.getValue().getEncryptedKey());
    }
}
