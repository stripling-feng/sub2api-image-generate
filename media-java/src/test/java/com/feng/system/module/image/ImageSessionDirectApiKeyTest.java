package com.feng.system.module.image;

import com.feng.system.module.image.entity.ApiProfile;
import com.feng.system.module.image.mapper.ApiProfileMapper;
import com.feng.system.module.image.mapper.ApiSessionMapper;
import com.feng.system.module.image.service.ImageSessionService;
import com.feng.system.module.image.service.Sub2apiBillingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImageSessionDirectApiKeyTest {

    @Test
    void resolvesAHeaderApiKeyWithoutAnApiProfileRow() {
        ApiProfileMapper profiles = mock(ApiProfileMapper.class);
        ApiSessionMapper sessions = mock(ApiSessionMapper.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        when(billing.validateApiKey("sk-direct-key")).thenReturn(
                new Sub2apiBillingService.BillingAccount("key-1", "user-1", "10", "10"));
        ImageSessionService service = new ImageSessionService(profiles, sessions, billing);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("X-API-Key")).thenReturn("sk-direct-key");

        ApiProfile profile = service.requireProfile(request, response);

        assertNotNull(profile);
        assertEquals("sk-direct-key", profile.getEncryptedKey());
        verify(billing).validateApiKey("sk-direct-key");
        verifyNoInteractions(profiles, sessions);
    }
}
