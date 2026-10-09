package com.genealogy.common.model;

/** Administrative level of a place, narrowest first. */
// In common/model: `gedcom` reads a path's levels from PLAC.FORM, and §4 forbids importing place's domain for it.
public enum PlaceType {

    // Thôn / làng / xóm / ấp: where a gia phả's quê quán usually sits.
    VILLAGE,

    // Xã / phường / thị trấn.
    WARD,

    // Huyện / quận / thị xã.
    DISTRICT,

    // Tỉnh / thành phố trực thuộc trung ương.
    PROVINCE,

    // Quốc gia.
    COUNTRY,

    // Tổng, phủ, trấn, châu, or any other unit the modern hierarchy does not have.
    OTHER;

    /**
     * Reports whether a place of this level may sit inside a place of another level.
     *
     * @param parent the level of the proposed parent
     * @return true when the parent is wider, or when either side is OTHER
     */
    public boolean fitsInside(PlaceType parent) {
        // OTHER has no fixed rank: a tổng sat between xã and huyện, a phủ between huyện and trấn.
        return this == OTHER || parent == OTHER || parent.ordinal() > ordinal();
    }
}
