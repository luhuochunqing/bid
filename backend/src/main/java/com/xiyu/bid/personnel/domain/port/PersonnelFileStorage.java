package com.xiyu.bid.personnel.domain.port;

public interface PersonnelFileStorage {

    String storeCertAttachment(Long personnelId, Long certId, byte[] content, String originalFilename, String contentType);

    String storeCertAttachmentWithNaming(
            Long personnelId,
            Long certId,
            byte[] content,
            String personnelName,
            String employeeNumber,
            int certificateSequence,
            String certificateName,
            String originalFilename,
            String contentType
    );

    String generateStandardFileName(
            String personnelName,
            String employeeNumber,
            int certificateSequence,
            String certificateName,
            String originalFilename
    );
}
