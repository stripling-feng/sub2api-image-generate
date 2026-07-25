package com.feng.system.module.system.enums;

import lombok.Getter;

@Getter
public enum MenuTypeEnum {
    DIRECTORY(0),
    MENU(1),
    BUTTON(2);

    private final int code;

    MenuTypeEnum(int code) {
        this.code = code;
    }
}
