package com.xiyu.bid.projectworkflow.parser;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class ScoreDraftFileTypePolicy {

    public FileType detect(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".docx")) {
            return FileType.DOCX;
        }
        if (lower.endsWith(".doc")) {
            return FileType.DOC;
        }
        if (lower.endsWith(".xlsx")) {
            return FileType.XLSX;
        }
        if (lower.endsWith(".xls")) {
            return FileType.XLS;
        }
        if (lower.endsWith(".pdf")) {
            return FileType.PDF;
        }
        throw new IllegalArgumentException("仅支持 .doc/.docx/.xls/.xlsx/.pdf 评分文件");
    }
}
