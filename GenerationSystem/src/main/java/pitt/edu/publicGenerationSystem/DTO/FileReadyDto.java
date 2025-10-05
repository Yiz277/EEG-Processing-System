package pitt.edu.publicGenerationSystem.DTO;

import lombok.Data;
import java.util.List;

@Data
public class FileReadyDto {
    private Long taskFileId;
    private String sourcePath;       // 比如 "xyz123"
    private String status;           // READY / FAILED / etc.
    private String message;          // 可选说明或错误信息
    private List<FileMeta> files;    // 多个文件的列表
    private String baseUrl;          // 电脑IP，用于访问文件
}
