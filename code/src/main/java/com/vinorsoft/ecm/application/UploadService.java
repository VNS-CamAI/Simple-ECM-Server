package com.vinorsoft.ecm.application;

import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.codec.multipart.FilePart;
import com.vinorsoft.ecm.domain.FileECM;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UploadService {

    Mono<FileECM> saveFile(FilePart file, UUID fileId, String category);

    Mono<byte[]> getFileContent(UUID fileId);

    Mono<InputStreamResource> getMultipleFileContent(List<UUID> fileIds);

    Mono<FileECM> getFileInfo(UUID fileId);

    Flux<FileECM> getFileInfos(List<UUID> fileId);

    Mono<Boolean> deleteFile(UUID fileId);
}
