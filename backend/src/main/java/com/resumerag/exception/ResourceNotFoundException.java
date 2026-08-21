package com.resumerag.exception;

public class ResourceNotFoundException extends RuntimeException {

    private final String resourceId;

    public ResourceNotFoundException(String message) {
        super(message);
        this.resourceId = null;
    }

    public ResourceNotFoundException(String message, String resourceId) {
        super(message);
        this.resourceId = resourceId;
    }

    public String getResourceId() {
        return resourceId;
    }
}
