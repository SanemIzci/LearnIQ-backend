package com.learniq.auth.dto;

public record CreateGlobalUserRequest(String username, String password, String email) {
}
