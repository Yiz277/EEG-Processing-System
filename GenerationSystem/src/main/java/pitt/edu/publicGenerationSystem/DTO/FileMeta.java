package pitt.edu.publicGenerationSystem.DTO;

import lombok.Data;

@Data
public class FileMeta {
    private String name;       // 文件名，例如 abc.lay
    private long size;         // 原始大小（解密后大小）
    private String sha256;     // 可选：完整性校验
}
