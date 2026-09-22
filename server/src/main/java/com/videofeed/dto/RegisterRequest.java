package com.videofeed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 20, message = "长度需在3~20之间") String username,
        @NotBlank @Size(min = 6, max = 64, message = "长度需在6~64之间") String password,
        @NotBlank @Size(max = 20) String nickname) {
}
