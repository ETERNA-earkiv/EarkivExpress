package com.example.restservice.eterna;

public record ApiSuccess<S>(S value) implements ApiResult<S> {
}
