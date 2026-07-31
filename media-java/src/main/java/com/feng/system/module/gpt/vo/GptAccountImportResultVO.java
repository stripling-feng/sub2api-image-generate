package com.feng.system.module.gpt.vo;

import java.util.List;

public record GptAccountImportResultVO(int total, int succeeded, int failed, List<Item> items) {
    public record Item(boolean success, String email, String message, GptAccountVO account) {
    }
}
