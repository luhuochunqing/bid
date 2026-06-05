package com.xiyu.bid.documents.service;

import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.documents.dto.AssemblyRequest;
import com.xiyu.bid.documents.dto.TemplateCreateRequest;
import com.xiyu.bid.documents.entity.AssemblyTemplate;
import com.xiyu.bid.documents.entity.DocumentAssembly;
import com.xiyu.bid.documents.repository.AssemblyTemplateRepository;
import com.xiyu.bid.documents.repository.DocumentAssemblyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

@ExtendWith(MockitoExtension.class)
abstract class AbstractDocumentAssemblyServiceTest {

    @Mock
    protected AssemblyTemplateRepository templateRepository;

    @Mock
    protected DocumentAssemblyRepository assemblyRepository;

    @Mock
    protected IAuditLogService auditLogService;

    protected DocumentAssemblyService documentAssemblyService;
    protected AssemblyTemplate testTemplate;
    protected DocumentAssembly testAssembly;
    protected TemplateCreateRequest createRequest;
    protected AssemblyRequest assemblyRequest;

    @BeforeEach
    void setUpDocumentAssemblyFixture() {
        documentAssemblyService = new DocumentAssemblyService(
                templateRepository,
                assemblyRepository,
                auditLogService
        );

        testTemplate = AssemblyTemplate.builder()
                .id(1L)
                .name("投标书模板")
                .description("标准投标书模板")
                .category("BIDDING_DOCUMENT")
                .templateContent("尊敬的${招标方名称}：\n\n我方愿意参与${项目名称}的投标，报价为${报价金额}元。")
                .variables("{\"招标方名称\":\"string\",\"项目名称\":\"string\",\"报价金额\":\"number\"}")
                .createdBy(100L)
                .createdAt(LocalDateTime.now())
                .build();

        testAssembly = DocumentAssembly.builder()
                .id(1L)
                .projectId(200L)
                .templateId(1L)
                .assembledContent("尊敬的XX公司：\n\n我方愿意参与ABC项目的投标，报价为500000元。")
                .variables("{\"招标方名称\":\"XX公司\",\"项目名称\":\"ABC项目\",\"报价金额\":500000}")
                .assembledBy(300L)
                .assembledAt(LocalDateTime.now())
                .build();

        createRequest = TemplateCreateRequest.builder()
                .name("新模板")
                .description("新模板描述")
                .category("CONTRACT")
                .templateContent("合同内容：${甲方}与${乙方}")
                .variables("{\"甲方\":\"string\",\"乙方\":\"string\"}")
                .createdBy(100L)
                .build();

        assemblyRequest = AssemblyRequest.builder()
                .templateId(1L)
                .variables("{\"招标方名称\":\"XX公司\",\"项目名称\":\"ABC项目\",\"报价金额\":500000}")
                .assembledBy(300L)
                .build();
    }
}
