package cc.nanoic.yunanexus.auth.entity;

/** Administrative trust classification, never accepted from an application owner. */
public enum ApplicationCategory {
    OFFICIAL_NATIVE, OFFICIAL_THIRD_PARTY, THIRD_PARTY;
    public static boolean allowsPassword(String category) {
        return OFFICIAL_NATIVE.name().equals(category)||OFFICIAL_THIRD_PARTY.name().equals(category);
    }
    public static boolean allowsCredit(String category){return allowsPassword(category);}
}
