package com.aiagent.agent.workflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record WorkflowRequest(
        @NotBlank @Size(max = 2000) String query,
        @NotNull @Size(max = 100) List<@NotNull @Valid UsageRow> usage
) {
    public record UsageRow(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String product,
            @NotBlank @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])") String month,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,16}") String unit,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 6) BigDecimal quantity
    ) {}
}
