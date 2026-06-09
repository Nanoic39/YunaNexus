package cc.nanoic.yunanexus.common.web.dto;

import lombok.Data;

@Data
public class UuidResult {
    private byte[] globalId;
    private String internalUuid;
    private String externalUuid;
}
