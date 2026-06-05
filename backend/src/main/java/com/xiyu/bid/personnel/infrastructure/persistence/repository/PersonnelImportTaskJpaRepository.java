package com.xiyu.bid.personnel.infrastructure.persistence.repository;

import com.xiyu.bid.personnel.infrastructure.persistence.entity.PersonnelImportTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PersonnelImportTaskJpaRepository extends JpaRepository<PersonnelImportTaskEntity, Long> {

    Optional<PersonnelImportTaskEntity> findByTaskNo(String taskNo);
}
