package com.feng.system.module.gpt.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GptTokenStoreTest {
    @Test
    void returnsPlaintextWithoutTransformingIt() {
        GptTokenStore store = new GptTokenStore();

        assertThat(store.read("plain-access-token")).isEqualTo("plain-access-token");
        assertThat(store.hash("plain-access-token")).hasSize(64);
    }
}
