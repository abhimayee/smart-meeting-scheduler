package com.example.scheduler.shared.exception;

public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message, Throwable cause) { super(message, cause); }
}
