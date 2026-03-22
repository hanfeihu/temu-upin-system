package com.tminos.temu.upin.sdk.v2.dto;

/**
 * TEMU OpenAPI response wrapper.
 *
 * <ul>
 *   <li>{@code success}: business success flag returned by TEMU</li>
 *   <li>{@code errorCode}: TEMU business error code</li>
 *   <li>{@code errorMsg}: TEMU business error message</li>
 *   <li>{@code requestId}: TEMU request trace id, useful for support/debugging</li>
 *   <li>{@code result}: typed business payload</li>
 * </ul>
 */
public class TemuApiResponse<T> {

    private boolean success;
    private Integer errorCode;
    private String errorMsg;
    private String requestId;
    private T result;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(Integer errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }
}
