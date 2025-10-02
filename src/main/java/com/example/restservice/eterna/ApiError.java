package com.example.restservice.eterna;

public final class ApiError<S> implements ApiResult<S> {
  private String type;
  private String message;

  public ApiError() {}

  private ApiError(String type, String message) {
    this.type = type;
    this.message = message;
  }

  public String getType() {
    return type;
  }

  public String getMessage() {
    return message;
  }
}
