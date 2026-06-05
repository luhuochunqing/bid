package com.xiyu.bid.casework.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseSearchResultDTO {

    private List<CaseDTO> items;
    private long total;
    private int page;
    private int pageSize;
    private int totalPages;
    private String sort;
}
