package com.vinorsoft.ecm.infrastructure.background;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingQueue;

import com.vinorsoft.ecm.application.VideoCompressionService;
import com.vinorsoft.ecm.domain.FileECM;
import com.vinorsoft.ecm.domain.FileECMRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileCompressConsumer {
    BlockingQueue<FileECM> emailQueue;

    private final FileECMRepository fileECMRepository;
    private final VideoCompressionService videoCompressionService;

    public FileCompressConsumer(FileECMRepository fileECMRepository, VideoCompressionService videoCompressionService) {
        this.fileECMRepository = fileECMRepository;
        this.videoCompressionService = videoCompressionService;
    }

    public void run() {
        try {
            while (true) {
                FileECM fileEcm = emailQueue.take();

                String pattern = "MM/dd/yyyy HH:mm:ss.SSS";
                DateFormat df = new SimpleDateFormat(pattern);
                log.info(Thread.currentThread().getName() + " file recive at " + df.format(new Date()) + " type: "
                        + fileEcm.getContentType());

                try {

                } catch (Exception ex) {
                    log.error("Error when consume file: {} for type {} with id {}", ex,
                            fileEcm.getContentType(), fileEcm.getId());
                }
            }
        } catch (InterruptedException ex) {
            log.error("Error when consume file: " + ex);
        }
    }
}
