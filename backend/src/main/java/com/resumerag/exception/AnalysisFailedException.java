package com.resumerag.exception;

public class AnalysisFailedException extends RuntimeException {

    public AnalysisFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
