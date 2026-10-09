package com.genealogy.common.model;

/** What kind of record one revision in the audit trail describes, with the Vietnamese noun for it. */
// In common/model: every audited feature writes it, and §4 puts shared discriminators here.
public enum AuditEntityType {

    // One person, their names included.
    PERSON("người"),

    // One union, its child links included.
    FAMILY("hôn nhân"),

    // One dated event of a person or a union.
    EVENT("sự kiện"),

    // One chi, phái or nhánh.
    BRANCH("chi"),

    // One place in the hierarchy; a rename changes what every event there says.
    PLACE("nơi chốn"),

    // One source of evidence.
    SOURCE("nguồn"),

    // One citation of a source against a record.
    CITATION("dẫn chứng"),

    // One grave record.
    GRAVE("mộ phần"),

    // One photo or scan; the file itself is gone after a delete, its caption and kind stay in the trail.
    MEDIA("tệp"),

    // One suggestion offered by a member, and its review.
    SUGGESTION("đề xuất"),

    // One app account; its password hash never enters the trail.
    MEMBER("tài khoản");

    // The one Vietnamese name of each kind: media, citations and nine stale-form checks each typed their own (#26).
    private final String noun;

    /**
     * Creates a kind with its Vietnamese noun.
     *
     * @param noun what a message calls one record of this kind
     */
    AuditEntityType(String noun) {
        this.noun = noun;
    }

    /**
     * Returns what a message calls one record of this kind.
     *
     * @return the Vietnamese noun, such as "hôn nhân"
     */
    public String noun() {
        return noun;
    }

    /**
     * Returns the noun of another discriminator whose constant has the same name, such as a media target.
     *
     * @param kind a constant named like one of these
     * @return its Vietnamese noun
     */
    public static String nounOf(Enum<?> kind) {
        return valueOf(kind.name()).noun();
    }
}
