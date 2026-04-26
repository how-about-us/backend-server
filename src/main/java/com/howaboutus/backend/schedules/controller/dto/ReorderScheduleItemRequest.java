package com.howaboutus.backend.schedules.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReorderScheduleItemRequest(
        @NotNull @Min(0) Integer newOrderIndex
) {
}
