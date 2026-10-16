package cc.nanoic.yunanexus.user.service;

import static org.junit.jupiter.api.Assertions.*;

import cc.nanoic.yunanexus.common.web.exception.BizException;

import org.junit.jupiter.api.Test;

class BadgeCatalogValidationTest {
    @Test
    void validatesDefinitionsAndDoesNotPretendAutomaticRulesAreImplemented() {
        assertDoesNotThrow(
                () ->
                        BadgeCatalogService.validate(
                                new BadgeCatalogService.Save(
                                        "member", "会员", 1, null, null, "manual", null, 1)));
        assertThrows(
                BizException.class,
                () ->
                        BadgeCatalogService.validate(
                                new BadgeCatalogService.Save(
                                        "member", "会员", 10, null, null, "manual", null, 1)));
        assertThrows(
                BizException.class,
                () ->
                        BadgeCatalogService.validate(
                                new BadgeCatalogService.Save(
                                        "member", "会员", 1, null, null, "condition", "login>7", 1)));
        assertThrows(
                BizException.class,
                () ->
                        BadgeCatalogService.validate(
                                new BadgeCatalogService.Save(
                                        "member",
                                        "会员",
                                        1,
                                        null,
                                        "javascript:alert(1)",
                                        "manual",
                                        null,
                                        1)));
    }
}
