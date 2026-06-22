package cc.nanoic.yunanexus.auth.entity.DTO;

import lombok.Data;

import java.util.List;

@Data
public class EndpointReportRequest {
    private String serviceName;
    private List<EndpointItem> endpoints;

    @Data
    public static class EndpointItem {
        private String httpMethod;
        private String pathPattern;
        private String requiredCode;
        private String description;
    }
}
