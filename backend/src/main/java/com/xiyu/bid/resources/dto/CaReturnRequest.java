package com.xiyu.bid.resources.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class CaReturnRequest {
    @NotNull(message = "实际归还时间不能为空")
    private LocalDate actualReturnDate;

    private String returnNotes;
}
