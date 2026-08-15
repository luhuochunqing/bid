// Input: HTTP 请求（五类 match 请求体）
// Output: ApiResponse<KnowledgeMatchResult>
// Pos: scoreparse/controller — 知识库五类匹配 REST 入口（spec 041 US2）
// 维护声明: 维护者按项目SOP；contracts/knowledge-match-api.md §1-§5
package com.xiyu.bid.scoreparse.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.scoreparse.application.match.BrandMatchService;
import com.xiyu.bid.scoreparse.application.match.CertMatchService;
import com.xiyu.bid.scoreparse.application.match.PersonMatchService;
import com.xiyu.bid.scoreparse.application.match.ProjectMatchService;
import com.xiyu.bid.scoreparse.application.match.WarehouseMatchService;
import com.xiyu.bid.scoreparse.dto.BrandMatchRequest;
import com.xiyu.bid.scoreparse.dto.CertMatchRequest;
import com.xiyu.bid.scoreparse.dto.KnowledgeMatchResult;
import com.xiyu.bid.scoreparse.dto.PersonMatchRequest;
import com.xiyu.bid.scoreparse.dto.ProjectMatchRequest;
import com.xiyu.bid.scoreparse.dto.WarehouseMatchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库五类匹配 Controller（spec 041 US2）。
 * <p>无状态确定性查询（不含 LLM，SC-005）；空结果 NONE 不抛错（FR-024）。
 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "知识库五类匹配", description = "评分标准解析的知识库证据匹配（spec 041）")
public class KnowledgeMatchController {

    private final CertMatchService certMatchService;
    private final PersonMatchService personMatchService;
    private final ProjectMatchService projectMatchService;
    private final WarehouseMatchService warehouseMatchService;
    private final BrandMatchService brandMatchService;

    @Operation(summary = "资质证书匹配", description = "FR-009：名称关键词 + 等级 + 有效期；过期证书算命中但标记 expired=true")
    @PostMapping("/cert/match")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<KnowledgeMatchResult>> matchCert(@RequestBody CertMatchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(certMatchService.match(request)));
    }

    @Operation(summary = "人员匹配", description = "FR-010：岗位关键词 + 证书子表（未删除、有效期内）；单人多证计一次")
    @PostMapping("/person/match")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<KnowledgeMatchResult>> matchPerson(@RequestBody PersonMatchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(personMatchService.match(request)));
    }

    @Operation(summary = "项目业绩匹配", description = "FR-010：类型/行业 + 签约日期 + 金额（NULL 金额跳过比对不失配）")
    @PostMapping("/project/match")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<KnowledgeMatchResult>> matchProject(@RequestBody ProjectMatchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(projectMatchService.match(request)));
    }

    @Operation(summary = "仓库匹配", description = "状态 + 名称/区域/面积；设施关键词基于备注文本降级匹配")
    @PostMapping("/warehouse/match")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<KnowledgeMatchResult>> matchWarehouse(@RequestBody WarehouseMatchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(warehouseMatchService.match(request)));
    }

    @Operation(summary = "品牌授权匹配", description = "状态 + 品牌名 + 产品线/进口国产（授权范围降级）+ 有效期；90 天内到期标记 expireSoon")
    @PostMapping("/brand/match")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<KnowledgeMatchResult>> matchBrand(@RequestBody BrandMatchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(brandMatchService.match(request)));
    }
}
