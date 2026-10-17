package com.jhaseft.tss.dto;

import java.util.Set;

public record CreateUserRequest(
        String email,
        String password,
        String firstName,
        String lastName,
        Set<String> roles
) {}
