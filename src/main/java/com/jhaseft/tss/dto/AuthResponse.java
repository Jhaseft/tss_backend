package com.jhaseft.tss.dto;

import java.util.Set;

public record AuthResponse(String token, String login, Set<String> roles) {}
