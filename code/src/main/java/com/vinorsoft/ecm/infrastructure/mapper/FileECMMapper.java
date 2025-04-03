package com.vinorsoft.ecm.infrastructure.mapper;

import java.util.List;

import org.mapstruct.*;

import com.vinorsoft.ecm.domain.FileECM;
import com.vinorsoft.ecm.interfaces.dto.FileECMDTO;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface FileECMMapper {
    FileECM toEntity(FileECMDTO fileDTO);

    FileECMDTO toDTO(FileECM file);

    List<FileECM> toListEntity(List<FileECMDTO> files);

    List<FileECMDTO> toListDTO(List<FileECM> files);
}
