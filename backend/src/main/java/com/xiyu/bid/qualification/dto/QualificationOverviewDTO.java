package com.xiyu.bid.qualification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualificationOverviewDTO {
    private long total;
    private long expiring;
    private long expired;
    private long borrowed;
}
