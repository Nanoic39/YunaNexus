package cc.nanoic.yunanexus.file.entity.DTO;

import lombok.Data;

@Data
public class QuotaResult {
    private String roleName;
    private boolean singleFileUnlimited;
    private long maxSingleFileSize;
    private boolean totalStorageUnlimited;
    private long maxTotalStorage;
    private long usedStorage;
    private long remainingStorage;
}