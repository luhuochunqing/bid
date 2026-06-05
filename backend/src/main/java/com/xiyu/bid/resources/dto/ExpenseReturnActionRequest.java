package com.xiyu.bid.resources.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExpenseReturnActionRequest {

    private String comment;

    @NotBlank(message = "Actor is required")
    private String actor;
}
