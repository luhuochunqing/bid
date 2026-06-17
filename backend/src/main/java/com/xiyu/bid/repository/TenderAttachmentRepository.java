package com.xiyu.bid.repository;

import com.xiyu.bid.entity.TenderAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TenderAttachmentRepository extends JpaRepository<TenderAttachment, Long> {

    List<TenderAttachment> findByTenderId(Long tenderId);

    void deleteByTenderId(Long tenderId);
}
