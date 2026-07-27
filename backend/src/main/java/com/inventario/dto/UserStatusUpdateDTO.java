package com.inventario.dto;

import jakarta.validation.constraints.NotNull;

public record UserStatusUpdateDTO(
        @NotNull
        Boolean enabled
) {
}
