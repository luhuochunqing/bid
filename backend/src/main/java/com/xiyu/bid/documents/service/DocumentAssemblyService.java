// Input: documents repositories, DTOs, and support services
// Output: Document Assembly business service operations
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.documents.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.documents.dto.AssemblyTemplateDTO;
import com.xiyu.bid.documents.dto.DocumentAssemblyDTO;
import com.xiyu.bid.documents.dto.TemplateCreateRequest;
import com.xiyu.bid.documents.entity.AssemblyTemplate;
import com.xiyu.bid.documents.entity.DocumentAssembly;
import com.xiyu.bid.documents.repository.AssemblyTemplateRepository;
import com.xiyu.bid.documents.repository.DocumentAssemblyRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.audit.service.IAuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档组装服务
 * 提供模板管理和文档组装功能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAssemblyService {

    private final AssemblyTemplateRepository templateRepository;
    private final DocumentAssemblyRepository assemblyRepository;
    private final IAuditLogService auditLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建文档模板
     */
    @Auditable(action = "CREATE", entityType = "AssemblyTemplate", description = "Create assembly template")
    @Transactional
    public AssemblyTemplateDTO createTemplate(TemplateCreateRequest request) {
        log.info("Creating assembly template: {}", request.getName());

        // Validate input
        validateTemplateRequest(request);

        AssemblyTemplate template = AssemblyTemplate.builder()
                .name(request.getName())
                .description(request.getDescription())
                .category(request.getCategory())
                .templateContent(request.getTemplateContent())
                .variables(request.getVariables())
                .createdBy(request.getCreatedBy())
                .build();

        AssemblyTemplate savedTemplate = templateRepository.save(template);
        log.info("Created assembly template with id: {}", savedTemplate.getId());

        return convertToDTO(savedTemplate);
    }

    /**
     * 根据分类获取模板列表
     */
    @Transactional(readOnly = true)
    public List<AssemblyTemplateDTO> getTemplatesByCategory(String category) {
        log.debug("Fetching templates by category: {}", category);
        return templateRepository.findByCategory(category).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 组装文档
     * 使用模板和变量值生成最终的文档内容
     */
    @Auditable(action = "ASSEMBLE", entityType = "DocumentAssembly", description = "Assemble document from template")
    @Transactional
    public DocumentAssemblyDTO assembleDocument(Long projectId, Long templateId,
                                                 String variables, Long assembledBy) {
        log.info("Assembling document for project: {} using template: {}", projectId, templateId);

        // Get template
        AssemblyTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Template not found with id: " + templateId));

        // Replace variables in template content
        String assembledContent = replaceVariables(template.getTemplateContent(), variables);

        // Create assembly record
        DocumentAssembly assembly = DocumentAssembly.builder()
                .projectId(projectId)
                .templateId(templateId)
                .assembledContent(assembledContent)
                .variables(variables)
                .assembledBy(assembledBy)
                .build();

        DocumentAssembly savedAssembly = assemblyRepository.save(assembly);
        log.info("Assembled document with id: {}", savedAssembly.getId());

        return convertToDTO(savedAssembly);
    }

    /**
     * 获取项目的所有组装记录
     */
    @Transactional(readOnly = true)
    public List<DocumentAssemblyDTO> getAssembliesByProject(Long projectId) {
        log.debug("Fetching assemblies for project: {}", projectId);
        return assemblyRepository.findByProjectId(projectId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 重新生成组装文档
     * 使用相同的变量值重新生成文档
     */
    @Auditable(action = "REGENERATE", entityType = "DocumentAssembly", description = "Regenerate assembled document")
    @Transactional
    public DocumentAssemblyDTO regenerateAssembly(Long assemblyId) {
        log.info("Regenerating assembly with id: {}", assemblyId);

        // Get existing assembly
        DocumentAssembly existingAssembly = assemblyRepository.findById(assemblyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Assembly not found with id: " + assemblyId));

        // Get template
        AssemblyTemplate template = templateRepository.findById(existingAssembly.getTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Template not found with id: " + existingAssembly.getTemplateId()));

        // Reassemble content
        String assembledContent = replaceVariables(template.getTemplateContent(),
                existingAssembly.getVariables());

        // Create new assembly record
        DocumentAssembly newAssembly = DocumentAssembly.builder()
                .projectId(existingAssembly.getProjectId())
                .templateId(existingAssembly.getTemplateId())
                .assembledContent(assembledContent)
                .variables(existingAssembly.getVariables())
                .assembledBy(existingAssembly.getAssembledBy())
                .build();

        DocumentAssembly savedAssembly = assemblyRepository.save(newAssembly);
        log.info("Regenerated document with new id: {}", savedAssembly.getId());

        return convertToDTO(savedAssembly);
    }

    /**
     * 替换模板中的变量占位符
     * 支持 ${variableName} 格式的占位符
     */
    public String replaceVariables(String templateContent, String variablesJson) {
        if (templateContent == null || templateContent.isEmpty()) {
            return templateContent;
        }

        if (variablesJson == null || variablesJson.isEmpty()) {
            return templateContent;
        }

        try {
            // Parse JSON variables
            Map<String, Object> variables = objectMapper.readValue(variablesJson,
                    new TypeReference<Map<String, Object>>() {});

            String result = templateContent;

            // Replace each variable placeholder
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                // Validate variable name to prevent injection attacks
                if (!isValidVariableName(entry.getKey())) {
                    log.warn("Invalid variable name detected and skipped: {}", entry.getKey());
                    continue;
                }

                String placeholder = "${" + entry.getKey() + "}";
                // Sanitize value to prevent script injection
                String value = sanitizeValue(entry.getValue());
                result = result.replace(placeholder, value);
            }

            return result;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse variables JSON: {}", variablesJson, e);
            // Return original template if JSON parsing fails
            return templateContent;
        }
    }

    /**
     * 验证变量名是否合法
     * 防止模板注入攻击
     * 只允许字母、数字、下划线和连字符
     */
    private boolean isValidVariableName(String variableName) {
        if (variableName == null || variableName.isEmpty()) {
            return false;
        }
        // Only allow alphanumeric characters, underscores, and hyphens
        return variableName.matches("^[\\p{L}\\p{N}_-]+$");
    }

    /**
     * 清理变量值
     * 防止XSS和脚本注入攻击
     */
    private String sanitizeValue(Object value) {
        if (value == null) {
            return "";
        }

        String stringValue = value.toString();

        // Remove dangerous HTML/Script tags
        return stringValue
                .replaceAll("(?i)<script[^>]*>.*?</script>", "")
                .replaceAll("(?i)<iframe[^>]*>.*?</iframe>", "")
                .replaceAll("(?i)<object[^>]*>.*?</object>", "")
                .replaceAll("(?i)<embed[^>]*>.*?</embed>", "")
                .replaceAll("(?i)javascript:", "")
                .replaceAll("(?i)on\\w+\\s*=", "");
    }

    /**
     * 验证模板创建请求
     */
    private void validateTemplateRequest(TemplateCreateRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Template name cannot be null or empty");
        }

        if (request.getTemplateContent() == null || request.getTemplateContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Template content cannot be null or empty");
        }
    }

    /**
     * 转换模板实体为DTO
     */
    private AssemblyTemplateDTO convertToDTO(AssemblyTemplate template) {
        return AssemblyTemplateDTO.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .category(template.getCategory())
                .templateContent(template.getTemplateContent())
                .variables(template.getVariables())
                .createdBy(template.getCreatedBy())
                .createdAt(template.getCreatedAt())
                .build();
    }

    /**
     * 转换组装记录实体为DTO
     */
    private DocumentAssemblyDTO convertToDTO(DocumentAssembly assembly) {
        return DocumentAssemblyDTO.builder()
                .id(assembly.getId())
                .projectId(assembly.getProjectId())
                .templateId(assembly.getTemplateId())
                .assembledContent(assembly.getAssembledContent())
                .variables(assembly.getVariables())
                .assembledBy(assembly.getAssembledBy())
                .assembledAt(assembly.getAssembledAt())
                .build();
    }
}
