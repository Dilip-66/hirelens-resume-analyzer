package com.resumerag.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentUser {
    public UUID id() {
        String sub = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return UUID.fromString(sub);
    }
}
