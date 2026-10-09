package com.genealogy.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Unit tests for what a family is told when something fails (CLAUDE.md §8.10 #8, #17–#19). */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /**
     * Builds the exception Spring throws when Postgres refuses a write.
     *
     * @param sqlState the SQLSTATE
     * @param constraint the constraint name, or null
     * @return the exception
     */
    private static DataIntegrityViolationException refused(String sqlState, String constraint) {
        SQLException sql =
                new SQLException("ERROR: new row violates … Failing row contains (Nguyễn Văn An, …)", sqlState);
        return new DataIntegrityViolationException("could not execute statement",
                new org.hibernate.exception.ConstraintViolationException("insert", sql, constraint));
    }

    @Test
    @DisplayName("a CHECK the service did not pre-check says what rule it is, as a 400, not 'someone else edited'")
    void checkViolationSaysWhichRule() {
        ProblemDetail problem = handler.handleIntegrityViolation(refused("23514", "ck_places_coordinates_paired"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("Vĩ độ và kinh độ phải có cả hai, hoặc để trống cả hai.");
        assertThat(problem.getTitle()).isEqualTo(ProblemMessages.TITLE_BAD_REQUEST);
    }

    @ParameterizedTest(name = "SQLSTATE {0} is {1}")
    @DisplayName("each kind of refusal gets its own status and a sentence that fits it")
    @CsvSource({
        "23505, 409, Đã có một bản ghi như vậy",
        "23503, 409, đang được chỗ khác dùng tới",
        "22001, 400, dài quá mức cho phép",
        "23502, 400, thiếu một thông tin bắt buộc",
        "23514, 400, không hợp lệ",
    })
    void refusalsAreTold(String sqlState, int status, String words) {
        ProblemDetail problem = handler.handleIntegrityViolation(refused(sqlState, "some_unnamed_constraint"));

        assertThat(problem.getStatus()).isEqualTo(status);
        assertThat(problem.getDetail()).contains(words);
        // The row's values are in Postgres's own message, and a living person's name must not travel (§8.10 #15).
        assertThat(problem.getDetail()).doesNotContain("Nguyễn Văn An");
    }

    @Test
    @DisplayName("a lock the write waited too long for is a 409 to retry, not a 500")
    void lockFailureIsAConflict() {
        ProblemDetail problem = handler.handleConcurrentWrite(new CannotAcquireLockException("deadlock detected"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).isEqualTo(ProblemMessages.RACED);
    }

    @Test
    @DisplayName("an unexpected failure is answered in Vietnamese and leaks nothing")
    void unexpectedIsVietnamese() {
        ProblemDetail problem = handler.handleUnexpected(new IllegalStateException("jdbc:postgresql://postgres:5432"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).isEqualTo(ProblemMessages.UNEXPECTED).doesNotContain("postgres");
        assertThat(problem.getTitle()).isEqualTo(ProblemMessages.TITLE_SERVER_ERROR);
    }

    @Test
    @DisplayName("a domain exception keeps its own Vietnamese title and detail")
    void apiExceptionIsRenderedAsIs() {
        ProblemDetail problem = handler.handleApiException(new NotFoundException("Không có người với id 9"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Không tìm thấy");
        assertThat(problem.getDetail()).isEqualTo("Không có người với id 9");
    }
}
