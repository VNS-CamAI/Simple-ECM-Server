package com.vinorsoft.ecm.application.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import com.vinorsoft.ecm.application.UploadService;
import com.vinorsoft.ecm.domain.FileECM;
import com.vinorsoft.ecm.domain.FileECMRepository;
import com.vinorsoft.ecm.interfaces.dto.FileDataDTO;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Slf4j
public class UploadServiceImpl implements UploadService {
    @Value("${upload.allowed-extensions}")
    private String allowedExtensionsConfig;

    @Value("${upload.allowed-media-types}")
    private String allowedMediaTypesConfig;

    private Set<String> ALLOWED_EXTENSIONS;
    private Set<String> ALLOWED_MEDIA_TYPES;

    private static final Pattern FILENAME_PATTERN = Pattern.compile("^[^/\\\\]+\\.[a-zA-Z0-9]+$");
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    @Value("${spring.servlet.multipart.max-file-size}")
    private String maxFileSize;

    @Value("${spring.servlet.compress.enabled}")
    private Boolean enableCompression;

    @Value("${upload.path}")
    private String BASE_UPLOAD_DIR;

    @Qualifier("fileCompressQueue")
    BlockingQueue<FileECM> fileCompressQueue;

    private final FileECMRepository fileECMRepository;

    public UploadServiceImpl(FileECMRepository fileECMRepository) {
        this.fileECMRepository = fileECMRepository;
    }

    @PostConstruct
    public void init() {
        ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(allowedExtensionsConfig.split(",")));
        ALLOWED_MEDIA_TYPES = new HashSet<>(Arrays.asList(allowedMediaTypesConfig.split(",")));

        // Kiểm tra và tạo thư mục BASE_UPLOAD_DIR nếu chưa tồn tại
        try {
            Path baseDir = Paths.get(BASE_UPLOAD_DIR);
            if (!Files.exists(baseDir)) {
                Files.createDirectories(baseDir);
                log.info("Thư mục BASE_UPLOAD_DIR đã được tạo: {}", BASE_UPLOAD_DIR);
            } else {
                log.info("Thư mục BASE_UPLOAD_DIR đã tồn tại: {}", BASE_UPLOAD_DIR);
            }
        } catch (IOException e) {
            log.error("Lỗi khi tạo thư mục BASE_UPLOAD_DIR: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể khởi tạo thư mục BASE_UPLOAD_DIR", e);
        }
    }

    public Mono<FileECM> saveFile(FilePart file, UUID fileId, String category) {
        String originalFileName = file.filename();

        return validateFile(originalFileName, category, file)
                .flatMap(valid -> {
                    if (!valid) {
                        return Mono.error(new IOException("File hoặc thư mục không hợp lệ."));
                    }

                    Mono<Integer> versionMono = (fileId == null)
                            ? Mono.just(1)
                            : fileECMRepository.findById(fileId)
                                    .map(files -> files.getVersion() + 1)
                                    .defaultIfEmpty(1);

                    return versionMono.flatMap(nextVersion -> {
                        String newFileName = UUID.randomUUID() + "_v" + nextVersion + "_" + originalFileName;
                        return getSafeCategoryDir(category)
                                .flatMap(categoryDir -> {
                                    Path targetPath = categoryDir.resolve(newFileName);
                                    return saveLargeFile(file, targetPath)
                                            .then(getFileSize(file))
                                            .flatMap(size -> getContentType(file)
                                                    .flatMap(contentType -> {
                                                        FileECM fileEntity = new FileECM(originalFileName,
                                                                targetPath.toString(),
                                                                contentType, size, category, nextVersion,
                                                                LocalDateTime.now(),
                                                                LocalDateTime.now());

                                                        return fileECMRepository.save(fileEntity)
                                                                .doOnSuccess(
                                                                        f -> {
                                                                            log.info(
                                                                                    "File đã lưu vào DB: fileId={}, version={}",
                                                                                    f.getId(), nextVersion);
                                                                            if (enableCompression)
                                                                                try {
                                                                                    fileCompressQueue.put(f);
                                                                                } catch (Exception e) {
                                                                                    log.error(
                                                                                            "Lỗi thêm file vào compress queue {}",
                                                                                            f);
                                                                                }
                                                                        })
                                                                .doOnError(e -> log.error("Lỗi khi lưu file vào DB: {}",
                                                                        e.getMessage(), e));
                                                    }));
                                });
                    })
                            .doOnSuccess(path -> log.info("File saved at: {}", path))
                            .doOnError(e -> log.error("Lỗi khi lưu file: {}", e.getMessage(), e));
                }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Path> getSafeCategoryDir(String category) {
        try {
            if (category == null || category.isBlank() || category.contains("..") || category.contains("/")) {
                log.warn("Thư mục không hợp lệ: {}", category);
                return Mono.error(new IllegalArgumentException("Tên thư mục không hợp lệ"));
            }

            Path baseDir = Paths.get(BASE_UPLOAD_DIR).toRealPath(); // Chuẩn hóa thư mục gốc
            Path categoryDir = baseDir.resolve(category).normalize(); // Chuẩn hóa đường dẫn

            // Kiểm tra xem đường dẫn có nằm trong BASE_UPLOAD_DIR không
            if (!categoryDir.startsWith(baseDir)) {
                log.warn("Đường dẫn truy cập bị chặn: {}", categoryDir);
                return Mono.error(new SecurityException("Hành vi path traversal bị phát hiện!"));
            }

            // Tạo thư mục nếu chưa tồn tại
            if (!Files.exists(categoryDir)) {
                Files.createDirectories(categoryDir);
            }

            return Mono.just(categoryDir);
        } catch (IOException | InvalidPathException e) {
            log.error("Lỗi khi kiểm tra thư mục: {}", e.getMessage(), e);
            return Mono.error(new IOException("Lỗi xử lý thư mục lưu trữ"));
        }
    }

    private Mono<Void> saveLargeFile(FilePart filePart, Path targetPath) {
        return Mono.fromCallable(() -> AsynchronousFileChannel.open(targetPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE))
                .flatMapMany(channel -> filePart.content()
                        .concatMap(dataBuffer -> {
                            ByteBuffer byteBuffer = dataBuffer.toByteBuffer(0, dataBuffer.readableByteCount());
                            return Mono.fromCallable(() -> {
                                channel.write(byteBuffer, channel.size()).get();
                                return dataBuffer.readableByteCount();
                            });
                        }))
                .then();
    }

    public Mono<byte[]> getFileContent(UUID fileId) {
        return fileECMRepository.findById(fileId)
                .flatMap(files -> {
                    if (files == null) {
                        log.warn("File không tồn tại: fileId={}", fileId);
                        return Mono.error(new IOException("File không tồn tại."));
                    }

                    try {
                        Path filePath = Paths.get(files.getFilePath());
                        byte[] fileContent = Files.readAllBytes(filePath);
                        log.info("File content retrieved: fileId={}, version={}, path={}", fileId, files.getVersion(),
                                files.getFilePath());
                        return Mono.just(fileContent);
                    } catch (IOException e) {
                        log.error("Lỗi khi đọc fileId {}: {}", fileId, e.getMessage(), e);
                        return Mono.error(e);
                    }
                }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<InputStreamResource> getMultipleFileContent(List<UUID> fileIds) {
        return Flux.fromIterable(fileIds)
                .flatMap(fileId -> getFileInfo(fileId)
                        .onErrorResume(e -> {
                            log.warn("Lỗi khi lấy file: fileId={}, error={}", fileId, e.getMessage());
                            return Mono.empty();
                        })
                        .flatMap(fileECM -> {
                            Path filePath = Paths.get(fileECM.getFilePath());
                            return Mono.just(new FileDataDTO(fileECM.getFileName(), filePath));
                        }))
                .collectList()
                .flatMap(fileDataList -> Mono.fromCallable(() -> {
                    PipedOutputStream pos = new PipedOutputStream();
                    PipedInputStream pis = new PipedInputStream(pos, 8192); // Dùng buffer để tránh deadlock

                    // Chạy tiến trình zip trong background
                    Executors.newSingleThreadExecutor().submit(() -> {
                        try (ZipOutputStream zos = new ZipOutputStream(pos)) {
                            for (FileDataDTO fileData : fileDataList) {
                                ZipEntry zipEntry = new ZipEntry(fileData.getFileName());
                                zos.putNextEntry(zipEntry);

                                try (InputStream fis = Files.newInputStream(fileData.getFilePath())) {
                                    byte[] buffer = new byte[8192];
                                    int bytesRead;
                                    while ((bytesRead = fis.read(buffer)) != -1) {
                                        zos.write(buffer, 0, bytesRead);
                                    }
                                }

                                zos.closeEntry();
                            }
                            zos.finish();
                        } catch (IOException e) {
                            log.error("Lỗi khi tạo file ZIP", e);
                        } finally {
                            try {
                                pos.close();
                            } catch (IOException ignored) {
                            }
                        }
                    });

                    return new InputStreamResource(pis);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    public Mono<FileECM> getFileInfo(UUID fileId) {
        return fileECMRepository.findById(fileId)
                .flatMap(files -> {
                    if (files == null) {
                        log.warn("File không tồn tại: fileId={}", fileId);
                        return Mono.error(new IOException("File không tồn tại."));
                    }

                    log.info("File retrieved: fileId={}, version={}, path={}", fileId, files.getVersion(),
                            files.getFilePath());
                    return Mono.just(files);
                });
    }

    public Flux<FileECM> getFileInfos(List<UUID> fileIds) {
        return Flux.fromIterable(fileIds)
                .flatMap(fileId -> fileECMRepository.findById(fileId)
                        .switchIfEmpty(Mono.error(new IOException("File không tồn tại: " + fileId))));
    }

    public Mono<Boolean> deleteFile(UUID fileId) {
        return fileECMRepository.findById(fileId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Không tìm thấy file để xóa: fileId={}", fileId);
                    return Mono.error(new IOException("Không tìm thấy file để xóa: fileId=" + fileId));
                }))
                .flatMap(file -> Mono.fromCallable(() -> Files.deleteIfExists(Paths.get(file.getFilePath())))
                        .subscribeOn(Schedulers.boundedElastic()) // Chạy xóa file trên threadpool khác
                        .doOnSuccess(deleted -> log.info("File system deleted: fileId={}, path={}, deleted={}",
                                file.getId(), file.getFilePath(), deleted))
                        .doOnError(e -> log.error("Lỗi khi xóa file trên hệ thống: fileId={}, error={}",
                                fileId, e.getMessage(), e))
                        .then(fileECMRepository.delete(file)) // Xóa trong database
                        .doOnSuccess(unused -> log.info("DB deleted: fileId={}", fileId))
                        .thenReturn(true) // Trả về true khi xóa thành công
                )
                .onErrorResume(e -> {
                    log.error("Lỗi khi xóa fileId {}: {}", fileId, e.getMessage(), e);
                    return Mono.just(false); // Xử lý lỗi mà không làm hỏng flow
                });
    }

    private Mono<Boolean> validateFile(String originalFileName, String category, FilePart file) {
        if (category == null || !CATEGORY_PATTERN.matcher(category).matches()) {
            log.warn("Thư mục không hợp lệ: {}", category);
            return Mono.just(false);
        }

        if (originalFileName == null || !isValidFileName(originalFileName)
                || !FILENAME_PATTERN.matcher(originalFileName).matches()) {
            log.warn("Tên file không hợp lệ: {}", originalFileName);
            return Mono.just(false);
        }

        long maxSizeBytes = parseMaxFileSize(maxFileSize);
        String fileExtension = getFileExtension(originalFileName);

        return Mono.zip(getFileSize(file), getContentType(file))
                .map(tuple -> {
                    long fileSize = tuple.getT1();
                    String contentType = tuple.getT2();

                    if (fileSize > maxSizeBytes) {
                        log.warn("File vượt quá giới hạn {}: {} bytes", maxFileSize, fileSize);
                        return false;
                    }

                    if (!ALLOWED_EXTENSIONS.contains(fileExtension.toLowerCase())) {
                        log.warn("Định dạng file không được phép: .{}", fileExtension);
                        return false;
                    }

                    if (contentType == null || !ALLOWED_MEDIA_TYPES.contains(contentType)) {
                        log.warn("MIME Type không hợp lệ: {}", contentType);
                        return false;
                    }

                    return true;
                })
                .defaultIfEmpty(false);
    }

    public boolean isValidFileName(String fileName) {
        // Kiểm tra rỗng hoặc chứa ký tự nguy hiểm
        if (fileName == null || fileName.isBlank() || fileName.contains("..") || fileName.contains("/")) {
            log.warn("Tên file không hợp lệ: {}", fileName);
            return false;
        }

        // Kiểm tra ký tự đặc biệt nguy hiểm
        Pattern INVALID_CHARS = Pattern.compile("[<>:\"/\\\\|?*]");
        if (INVALID_CHARS.matcher(fileName).find()) {
            log.warn("Tên file chứa ký tự không hợp lệ: {}", fileName);
            return false;
        }

        return true;
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf(".");
        return (lastDotIndex == -1) ? "" : fileName.substring(lastDotIndex + 1);
    }

    private long parseMaxFileSize(String maxSize) {
        maxSize = maxSize.toUpperCase().trim();
        if (maxSize.endsWith("MB")) {
            return Long.parseLong(maxSize.replace("MB", "")) * 1024 * 1024;
        } else if (maxSize.endsWith("KB")) {
            return Long.parseLong(maxSize.replace("KB", "")) * 1024;
        } else {
            return Long.parseLong(maxSize);
        }
    }

    private Mono<Long> getFileSize(FilePart filePart) {
        return createTempFile(filePart.filename())
                .flatMap(tempFile -> filePart.transferTo(tempFile)
                        .then(Mono.fromCallable(() -> Files.size(tempFile.toPath()))));
    }

    private Mono<String> getContentType(FilePart filePart) {
        return Mono.justOrEmpty(filePart.headers().getContentType()) // Get content type
                .map(MediaType::toString) // Convert to string
                .defaultIfEmpty("application/octet-stream"); // Default if not available
    }

    private Mono<File> createTempFile(String filename) {
        return Mono.fromCallable(() -> {
            Path tempFile = Files.createTempFile("upload-", "-" + filename);
            return tempFile.toFile();
        });
    }
}
