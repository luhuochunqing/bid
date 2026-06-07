package com.xiyu.bid.template.dto;

import com.xiyu.bid.entity.Template;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateMutationRequest {

    private String name;
    private Template.Category category;
    private String productType;
    private String industry;
    private String documentType;
    private String fileUrl;
    private String description;
    private String fileSize;
    private List<String> tags;
    private Long createdBy;
}
