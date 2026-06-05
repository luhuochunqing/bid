package com.xiyu.bid.templatecatalog.infrastructure.persistence;

import com.xiyu.bid.entity.Template;
import com.xiyu.bid.repository.TemplateRepository;
import com.xiyu.bid.templatecatalog.application.command.TemplateQueryCriteria;
import com.xiyu.bid.templatecatalog.domain.port.TemplateCatalogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TemplateCatalogRepositoryAdapter implements TemplateCatalogRepository {

    private final TemplateRepository templateRepository;

    @Override
    public Template save(Template template) {
        return templateRepository.save(template);
    }

    @Override
    public Optional<Template> findById(Long id) {
        return templateRepository.findById(id);
    }

    @Override
    public List<Template> findAll(TemplateQueryCriteria criteria) {
        Sort sort = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
        return templateRepository.findAll(buildSpecification(criteria), sort);
    }

    @Override
    public boolean existsById(Long id) {
        return templateRepository.existsById(id);
    }

    @Override
    public void deleteById(Long id) {
        templateRepository.deleteById(id);
    }

    private Specification<Template> buildSpecification(TemplateQueryCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria == null) {
                return cb.conjunction();
            }
            if (criteria.getCategory() != null) {
                predicates.add(cb.equal(root.get("category"), criteria.getCategory()));
            }
            if (criteria.getCreatedBy() != null) {
                predicates.add(cb.equal(root.get("createdBy"), criteria.getCreatedBy()));
            }
            if (criteria.getProductType() != null) {
                predicates.add(cb.equal(root.get("productType"), criteria.getProductType().getLabel()));
            }
            if (criteria.getIndustry() != null) {
                predicates.add(cb.equal(root.get("industry"), criteria.getIndustry().getLabel()));
            }
            if (criteria.getDocumentType() != null) {
                predicates.add(cb.equal(root.get("documentType"), criteria.getDocumentType().getLabel()));
            }
            if (criteria.getName() != null && !criteria.getName().isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("name")),
                        "%" + criteria.getName().toLowerCase(Locale.ROOT) + "%"
                ));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
