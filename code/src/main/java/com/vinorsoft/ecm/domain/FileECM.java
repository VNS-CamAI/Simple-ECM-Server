package com.vinorsoft.ecm.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Table("FILE_ECM")
public class FileECM {
    @Id
    private UUID id;

    @Size(max = 1000)
    @NotNull
    @Column(value = "FILE_NAME")
    private String fileName;

    @Size(max = 2000)
    @NotNull
    @Column(value = "FILE_PATH")
    private String filePath;

    @Size(max = 50)
    @NotNull
    @Column(value = "CONTENT_TYPE")
    private String contentType;

    @Column(value = "FILE_SIZE")
    private long fileSize;

    @Size(max = 255)
    @NotNull
    @Column(value = "CATEGORY")
    private String category;

    @Column(value = "VERSION")
    private Integer version;

    @Column(value = "DATE_UPLOAD")
    private LocalDateTime dateUpload;

    @Column(value = "UPDATE_AT")
    private LocalDateTime updatedAt;

    public FileECM(@Size(max = 1000) @NotNull String fileName, @Size(max = 2000) @NotNull String filePath,
            @Size(max = 50) @NotNull String contentType, long fileSize, @Size(max = 255) @NotNull String category,
            Integer version, LocalDateTime dateUpload, LocalDateTime updatedAt) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.category = category;
        this.version = version;
        this.dateUpload = dateUpload;
        this.updatedAt = updatedAt;
    }
}
