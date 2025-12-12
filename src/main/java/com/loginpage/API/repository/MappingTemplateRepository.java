package com.loginpage.API.repository;

import com.loginpage.API.model.MappingTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MappingTemplateRepository extends JpaRepository<MappingTemplate, Long> {
    List<MappingTemplate> findAllByOrderByCreatedAtDesc();
    List<MappingTemplate> findByDeletedFalseOrderByCreatedAtDesc();
    List<MappingTemplate> findByDeletedTrueOrderByDeletedAtDesc();
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);
}

