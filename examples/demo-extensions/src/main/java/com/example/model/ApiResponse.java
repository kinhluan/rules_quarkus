package com.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Generic API response wrapper
 */
public class ApiResponse<T> {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private T data;

    @JsonProperty("tier")
    private String tier;

    public ApiResponse() {
    }

    public static <T> ApiResponse<T> success(T data, String tier) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.message = "OK";
        response.data = data;
        response.tier = tier;
        return response;
    }

    public static <T> ApiResponse<T> error(String message, String tier) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.message = message;
        response.tier = tier;
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }
}
