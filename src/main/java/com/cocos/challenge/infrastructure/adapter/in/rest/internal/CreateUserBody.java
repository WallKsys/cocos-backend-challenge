package com.cocos.challenge.infrastructure.adapter.in.rest.internal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserBody(@NotBlank @Email @Size(max = 255) String email) {
}
