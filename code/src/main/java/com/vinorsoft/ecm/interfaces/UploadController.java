package com.vinorsoft.ecm.interfaces;

import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import com.vinorsoft.ecm.application.UploadService;
import com.vinorsoft.ecm.infrastructure.constants.ApiControllerConstants;
import com.vinorsoft.ecm.infrastructure.mapper.FileECMMapper;
import com.vinorsoft.ecm.interfaces.dto.FileECMDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(ApiControllerConstants.API_FILE_UPLOAD)
@RequiredArgsConstructor
@Slf4j
public class UploadController {
    @Autowired
    private final UploadService uploadService;
    @Autowired
    private final FileECMMapper fileEcmMapper;

    @PostMapping(ApiControllerConstants.API_FILE_UPLOAD_UPLOAD)
    public Mono<ResponseEntity<FileECMDTO>> uploadFile(
            @RequestPart("file") Mono<FilePart> filePartMono,
            @RequestParam(name = "fileId", required = false, defaultValue = "") UUID fileId,
            @RequestParam("category") String category) {
        log.info("Nhận request upload file: fileId={}, category={}", fileId, category);
        return filePartMono
                .flatMap(filePart -> uploadService.saveFile(filePart, fileId, category))
                .map(file -> fileEcmMapper.toDTO(file))
                .map(ResponseEntity::ok)
                .doOnSuccess(uuid -> log.info("Upload thành công: fileId={}", uuid))
                .doOnError(e -> log.error("Upload thất bại: {}", e.getMessage(), e))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @PostMapping(ApiControllerConstants.API_FILE_UPLOAD_UPLOAD_MULTIPLE)
    public Mono<ResponseEntity<List<FileECMDTO>>> uploadMutilpleFile(
            @RequestPart("files") Flux<FilePart> files,
            @RequestParam("category") String category) {
        log.info("Nhận request upload multiple files: category={}", category);
        return files
                .flatMap(file -> uploadService.saveFile(file, null, category))
                .collectList()
                .map(fileDatas -> fileEcmMapper.toListDTO(fileDatas))
                .map(ResponseEntity::ok)
                .doOnSuccess(uuids -> log.info("Upload thành công: fileIds={}", uuids))
                .doOnError(e -> log.error("Upload thất bại: {}", e.getMessage(), e))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @GetMapping(ApiControllerConstants.API_FILE_UPLOAD_DOWNLOAD)
    public Mono<ResponseEntity<byte[]>> downloadFile(
            @RequestParam UUID fileId,
            @RequestParam(value = "version", required = false) Integer version) {
        log.info("Nhận request tải file: fileId={}, version={}", fileId, version);

        return uploadService.getFileInfo(fileId)
                .flatMap(fileECM -> uploadService.getFileContent(fileId)
                        .map(fileData -> {
                            log.info("File tải thành công: fileId={}, version={}", fileId, fileECM.getVersion());
                            return ResponseEntity.ok()
                                    .contentType(MediaType.parseMediaType(fileECM.getContentType()))
                                    .body(fileData);
                        }))
                .onErrorResume(e -> {
                    log.warn("File không tìm thấy: fileId={}, version={}", fileId, version);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }

    @GetMapping(ApiControllerConstants.API_FILE_UPLOAD_DOWNLOAD_MULTIPLE)
    public Mono<ResponseEntity<InputStreamResource>> downloadMultipleFiles(
            @RequestParam List<UUID> fileIds,
            @RequestParam(value = "version", required = false) Integer version) {
        log.info("Nhận request tải nhiều file: fileIds={}, version={}", fileIds, version);

        return uploadService.getMultipleFileContent(fileIds)
                .map(inputStreamResource -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"files.zip\"")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(inputStreamResource))
                .onErrorResume(e -> {
                    log.warn("Lỗi khi tải nhiều file: {}", e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    @DeleteMapping(ApiControllerConstants.API_FILE_UPLOAD_DELETE_FILE)
    public Mono<ResponseEntity<String>> deleteFile(
            @RequestParam UUID fileId) {
        log.info("Nhận request xóa file: fileId={}", fileId);
        return uploadService.deleteFile(fileId)
                .map(deleted -> {
                    if (deleted) {
                        log.info("File đã xóa: fileId={}", fileId);
                        return ResponseEntity.ok("File deleted successfully");
                    } else {
                        log.warn("File không tìm thấy để xóa: fileId={}", fileId);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
                    }
                });
    }

    @GetMapping(ApiControllerConstants.API_FILE_UPLOAD_GET_FILE_INFO)
    public Mono<ResponseEntity<FileECMDTO>> getFileInfo(
            @RequestParam UUID fileId) {
        log.info("Nhận request lấy thông tin file: fileId={}", fileId);
        return uploadService.getFileInfo(fileId)
                .flatMap(fileECM -> {
                    FileECMDTO fileECMDto = fileEcmMapper.toDTO(fileECM);
                    log.info("File tải thành công: fileId={}, version={}", fileId, fileECM.getVersion());
                    return Mono.just(ResponseEntity.ok()
                            .body(fileECMDto));

                })
                .onErrorResume(e -> {
                    log.warn("File không tìm thấy: fileId={}", fileId);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }

    @GetMapping(ApiControllerConstants.API_FILE_UPLOAD_GET_FILES_INFO)
    public Mono<ResponseEntity<List<FileECMDTO>>> getFileInfos(
            @RequestParam List<UUID> fileIds) {
        log.info("Nhận request lấy thông tin file: fileIds={}", fileIds);
        return uploadService.getFileInfos(fileIds)
                .flatMap((fileECM) -> Mono.just(fileEcmMapper.toDTO(fileECM)))
                .collectList().map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.warn("File không tìm thấy: fileId={}", fileIds);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }
}
