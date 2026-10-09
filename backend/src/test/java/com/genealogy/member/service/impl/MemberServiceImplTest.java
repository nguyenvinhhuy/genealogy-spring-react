package com.genealogy.member.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genealogy.audit.service.AuditService;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.exception.ForbiddenException;
import com.genealogy.common.exception.NotFoundException;
import com.genealogy.common.exception.TooManyRequestsException;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.model.Role;
import com.genealogy.common.security.LoginThrottle;
import com.genealogy.member.domain.Member;
import com.genealogy.member.dto.request.CreateMemberRequest;
import com.genealogy.member.dto.request.UpdateMemberRequest;
import com.genealogy.member.dto.response.MemberResponse;
import com.genealogy.member.dto.response.MemberSnapshot;
import com.genealogy.member.mapper.MemberMapper;
import com.genealogy.member.repository.MemberRepository;
import com.genealogy.member.service.CredentialsChanged;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Unit tests for {@link MemberServiceImpl}. */
@ExtendWith(MockitoExtension.class)
class MemberServiceImplTest {

    private static final String EMAIL = "admin@genealogy.vn";
    private static final String PASSWORD = "Admin@123";
    // The ADMIN resetting someone else's password; never the account under test.
    private static final Long ACTOR = 7L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private ApplicationEventPublisher events;

    private MemberServiceImpl memberService;
    private PasswordEncoder passwordEncoder;
    private Member member;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();

        // The real MapStruct mapper is used so mapping is exercised rather than stubbed (CLAUDE.md 4.1).
        MemberMapper memberMapper = Mappers.getMapper(MemberMapper.class);
        memberService = new MemberServiceImpl(
                memberRepository, memberMapper, passwordEncoder, new LoginThrottle(), auditService, events);

        member = new Member();
        member.setId(1L);
        member.setFullName("Quan tri vien");
        member.setEmail(EMAIL);
        member.setPasswordHash(passwordEncoder.encode(PASSWORD));
        member.setRole(Role.ADMIN);
        member.setActive(true);
    }

    @Test
    void verifyCredentialsAcceptsTheRightPassword() {
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));

        assertThat(memberService.verifyCredentials(EMAIL, PASSWORD))
                .get()
                .satisfies(response -> {
                    assertThat(response.email()).isEqualTo(EMAIL);
                    assertThat(response.role()).isEqualTo(Role.ADMIN);
                });
    }

    @Test
    void verifyCredentialsIgnoresTheCaseAndSpacesOfTheEmail() {
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));

        // "Admin@Genealogy.vn" from a phone's auto-capitalisation was a wrong password (§8.11 #11).
        assertThat(memberService.verifyCredentials("  Admin@Genealogy.VN ", PASSWORD)).isPresent();
    }

    @Test
    void verifyCredentialsRejectsTheWrongPassword() {
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));

        assertThat(memberService.verifyCredentials(EMAIL, "wrong-password")).isEmpty();
    }

    @Test
    void verifyCredentialsRejectsADisabledAccount() {
        member.setActive(false);
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));

        assertThat(memberService.verifyCredentials(EMAIL, PASSWORD)).isEmpty();
    }

    @Test
    void verifyCredentialsGivesTheSameAnswerForAnUnknownEmail() {
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThat(memberService.verifyCredentials(EMAIL, PASSWORD)).isEmpty();
    }

    @Test
    void theDummyHashIsRealEnoughToCostWhatAMatchCosts() {
        // A malformed hash makes BCrypt return false at once, and the timing oracle comes straight back.
        assertThat(MemberServiceImpl.NO_SUCH_MEMBER_HASH).matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, MemberServiceImpl.NO_SUCH_MEMBER_HASH))
                .isFalse();
    }

    @Test
    void findActiveReturnsNothingForADisabledAccount() {
        member.setActive(false);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThat(memberService.findActive(1L)).isEmpty();
    }

    @Test
    void changeOwnPasswordReplacesTheHashWhenTheCurrentOneIsRight() {
        String oldHash = member.getPasswordHash();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        memberService.changeOwnPassword(1L, PASSWORD, "Moi@12345");

        assertThat(member.getPasswordHash()).isNotEqualTo(oldHash);
        assertThat(passwordEncoder.matches("Moi@12345", member.getPasswordHash())).isTrue();
    }

    @Test
    void changeOwnPasswordRefusesWhenTheCurrentPasswordIsWrong() {
        String oldHash = member.getPasswordHash();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        // A 401 here made the client refresh, retry, and then sign the caller out over a typo.
        assertThatThrownBy(() -> memberService.changeOwnPassword(1L, "wrong-password", "Moi@12345"))
                .isInstanceOf(BadRequestException.class);

        assertThat(member.getPasswordHash()).isEqualTo(oldHash);
        assertThat(member.getCredentialVersion()).isZero();
    }

    @Test
    void changeOwnPasswordBumpsTheVersionEndsTheSessionsAndIsAudited() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        memberService.changeOwnPassword(1L, PASSWORD, "Moi@12345");

        assertThat(member.getCredentialVersion()).isEqualTo(1);
        // Published inside this transaction, so the sessions end with the password or not at all (§8.11 #6).
        verify(events).publishEvent(new CredentialsChanged(1L));
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq(AuditEntityType.MEMBER), eq(1L), eq(AuditAction.UPDATE),
                any(MemberSnapshot.class), after.capture(), eq(1L), any());
        // The trail shows a password changed, never the hash.
        assertThat(after.getValue()).isInstanceOf(MemberSnapshot.class)
                .extracting("credentialVersion").isEqualTo(1);
    }

    @Test
    void changeOwnPasswordRefusesTheSamePasswordAgain() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.changeOwnPassword(1L, PASSWORD, PASSWORD))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("khác");
    }

    @Test
    void changeOwnPasswordLocksAfterTooManyWrongGuesses() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> memberService.changeOwnPassword(1L, "wrong-password", "Moi@12345"))
                    .isInstanceOf(BadRequestException.class);
        }

        // The right password is refused too once locked, or the lock only slows a guesser down by one try.
        assertThatThrownBy(() -> memberService.changeOwnPassword(1L, PASSWORD, "Moi@12345"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void aPasswordOverSeventyTwoBytesIsRefusedEvenUnderSeventyTwoCharacters() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        // 30 letters, 90 bytes: BCrypt would throw, and that used to be a 500.
        String vietnamese = "ặ".repeat(30);

        assertThatThrownBy(() -> memberService.resetPassword(ACTOR, Role.ADMIN, 1L, vietnamese))
                .isInstanceOf(BadRequestException.class);
        assertThat(passwordEncoder.matches(PASSWORD, member.getPasswordHash())).isTrue();
    }

    @Test
    void changeOwnPasswordIsNotLockedBySomeoneElsesFailedSignIns() {
        LoginThrottle throttle = new LoginThrottle();
        memberService = new MemberServiceImpl(memberRepository, Mappers.getMapper(MemberMapper.class),
                passwordEncoder, throttle, auditService, events);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                throttle.beginLogin(EMAIL, "203.0.113." + attempt);
            } catch (TooManyRequestsException ignored) {
                // A stranger's guesses at the sign-in form.
            }
        }

        memberService.changeOwnPassword(1L, PASSWORD, "Moi@12345");

        assertThat(passwordEncoder.matches("Moi@12345", member.getPasswordHash())).isTrue();
    }

    @Test
    void createIsRefusedBelowAdminInTheServiceItself() {
        CreateMemberRequest request = new CreateMemberRequest("Con cháu", "a@b.vn", "Matkhau@1", Role.ADMIN);

        // An EDITOR who reached this past the matcher could make themselves an ADMIN (§8.11 #12).
        assertThatThrownBy(() -> memberService.create(ACTOR, Role.EDITOR, request))
                .isInstanceOf(ForbiddenException.class);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void createStoresTheEmailLowerCasedAndRecordsTheAccount() {
        when(memberRepository.save(any(Member.class))).thenAnswer(call -> {
            Member saved = call.getArgument(0);
            saved.setId(40L);
            return saved;
        });

        MemberResponse created = memberService.create(
                ACTOR, Role.ADMIN, new CreateMemberRequest("Con cháu", " An@X.vn ", "Matkhau@1", Role.MEMBER));

        assertThat(created.email()).isEqualTo("an@x.vn");
        assertThat(created.role()).isEqualTo(Role.MEMBER);
        verify(memberRepository).existsByEmail("an@x.vn");
        verify(auditService).record(eq(AuditEntityType.MEMBER), eq(40L), eq(AuditAction.CREATE),
                eq(null), any(MemberSnapshot.class), eq(ACTOR), any());
    }

    @Test
    void updateChangesRoleAndEndsTheSessions() {
        Member editor = account(2L, Role.EDITOR);
        when(memberRepository.findById(2L)).thenReturn(Optional.of(editor));

        MemberResponse updated = memberService.update(
                ACTOR, Role.ADMIN, 2L, new UpdateMemberRequest("Biên tập", Role.MEMBER, true, "Thôi biên tập"));

        assertThat(updated.role()).isEqualTo(Role.MEMBER);
        // The old access token stops working at once, which is what the dialog tells the ADMIN (§8.12 #13).
        assertThat(editor.getCredentialVersion()).isEqualTo(1);
        verify(events).publishEvent(new CredentialsChanged(2L));
        verify(auditService).record(eq(AuditEntityType.MEMBER), eq(2L), eq(AuditAction.UPDATE),
                any(MemberSnapshot.class), any(MemberSnapshot.class), eq(ACTOR), eq("Thôi biên tập"));
    }

    @Test
    void updateOfTheNameAloneLeavesTheSessionsAlone() {
        Member editor = account(2L, Role.EDITOR);
        when(memberRepository.findById(2L)).thenReturn(Optional.of(editor));

        memberService.update(ACTOR, Role.ADMIN, 2L, new UpdateMemberRequest("Tên mới", Role.EDITOR, true, null));

        verify(events, never()).publishEvent(any());
    }

    @Test
    void anAdminCannotDisableOrDemoteThemselves() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.update(
                1L, Role.ADMIN, 1L, new UpdateMemberRequest("Quan tri vien", Role.ADMIN, false, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> memberService.update(
                1L, Role.ADMIN, 1L, new UpdateMemberRequest("Quan tri vien", Role.EDITOR, true, null)))
                .isInstanceOf(BadRequestException.class);
        assertThat(member.isActive()).isTrue();
        assertThat(member.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void theLastActiveAdminCannotBeDisabledByAnother() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        // The ADMIN rows are locked and counted in one read, so a second demotion cannot pass on a stale count.
        when(memberRepository.lockActiveByRole(Role.ADMIN)).thenReturn(List.of(member));

        assertThatThrownBy(() -> memberService.update(
                ACTOR, Role.ADMIN, 1L, new UpdateMemberRequest("Quan tri vien", Role.ADMIN, false, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ít nhất một trưởng tộc");
    }

    @Test
    void updateAndResetAreRefusedBelowAdmin() {
        assertThatThrownBy(() -> memberService.update(
                ACTOR, Role.EDITOR, 2L, new UpdateMemberRequest("X", Role.ADMIN, true, null)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> memberService.resetPassword(ACTOR, Role.EDITOR, 2L, "Moi@12345"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void searchListsAccountsForAnAdmin() {
        when(memberRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(member)));

        assertThat(memberService.search(PageRequest.of(0, 20), Role.ADMIN).getContent())
                .singleElement()
                .satisfies(response -> assertThat(response.email()).isEqualTo(EMAIL));
    }

    @Test
    void searchRefusesAnyoneButAnAdmin() {
        assertThatThrownBy(() -> memberService.search(PageRequest.of(0, 20), Role.EDITOR))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> memberService.search(PageRequest.of(0, 20), Role.MEMBER))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void resetPasswordSetsTheHashWithoutTheOldPassword() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        memberService.resetPassword(ACTOR, Role.ADMIN, 1L, "Moi@12345");

        assertThat(passwordEncoder.matches("Moi@12345", member.getPasswordHash())).isTrue();
        verify(events).publishEvent(new CredentialsChanged(1L));
    }

    @Test
    void resetPasswordFailsForAnUnknownAccount() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.resetPassword(ACTOR, Role.ADMIN, 99L, "Moi@12345"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void resetPasswordRefusesTheAdminsOwnAccount() {
        String oldHash = member.getPasswordHash();

        assertThatThrownBy(() -> memberService.resetPassword(1L, Role.ADMIN, 1L, "Moi@12345"))
                .isInstanceOf(BadRequestException.class);
        assertThat(member.getPasswordHash()).isEqualTo(oldHash);
    }

    /**
     * Builds an active account.
     *
     * @param id member id
     * @param role access level
     * @return the entity
     */
    private Member account(Long id, Role role) {
        Member account = new Member();
        account.setId(id);
        account.setFullName("Biên tập");
        account.setEmail("bientap@genealogy.vn");
        account.setPasswordHash(member.getPasswordHash());
        account.setRole(role);
        account.setActive(true);
        return account;
    }
}
