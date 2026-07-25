package com.feng.system.module.system.dto;

import lombok.Data;

import java.util.List;

@Data
public class ResetPasswordBatchDTO {

    private List<Long> ids;

    private String password;
}
