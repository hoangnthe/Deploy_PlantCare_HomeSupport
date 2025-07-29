package com.plantcare_backend.dto.response.base;

import org.springframework.http.HttpStatus;

/**
 * Create by TaHoang
 */

public class ResponseFailure extends ResponseSuccess {
    public ResponseFailure(HttpStatus status, String message) {
        super(status, message);
    }
}
