package cc.nanoic.yunanexus.file.storage;

import cc.nanoic.yunanexus.common.web.common.BusinessException;
import cc.nanoic.yunanexus.common.web.common.R;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StorageDriverRegistry {
    @Resource
    private List<StorageDriver> storageDrivers;

    public StorageDriver requireDriver(Integer storageVendor) {
        return storageDrivers.stream()
                .filter(driver -> driver.supports(storageVendor))
                .findFirst()
                .orElseThrow(() -> new BusinessException(R.SERVER_ERROR, "当前存储节点没有可用驱动"));
    }
}