package com.vinorsoft.ecm.application.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.vinorsoft.ecm.application.VideoCompressionService;

import java.io.File;
import java.io.IOException;

@Service
public class VideoCompressionServiceImpl implements VideoCompressionService {
    private static final Logger logger = LoggerFactory.getLogger(VideoCompressionServiceImpl.class);

    /*
     * -vcodec libx265: Sử dụng H.265 để giảm dung lượng so với H.264.
     * -crf 28: Giữ chất lượng tốt trong khi giảm kích thước.
     * -preset slow: Tối ưu nén để giữ chất lượng.
     * -b:v 1000k: Giảm bitrate video xuống 1000 kbps.
     * -r 24: Giảm số khung hình trên giây xuống 24 FPS.
     * -vf scale=1280:720: Giảm độ phân giải xuống 1920:1080 (FullHD).
     * -acodec aac -b:a 64k: Nén âm thanh bằng AAC với bitrate 64kbps.
     */
    @Async
    public String compressVideo(String inputPath) {

        // Kiểm tra file đầu vào có tồn tại không
        File inputFile = new File(inputPath);
        if (!inputFile.exists() || !inputFile.isFile()) {
            logger.error("Input file does not exist: " + inputPath);
            return null;
        }

        // Tạo đường dẫn output (tự động thêm "_compress" nhưng giữ nguyên phần mở rộng)
        String outputPath = getOutputPath(inputPath);

        // Câu lệnh FFmpeg để scale về Full HD (1920px) nhưng giữ nguyên tỷ lệ
        String command = "ffmpeg -i " + inputPath +
                " -vcodec libx265 -crf 28 -preset slow" +
                " -b:v 1000k -r 24 -vf \"scale='if(gt(iw,ih),1920,-1)':'if(gt(iw,ih),-1,1920)'\"" +
                " -acodec aac -b:a 64k " + outputPath;

        try {
            logger.info("Starting video compression: " + inputPath);
            ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                logger.info("Compression successful: " + outputPath);
                return outputPath;
            } else {
                logger.error("Compression failed with exit code: " + exitCode);
                return null;
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Error during video compression: ", e);
            return null;
        }
    }

    // Hàm tạo đường dẫn output (inputPath + "_compress" nhưng giữ nguyên phần mở
    // rộng)
    private String getOutputPath(String inputPath) {
        File inputFile = new File(inputPath);
        String parentDir = inputFile.getParent();
        String fileName = inputFile.getName();

        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            // Nếu không có phần mở rộng, thêm _compress vào cuối tên file
            return parentDir + File.separator + fileName + "_compress";
        }

        String fileNameWithoutExt = fileName.substring(0, lastDotIndex);
        String fileExtension = fileName.substring(lastDotIndex);

        return parentDir + File.separator + fileNameWithoutExt + "_compress" + fileExtension;
    }
}
