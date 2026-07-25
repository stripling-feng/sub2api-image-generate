package com.feng.system.common.submit;

import com.feng.system.common.exception.BusinessException;

public class DuplicateSubmitException extends BusinessException {

    public DuplicateSubmitException(String message) {
        super(message);
    }
}
