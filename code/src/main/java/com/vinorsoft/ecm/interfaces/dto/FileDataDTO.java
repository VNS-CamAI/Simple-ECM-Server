package com.vinorsoft.ecm.interfaces.dto;

import java.nio.file.Path;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileDataDTO {
    private String fileName;
    private Path filePath;
}
