package com.jhaseft.tss.dto;

import java.util.Set;

public record UpdateUserRequest(
        String email,
        String firstName,
        String lastName,
        boolean activated,
        Set<String> roles,
        String password
) {}
