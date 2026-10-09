package com.genealogy.common.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Converts every uncaught exception into an RFC 9457 ProblemDetail response, in Vietnamese. */
// Extends ResponseEntityExceptionHandler: a bare @RestControllerAdvice turned every MVC exception into 500.
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String SQL_UNIQUE = "23505";
    private static final String SQL_FOREIGN_KEY = "23503";
    private static final String SQL_CHECK = "23514";
    private static final String SQL_NOT_NULL = "23502";
    private static final String SQL_TOO_LONG = "22001";

    // The Vietnamese name of each request field a validation message can be about; a Java path means nothing.
    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("surname", "Họ"),
            Map.entry("middleName", "Tên đệm"),
            Map.entry("givenName", "Tên"),
            Map.entry("names", "Tên"),
            Map.entry("name", "Tên"),
            Map.entry("fullName", "Họ tên"),
            Map.entry("title", "Tiêu đề"),
            Map.entry("author", "Tác giả"),
            Map.entry("dateText", "Thời điểm"),
            Map.entry("repository", "Nơi lưu giữ"),
            Map.entry("notes", "Ghi chú"),
            Map.entry("description", "Mô tả"),
            Map.entry("locator", "Vị trí trích"),
            Map.entry("quote", "Đoạn trích"),
            Map.entry("plot", "Vị trí mộ"),
            Map.entry("latitude", "Vĩ độ"),
            Map.entry("longitude", "Kinh độ"),
            Map.entry("caption", "Chú thích"),
            Map.entry("message", "Nội dung"),
            Map.entry("reviewNote", "Lời người duyệt"),
            Map.entry("changeNote", "Lý do sửa"),
            Map.entry("reason", "Lý do"),
            Map.entry("year", "Năm"),
            Map.entry("month", "Tháng"),
            Map.entry("day", "Ngày"),
            Map.entry("year2", "Năm kết thúc"),
            Map.entry("month2", "Tháng kết thúc"),
            Map.entry("day2", "Ngày kết thúc"),
            Map.entry("birthOrder", "Thứ tự sinh"),
            Map.entry("orderIndex", "Thứ tự"),
            Map.entry("sortOrder", "Thứ tự"),
            Map.entry("email", "Email"),
            Map.entry("password", "Mật khẩu"),
            Map.entry("currentPassword", "Mật khẩu hiện tại"),
            Map.entry("newPassword", "Mật khẩu mới"),
            Map.entry("query", "Từ khoá"),
            Map.entry("ids", "Danh sách"),
            Map.entry("depth", "Số đời"),
            Map.entry("days", "Số ngày"),
            Map.entry("size", "Số dòng mỗi trang"));

    /**
     * Renders a domain exception using the status and title it carries, logging the cause of a server fault.
     *
     * @param ex the thrown API exception
     * @return the problem detail to serialise
     */
    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex) {
        // A 5xx carries its cause for exactly this line; without it a broken font or a storage outage left no trace.
        if (ex.getStatus().is5xxServerError()) {
            log.error("{}: {}", ex.getStatus(), ex.getMessage(), ex.getCause());
        }
        return problem(ex.getStatus(), ex.getTitle(), ex.getMessage());
    }

    /**
     * Renders bean-validation failures on a request body as a single problem detail.
     *
     * @param ex the validation failure
     * @param headers the response headers Spring prepared
     * @param status the status Spring chose
     * @param request the current request
     * @return the response to send
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<String> messages = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> messages.add(describe(error)));
        // A class-level rule is a global error; joining field errors only sent it out with an empty detail.
        ex.getBindingResult().getGlobalErrors().stream().map(ObjectError::getDefaultMessage).forEach(messages::add);
        return ResponseEntity.badRequest().body(badRequest(messages));
    }

    /**
     * Renders constraints on a controller's parameters, as Spring MVC validates them itself.
     *
     * @param ex the validation failure
     * @param headers the response headers Spring prepared
     * @param status the status Spring chose
     * @param request the current request
     * @return the response to send
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        // The base class answered "Validation failure", naming neither the field nor the rule.
        List<String> messages = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> labelled(result.getMethodParameter().getParameterName(), message(error))))
                .toList();
        return ResponseEntity.badRequest().body(badRequest(messages));
    }

    /**
     * Renders constraint violations raised by a {@code @Validated} controller or service.
     *
     * @param ex the constraint violation
     * @return the problem detail to serialise
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        // Its own message is "search.arg0: must be…": a Java method name, in English, straight to the toast.
        List<String> messages = ex.getConstraintViolations().stream()
                .map(violation -> labelled(lastNode(violation), violation.getMessage()))
                .toList();
        return badRequest(messages);
    }

    /**
     * Renders an upload the multipart resolver rejected for being too large.
     *
     * @param ex the size failure
     * @param headers the headers to use for the response
     * @param status the status the base class chose
     * @param request the current request
     * @return the response carrying the problem detail
     */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        // Overridden, not added: ResponseEntityExceptionHandler already maps this, and a second mapping stops the app.
        ProblemDetail problem =
                problem(HttpStatus.PAYLOAD_TOO_LARGE, ProblemMessages.TITLE_TOO_LARGE, "Tệp quá lớn, tối đa 20 MB");
        return handleExceptionInternal(ex, problem, headers, HttpStatus.PAYLOAD_TOO_LARGE, request);
    }

    /**
     * Renders two writes to the same row racing each other, or one waiting too long for another, as a 409.
     *
     * @param ex the failed version check, lock or timeout
     * @return the problem detail to serialise
     */
    @ExceptionHandler({
        OptimisticLockingFailureException.class,
        PessimisticLockingFailureException.class,
        QueryTimeoutException.class,
    })
    public ProblemDetail handleConcurrentWrite(RuntimeException ex) {
        // A deadlock or a lock timeout is the same situation as a version clash, and retrying is the same answer.
        log.warn("A write lost a race: {}", ex.getClass().getSimpleName());
        return problem(HttpStatus.CONFLICT, ProblemMessages.TITLE_CONFLICT, ProblemMessages.RACED);
    }

    /**
     * Renders a sort on a field the record does not have as a 400.
     *
     * @param ex the unknown property
     * @return the problem detail to serialise
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleUnknownSort(PropertyReferenceException ex) {
        return problem(HttpStatus.BAD_REQUEST, ProblemMessages.TITLE_BAD_REQUEST,
                "Không sắp xếp được theo trường " + ex.getPropertyName());
    }

    /**
     * Renders a write refused by a database constraint, saying which rule refused it.
     *
     * @param ex the constraint failure
     * @return the problem detail to serialise
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrityViolation(DataIntegrityViolationException ex) {
        String sqlState = sqlState(ex);
        String constraint = constraintName(ex);
        // Named, never the message: Postgres puts the offending row's values there, a living person's included.
        log.warn("A write was refused by the database: state {}, constraint {}", sqlState, constraint);
        // Only a unique clash is a race; a CHECK or a too-long value fails the same way on every retry.
        String named = ProblemMessages.forConstraint(constraint).orElse(null);
        if (SQL_UNIQUE.equals(sqlState)) {
            return problem(HttpStatus.CONFLICT, ProblemMessages.TITLE_CONFLICT, named != null
                    ? named
                    : "Đã có một bản ghi như vậy — có thể ai đó vừa thêm. Hãy tải lại trang.");
        }
        if (SQL_FOREIGN_KEY.equals(sqlState)) {
            return problem(HttpStatus.CONFLICT, ProblemMessages.TITLE_CONFLICT,
                    "Bản ghi này đang được chỗ khác dùng tới, hoặc thứ nó trỏ tới vừa bị xoá. Hãy tải lại trang.");
        }
        String detail = named != null ? named : switch (sqlState == null ? "" : sqlState) {
            case SQL_TOO_LONG -> "Có một ô nhập dài quá mức cho phép.";
            case SQL_NOT_NULL -> "Còn thiếu một thông tin bắt buộc.";
            case SQL_CHECK -> "Dữ liệu này không hợp lệ nên không lưu được.";
            default -> "Dữ liệu này không lưu được.";
        };
        return problem(HttpStatus.BAD_REQUEST, ProblemMessages.TITLE_BAD_REQUEST, detail);
    }

    /**
     * Renders anything unmapped as a 500 without leaking internals to the client.
     *
     * @param ex the unexpected exception
     * @return the problem detail to serialise
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ProblemMessages.TITLE_SERVER_ERROR,
                ProblemMessages.UNEXPECTED);
    }

    /**
     * Translates the English title and detail Spring gives its own exceptions, leaving ours as they are.
     *
     * @param ex the exception being rendered
     * @param body the body the base class built, usually a ProblemDetail
     * @param headers the response headers
     * @param statusCode the response status
     * @param request the current request
     * @return the response to send
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        // Spring titles its own problems with the reason phrase ("Bad Request"); ours carry a Vietnamese title.
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        // Most of Spring's own handlers pass no body and let the exception carry it, a 404 among them.
        Object problemBody = body == null && ex instanceof ErrorResponse error ? error.getBody() : body;
        if (problemBody instanceof ProblemDetail problem && status != null
                && status.getReasonPhrase().equals(problem.getTitle())) {
            problem.setTitle(titleOf(status));
            problem.setDetail(genericDetail(status));
        }
        return super.handleExceptionInternal(ex, problemBody, headers, statusCode, request);
    }

    /**
     * Builds a problem detail with a status, a title and a detail.
     *
     * @param status the status
     * @param title the Vietnamese title
     * @param detail the Vietnamese detail
     * @return the problem detail
     */
    private static ProblemDetail problem(HttpStatusCode status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }

    /**
     * Builds the 400 for one or more validation messages.
     *
     * @param messages the Vietnamese messages, each already labelled
     * @return the problem detail
     */
    private static ProblemDetail badRequest(List<String> messages) {
        String detail = messages.isEmpty() ? "Dữ liệu gửi lên không hợp lệ." : String.join("; ", messages);
        return problem(HttpStatus.BAD_REQUEST, ProblemMessages.TITLE_BAD_REQUEST, detail);
    }

    /**
     * Formats one field error as "Label: message".
     *
     * @param error the field error
     * @return the formatted message
     */
    private static String describe(FieldError error) {
        return labelled(error.getField(), error.getDefaultMessage());
    }

    /**
     * Prefixes a validation message with the Vietnamese name of its field, when that name is known.
     *
     * @param field the field or parameter path, such as {@code names[0].givenName}
     * @param message the Vietnamese message
     * @return the labelled message
     */
    private static String labelled(String field, String message) {
        if (field == null) {
            return message;
        }
        String last = field.substring(field.lastIndexOf('.') + 1).replaceAll("\\[\\d*]", "");
        String label = FIELD_LABELS.get(last);
        return label == null ? message : label + ": " + message;
    }

    /**
     * Reads the default message of a resolvable validation error.
     *
     * @param error the error
     * @return its message
     */
    private static String message(MessageSourceResolvable error) {
        return error.getDefaultMessage();
    }

    /**
     * Returns the name of the last node of a violation's path, the field or parameter it is about.
     *
     * @param violation the violation
     * @return the node name, or null
     */
    private static String lastNode(ConstraintViolation<?> violation) {
        String last = null;
        for (Path.Node node : violation.getPropertyPath()) {
            last = node.getName();
        }
        return last;
    }

    /**
     * Finds the SQLSTATE of the database error behind a failure.
     *
     * @param ex the failure
     * @return the five-character state, or null when no SQL error is in the chain
     */
    private static String sqlState(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    /**
     * Finds the name of the constraint behind a failure, as Hibernate reports it.
     *
     * @param ex the failure
     * @return the constraint name, or null when none was reported
     */
    private static String constraintName(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }

    /**
     * Names a status in Vietnamese.
     *
     * @param status the status
     * @return the title
     */
    private static String titleOf(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> ProblemMessages.TITLE_UNAUTHORIZED;
            case FORBIDDEN -> ProblemMessages.TITLE_FORBIDDEN;
            case NOT_FOUND -> ProblemMessages.TITLE_NOT_FOUND;
            case CONFLICT -> ProblemMessages.TITLE_CONFLICT;
            case PAYLOAD_TOO_LARGE -> ProblemMessages.TITLE_TOO_LARGE;
            case SERVICE_UNAVAILABLE -> ProblemMessages.TITLE_UNAVAILABLE;
            default -> status.is5xxServerError()
                    ? ProblemMessages.TITLE_SERVER_ERROR
                    : ProblemMessages.TITLE_BAD_REQUEST;
        };
    }

    /**
     * Says in Vietnamese what went wrong with a request Spring itself refused.
     *
     * @param status the status
     * @return the detail
     */
    private static String genericDetail(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Không có đường dẫn này.";
            case METHOD_NOT_ALLOWED -> "Thao tác này không được hỗ trợ ở đây.";
            case NOT_ACCEPTABLE, UNSUPPORTED_MEDIA_TYPE -> "Định dạng dữ liệu không được hỗ trợ.";
            case BAD_REQUEST -> "Dữ liệu gửi lên không đọc được hoặc thiếu một thông tin bắt buộc.";
            default -> status.is5xxServerError() ? ProblemMessages.UNEXPECTED : "Yêu cầu gửi lên không hợp lệ.";
        };
    }
}
