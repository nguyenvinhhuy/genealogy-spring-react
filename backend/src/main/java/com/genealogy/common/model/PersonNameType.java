package com.genealogy.common.model;

/** Which kind of name a person_name row records; a cụ tổ commonly has three or four. */
public enum PersonNameType {

    // Tên khai sinh: the name given at birth.
    BIRTH,

    // Tên húy: the taboo name, avoided in speech after death.
    HUY,

    // Tên tự: the courtesy name taken in adulthood.
    TU,

    // Tên hiệu: the chosen art or scholarly name.
    HIEU,

    // Thụy hiệu: the posthumous honorific.
    THUY,

    // Tên thánh: the baptismal name.
    SAINT,

    // Biệt danh: any other name the family used.
    ALIAS
}
