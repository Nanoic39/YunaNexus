package cc.nanoic.yunanexus.auth.entity.DTO;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EndpointConfigItem {
    private String httpMethod;
    private String pathPattern;
    private String requiredCode;
    private Integer status;
}
