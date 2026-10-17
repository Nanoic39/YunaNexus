package cc.nanoic.yunanexus.permission.domain;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class CatalogValidationTest {
    @Test
    void seededNamespacedMenuCodesRemainEditable() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var menu = new MenuSaveRequest(); menu.setMenuCode("system:users"); menu.setMenuName("用户管理");
            assertTrue(factory.getValidator().validate(menu).isEmpty());
            menu.setMenuCode("system users");
            assertFalse(factory.getValidator().validate(menu).isEmpty());
        }
    }

    @Test
    void unknownStatusesAndNegativeParentsAreRejected() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var menu = new MenuSaveRequest(); menu.setMenuCode("draft"); menu.setMenuName("草稿");
            var permission = new PermissionSaveRequest(); permission.setPermCode("draft:read"); permission.setResource("/draft/**"); permission.setAction("read");
            for (int status : new int[]{-1, 2}) {
                menu.setStatus(status); permission.setStatus(status);
                assertFalse(validator.validate(menu).isEmpty());
                assertFalse(validator.validate(permission).isEmpty());
            }
            menu.setStatus(1); menu.setParentId(-1L);
            assertFalse(validator.validate(menu).isEmpty());
            menu.setParentId(0L); permission.setStatus(0);
            assertTrue(validator.validate(menu).isEmpty());
            assertTrue(validator.validate(permission).isEmpty());
        }
    }
}
