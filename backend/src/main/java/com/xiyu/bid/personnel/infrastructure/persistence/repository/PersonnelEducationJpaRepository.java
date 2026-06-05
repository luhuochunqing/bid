package com.xiyu.bid.personnel.infrastructure.persistence.repository;

import com.xiyu.bid.personnel.infrastructure.persistence.entity.PersonnelEducationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PersonnelEducationJpaRepository extends JpaRepository<PersonnelEducationEntity, Long> {

    List<PersonnelEducationEntity> findByPersonnelId(Long personnelId);

    @Modifying
    @Query("DELETE FROM PersonnelEducationEntity e WHERE e.personnelId = :personnelId")
    void deleteByPersonnelId(@Param("personnelId") Long personnelId);
}
