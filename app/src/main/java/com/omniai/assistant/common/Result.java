package com.omniai.assistant.common;

public class Result<T> {

    private boolean success;
    private T data;
    private String error;
    private int code;

    private Result(boolean success, T data, String error, int code) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.code = code;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(true, data, null, 0);
    }

    public static <T> Result<T> success(T data, int code) {
        return new Result<>(true, data, null, code);
    }

    public static <T> Result<T> error(String error) {
        return new Result<>(false, null, error, -1);
    }

    public static <T> Result<T> error(String error, int code) {
        return new Result<>(false, null, error, code);
    }

    public static <T> Result<T> error(String error, int code, T data) {
        return new Result<>(false, data, error, code);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public boolean hasData() {
        return data != null;
    }

    public T getDataOr(T defaultValue) {
        return data != null ? data : defaultValue;
    }
}
