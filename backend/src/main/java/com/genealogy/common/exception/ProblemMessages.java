package com.genealogy.common.exception;

import java.util.Map;
import java.util.Optional;

/** The Vietnamese titles and messages every error response shares, in one place (CLAUDE.md §6: toasts show them). */
// One table: the 409 sentence had two drifted copies, and the titles were English wherever the detail was not.
public final class ProblemMessages {

    public static final String TITLE_BAD_REQUEST = "Yêu cầu không hợp lệ";
    public static final String TITLE_UNAUTHORIZED = "Chưa đăng nhập";
    public static final String TITLE_FORBIDDEN = "Không có quyền";
    public static final String TITLE_NOT_FOUND = "Không tìm thấy";
    public static final String TITLE_CONFLICT = "Xung đột dữ liệu";
    public static final String TITLE_TOO_LARGE = "Tệp quá lớn";
    public static final String TITLE_TOO_MANY_REQUESTS = "Thử quá nhiều lần";
    public static final String TITLE_SERVER_ERROR = "Lỗi máy chủ";
    public static final String TITLE_UNAVAILABLE = "Dịch vụ tạm ngừng";

    // Said for a stale form and for two saves that raced; the wording is StaleEdit's, the subject generic.
    public static final String RACED =
            "Có người khác vừa sửa bản ghi này. Hãy tải lại trang để xem bản mới rồi sửa lại.";

    public static final String CITED_ALREADY = "Nguồn này đã được trích dẫn cho bản ghi này ở cùng vị trí.";

    public static final String UNAUTHORIZED = "Phiên đăng nhập đã hết hoặc chưa đăng nhập. Hãy đăng nhập lại.";
    public static final String FORBIDDEN = "Tài khoản của bạn không được làm việc này.";
    public static final String UNEXPECTED =
            "Máy chủ gặp lỗi ngoài dự kiến. Hãy thử lại sau ít phút; nếu vẫn lỗi, hãy báo trưởng tộc.";

    // What the database refuses by name: each is a rule the family can act on once it is said in words.
    private static final Map<String, String> CONSTRAINTS = Map.ofEntries(
            Map.entry("ck_places_coordinates_paired", "Vĩ độ và kinh độ phải có cả hai, hoặc để trống cả hai."),
            Map.entry("ck_graves_coordinates_paired", "Vĩ độ và kinh độ phải có cả hai, hoặc để trống cả hai."),
            Map.entry("ck_media_portrait_person", "Chỉ một người mới có ảnh chân dung."),
            Map.entry("ck_events_date_leap_month", "Tháng nhuận chỉ có ở một tháng âm lịch."),
            Map.entry("ck_events_date_leap_month2", "Tháng nhuận chỉ có ở một tháng âm lịch."),
            Map.entry("ck_events_date_precision", "Ngày dương lịch có tháng hoặc ngày thì phải có năm."),
            Map.entry("ck_events_date_second_endpoint", "Chỉ một khoảng thời gian mới có mốc thứ hai."),
            Map.entry("ck_families_distinct_partners", "Một người không thể kết hôn với chính mình."),
            Map.entry("ck_families_has_partner", "Một hôn nhân cần ít nhất một người."),
            Map.entry("ck_branches_not_own_parent", "Một chi không thể nằm trong chính nó."),
            Map.entry("ck_places_not_own_parent", "Một nơi chốn không thể nằm trong chính nó."),
            Map.entry("uq_media_one_portrait", "Người này vừa được đặt một ảnh chân dung khác. Hãy tải lại trang."),
            Map.entry("uq_person_names_one_primary", "Một người chỉ có một tên chính."),
            Map.entry("uq_family_children", "Người này đã được ghi là con của hôn nhân này."),
            Map.entry("uq_citations", CITED_ALREADY),
            Map.entry("uq_branches_sibling_name", "Đã có một chi cùng tên trong cùng chi cha."),
            Map.entry("uq_members_email_lower", "Email này đã có tài khoản."));

    /** Not instantiable. */
    private ProblemMessages() {
    }

    /**
     * Returns the Vietnamese explanation of a named database constraint.
     *
     * @param constraint the constraint name, or null
     * @return the explanation, or empty when the constraint has none written
     */
    public static Optional<String> forConstraint(String constraint) {
        return constraint == null ? Optional.empty() : Optional.ofNullable(CONSTRAINTS.get(constraint));
    }
}
