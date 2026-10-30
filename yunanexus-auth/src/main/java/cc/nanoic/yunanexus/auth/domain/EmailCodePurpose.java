package cc.nanoic.yunanexus.auth.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

/** 邮箱挑战的用途；LOGIN 只保留挑战空间，尚未作为登录凭证接入。 */
public enum EmailCodePurpose {
    REGISTER("注册账号"),
    LOGIN("登录账号");

    private final String description;

    EmailCodePurpose(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static EmailCodePurpose parse(String purpose) {
        return valueOf(purpose);
    }
}
