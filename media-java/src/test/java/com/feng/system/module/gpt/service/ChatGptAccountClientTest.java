package com.feng.system.module.gpt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ChatGptAccountClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void checkSendsApifoxStyleRequestHeaders() {
        String token = jwt();
        AtomicReference<Request> captured = new AtomicReference<>();
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    captured.set(chain.request());
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create("""
                                    {
                                      "accounts": {
                                        "account-1": {
                                          "account": {"account_id":"account-1","plan_type":"free"},
                                          "entitlement": {"subscription_plan":"chatgptfreeplan"}
                                        }
                                      },
                                      "account_ordering":"account-1"
                                    }
                                    """, MediaType.parse("application/json")))
                            .build();
                })
                .build();
        ChatGptAccountClient client = new ChatGptAccountClient(
                okHttpClient, objectMapper, new ChatGptAccountStatusParser(objectMapper));

        ChatGptAccountClient.CheckedAccount checked = client.check(token);

        assertThat(checked.status().accountId()).isEqualTo("account-1");
        assertThat(captured.get().url().toString()).isEqualTo(ChatGptAccountClient.STATUS_URL);
        assertThat(captured.get().method()).isEqualTo("GET");
        assertThat(captured.get().header("Authorization")).isEqualTo("Bearer " + token);
        assertThat(captured.get().header("User-Agent")).isEqualTo("Apifox/1.0.0 (https://apifox.com)");
        assertThat(captured.get().header("Accept")).isEqualTo("*/*");
        assertThat(captured.get().header("Host")).isEqualTo("chatgpt.com");
        assertThat(captured.get().header("Connection")).isEqualTo("keep-alive");
    }

    private String jwt() {
        String header = base64("{\"alg\":\"none\"}");
        long exp = Instant.now().plusSeconds(3600).getEpochSecond();
        String payload = base64("""
                {
                  "exp": %d,
                  "https://api.openai.com/auth": {"user_id":"user-1"},
                  "https://api.openai.com/profile": {"email":"user@example.com","name":"Test User"}
                }
                """.formatted(exp));
        return header + "." + payload + ".signature";
    }

    private String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
