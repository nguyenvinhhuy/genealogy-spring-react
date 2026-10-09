package com.genealogy.member.service.impl;

import com.genealogy.audit.service.AuditService;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.exception.ConflictException;
import com.genealogy.common.exception.ForbiddenException;
import com.genealogy.common.exception.NotFoundException;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.model.Role;
import com.genealogy.common.security.LoginThrottle;
import com.genealogy.common.web.PageRequests;
import com.genealogy.member.domain.Member;
import com.genealogy.member.dto.request.CreateMemberRequest;
import com.genealogy.member.dto.request.Passwords;
import com.genealogy.member.dto.request.UpdateMemberRequest;
import com.genealogy.member.dto.response.MemberResponse;
import com.genealogy.member.dto.response.MemberSnapshot;
import com.genealogy.member.mapper.MemberMapper;
import com.genealogy.member.repository.MemberRepository;
import com.genealogy.member.service.CredentialsChanged;
import com.genealogy.member.service.MemberService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link MemberService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberServiceImpl implements MemberService {

    // A real BCrypt hash matching nothing, so an unknown email still costs one full verification.
    static final String NO_SUCH_MEMBER_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private static final String ADMIN_ONLY = "Chỉ trưởng tộc được quản lý tài khoản.";

    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final LoginThrottle loginThrottle;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    /**
     * Checks a presented email and password, without letting the stored hash leave this feature.
     *
     * @param email the email presented, in any case
     * @param rawPassword the password presented
     * @return the account when the credentials are good and it is active, empty otherwise
     */
    @Override
    // No transaction around BCrypt: the lookup opens its own, so no pooled connection waits on a hash.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<MemberResponse> verifyCredentials(String email, String rawPassword) {
        Member member = memberRepository.findByEmail(normaliseEmail(email)).orElse(null);

        // Hashed even when nobody matched, or an unknown email answers in a millisecond and a known one in tens.
        String hash = member == null ? NO_SUCH_MEMBER_HASH : member.getPasswordHash();
        boolean matches = matchesStored(rawPassword, hash);

        // One empty answer for all three cases: the caller cannot tell them apart and so cannot leak them.
        if (member == null || !matches || !member.isActive()) {
            return Optional.empty();
        }
        return Optional.of(memberMapper.toResponse(member));
    }

    /**
     * Returns an account only while it is still active, for a caller re-checking a live session.
     *
     * @param id member id
     * @return the member, or empty when unknown or disabled
     */
    @Override
    public Optional<MemberResponse> findActive(Long id) {
        return memberRepository.findById(id).filter(Member::isActive).map(memberMapper::toResponse);
    }

    /**
     * Makes every access token issued to an account stop working at once.
     *
     * @param memberId the account whose tokens end
     */
    @Override
    @Transactional
    public void endAccessTokens(Long memberId) {
        memberRepository.bumpCredentialVersion(memberId);
    }

    /**
     * Replaces a member's own password, after checking the one they currently hold, and ends their sessions.
     *
     * @param memberId the account changing its password
     * @param currentPassword the password they hold now
     * @param newPassword the password to set
     */
    @Override
    @Transactional
    public void changeOwnPassword(Long memberId, String currentPassword, String newPassword) {
        Member member = require(memberId);
        // Counted per account, apart from sign-in, so a borrowed session cannot guess without limit.
        LoginThrottle.Attempt attempt = loginThrottle.beginPasswordCheck(memberId);
        // Asked for, or whoever borrows an open session can lock the owner out of their own account.
        if (!matchesStored(currentPassword, member.getPasswordHash())) {
            // A 400, not a 401: the client reads 401 as an expired session and signs the caller out.
            throw new BadRequestException("Mật khẩu hiện tại không đúng.");
        }
        attempt.succeeded();
        if (currentPassword.equals(newPassword)) {
            throw new BadRequestException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        MemberSnapshot before = memberMapper.toSnapshot(member);
        setPassword(member, newPassword);
        recordChange(member, before, memberId, "Đổi mật khẩu");
    }

    /**
     * Sets another member's password without knowing the old one, refusing callers below ADMIN.
     *
     * @param actorId the ADMIN doing the reset
     * @param callerRole the calling member's access level
     * @param memberId the account whose password is being reset
     * @param newPassword the password to set
     */
    @Override
    @Transactional
    public void resetPassword(Long actorId, Role callerRole, Long memberId, String newPassword) {
        requireAdmin(callerRole);
        // Else a borrowed ADMIN session skips the current-password check that changing one's own requires.
        if (memberId.equals(actorId)) {
            throw new BadRequestException("Không đặt lại mật khẩu của chính mình ở đây; hãy dùng Đổi mật khẩu.");
        }
        Member member = require(memberId);
        MemberSnapshot before = memberMapper.toSnapshot(member);
        setPassword(member, newPassword);
        recordChange(member, before, actorId, "Trưởng tộc đặt lại mật khẩu");
    }

    /**
     * Lists every account by name, for an ADMIN managing who may sign in.
     *
     * @param pageable the page to return
     * @param callerRole the calling member's access level
     * @return one page of accounts
     */
    @Override
    public Page<MemberResponse> search(Pageable pageable, Role callerRole) {
        // The matcher is not the guard (§3.6): every account's email is personal data.
        requireAdmin(callerRole);
        Pageable page = PageRequests.capped(pageable, Sort.by("fullName").and(Sort.by("id")));
        return memberRepository.findAll(page).map(memberMapper::toResponse);
    }

    /**
     * Returns the account with the given id.
     *
     * @param id member id
     * @return the member
     */
    @Override
    public MemberResponse getById(Long id) {
        return memberMapper.toResponse(require(id));
    }

    /**
     * Creates an app account with a hashed password, refusing callers below ADMIN.
     *
     * @param actorId the ADMIN creating it
     * @param callerRole the calling member's access level
     * @param request the account to create
     * @return the created member
     */
    @Override
    @Transactional
    public MemberResponse create(Long actorId, Role callerRole, CreateMemberRequest request) {
        // The matcher is not the guard (§3.6): a creator picks the role, so this is as strong as an ADMIN.
        requireAdmin(callerRole);
        String email = normaliseEmail(request.email());
        if (memberRepository.existsByEmail(email)) {
            throw new ConflictException("Email này đã có tài khoản: " + email);
        }

        Member member = memberMapper.toEntity(request);
        member.setEmail(email);
        member.setPasswordHash(encode(request.password()));
        Member saved = memberRepository.save(member);
        auditService.record(AuditEntityType.MEMBER, saved.getId(), AuditAction.CREATE,
                null, memberMapper.toSnapshot(saved), actorId, null);
        return memberMapper.toResponse(saved);
    }

    /**
     * Renames an account, changes its role or disables it, refusing callers below ADMIN.
     *
     * @param actorId the ADMIN making the change
     * @param callerRole the calling member's access level
     * @param memberId the account to change
     * @param request the account's new state
     * @return the account as it is now
     */
    @Override
    @Transactional
    public MemberResponse update(Long actorId, Role callerRole, Long memberId, UpdateMemberRequest request) {
        requireAdmin(callerRole);
        // Locked before anything is read: two ADMINs demoting each other would each still see the other active.
        List<Member> activeAdmins = memberRepository.lockActiveByRole(Role.ADMIN);
        Member member = require(memberId);
        boolean demotes = member.getRole() == Role.ADMIN && request.role() != Role.ADMIN;
        boolean disables = member.isActive() && !request.active();
        // Else the trưởng tộc can lock themselves out, and nobody is left who could let them back in.
        if (memberId.equals(actorId) && (demotes || disables)) {
            throw new BadRequestException("Không tự khoá hay tự hạ vai trò tài khoản của chính mình.");
        }
        boolean removesActiveAdmin = (demotes && member.isActive()) || (disables && member.getRole() == Role.ADMIN);
        if (removesActiveAdmin && activeAdmins.size() <= 1) {
            throw new BadRequestException("Phải còn ít nhất một trưởng tộc đang hoạt động.");
        }

        MemberSnapshot before = memberMapper.toSnapshot(member);
        boolean accessChanges = member.getRole() != request.role() || member.isActive() != request.active();
        member.setFullName(request.fullName().strip());
        member.setRole(request.role());
        member.setActive(request.active());
        if (accessChanges) {
            // A disabled or re-roled account signs in again, so no session keeps the access it had.
            member.setCredentialVersion(member.getCredentialVersion() + 1);
            events.publishEvent(new CredentialsChanged(memberId));
        }
        auditService.record(AuditEntityType.MEMBER, memberId, AuditAction.UPDATE,
                before, memberMapper.toSnapshot(member), actorId, request.changeNote());
        return memberMapper.toResponse(member);
    }

    /**
     * Sets a new password, bumps the credential version and ends every session the account holds.
     *
     * @param member the account
     * @param newPassword the password to set
     */
    private void setPassword(Member member, String newPassword) {
        member.setPasswordHash(encode(newPassword));
        // Every access token issued under the old password stops working at once, not in 15 minutes (§8.10 #10).
        member.setCredentialVersion(member.getCredentialVersion() + 1);
        // Inside this transaction: revoking the sessions separately left them alive if the second step failed.
        events.publishEvent(new CredentialsChanged(member.getId()));
    }

    /**
     * Records a password change in the trail, without the hash.
     *
     * @param member the account as it is now
     * @param before the account as it was
     * @param actorId who made the change
     * @param note what kind of change it was
     */
    private void recordChange(Member member, MemberSnapshot before, Long actorId, String note) {
        auditService.record(AuditEntityType.MEMBER, member.getId(), AuditAction.UPDATE,
                before, memberMapper.toSnapshot(member), actorId, note);
    }

    /**
     * Refuses any caller below ADMIN.
     *
     * @param callerRole the calling member's access level
     */
    private static void requireAdmin(Role callerRole) {
        if (callerRole != Role.ADMIN) {
            throw new ForbiddenException(ADMIN_ONLY);
        }
    }

    /**
     * Checks a presented password against a stored hash, answering false for one BCrypt cannot read in full.
     *
     * @param rawPassword the password presented
     * @param hash the stored hash
     * @return true when they match
     */
    private boolean matchesStored(String rawPassword, String hash) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > Passwords.MAX_BYTES) {
            // Spring Security 7 refuses such input outright, so it is never a match; hashed anyway for equal timing.
            passwordEncoder.matches("", hash);
            return false;
        }
        return passwordEncoder.matches(rawPassword, hash);
    }

    /**
     * Hashes a password after checking BCrypt can read all of it.
     *
     * @param rawPassword the password to hash
     * @return the hash
     */
    private String encode(String rawPassword) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > Passwords.MAX_BYTES) {
            throw new BadRequestException("Mật khẩu quá dài. Hãy dùng mật khẩu ngắn hơn: chữ có dấu chiếm"
                    + " nhiều chỗ hơn chữ không dấu, tối đa khoảng 24 chữ có dấu.");
        }
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Loads an account or fails.
     *
     * @param memberId member id
     * @return the entity
     */
    private Member require(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Không có tài khoản với id " + memberId));
    }

    /**
     * Brings an email to the one form accounts are stored and looked up in.
     *
     * @param email the email as typed
     * @return the email trimmed and lower-cased
     */
    static String normaliseEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
