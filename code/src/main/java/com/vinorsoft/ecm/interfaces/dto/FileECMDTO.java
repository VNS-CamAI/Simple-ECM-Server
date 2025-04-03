package com.vinorsoft.ecm.interfaces.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Data;

@Data
public class FileECMDTO {
    private UUID id;
    private String fileName;
    private String contentType;
    private long fileSize;
    private String category;
    private Integer version;
    private LocalDateTime dateUpload;
}
