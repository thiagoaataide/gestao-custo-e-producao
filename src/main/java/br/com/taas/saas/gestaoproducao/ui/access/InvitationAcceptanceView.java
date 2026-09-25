package br.com.taas.saas.gestaoproducao.ui.access;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.VaadinServletResponse;

import br.com.taas.saas.gestaoproducao.platform.access.security.SupabaseAuthenticationToken;
import br.com.taas.saas.gestaoproducao.platform.access.security.SupabaseInvitationAuthService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.AcceptInvitationCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationAcceptanceService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationOnboardingQueryService;
import br.com.taas.saas.gestaoproducao.platform.access.application.model.AccessTokenContext;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

@Route("invitations")
@PageTitle("Aceitar convite | Gestão de Produção")
@AnonymousAllowed
public final class InvitationAcceptanceView extends VerticalLayout implements HasUrlParameter<String> {

    private static final String UNAVAILABLE_MESSAGE =
            "Não foi possível continuar com este convite. Solicite um novo link à administração.";
    private static final String MISMATCH_MESSAGE =
            "Esta sessão pertence a outro e-mail. Troque para a conta convidada para continuar.";

    private final InvitationAcceptanceService invitationAcceptanceService;
    private final InvitationOnboardingQueryService onboardingQueryService;
    private final SupabaseInvitationAuthService invitationAuthService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    private final Paragraph resultMessage = new Paragraph("Carregando convite...");
    private final TextField invitedEmailField = new TextField("E-mail do convite");
    private final PasswordField signupPasswordField = new PasswordField("Crie uma senha");
    private final Button signupButton = new Button("Criar conta e enviar código");
    private final PasswordField loginPasswordField = new PasswordField("Senha da conta existente");
    private final Button loginButton = new Button("Entrar com conta existente");
    private final TextField otpField = new TextField("Código recebido por e-mail");
    private final Button verifyOtpButton = new Button("Verificar e-mail");
    private final Button resendOtpButton = new Button("Enviar ou reenviar código");
    private final Button switchAccountButton = new Button("Trocar de conta e continuar convite");
    private final Button confirmButton = new Button("Confirmar aceite do convite");

    private String token;
    private String invitedEmail;

    public InvitationAcceptanceView(
            InvitationAcceptanceService invitationAcceptanceService,
            InvitationOnboardingQueryService onboardingQueryService,
            SupabaseInvitationAuthService invitationAuthService,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository) {
        this.invitationAcceptanceService = Objects.requireNonNull(invitationAcceptanceService);
        this.onboardingQueryService = Objects.requireNonNull(onboardingQueryService);
        this.invitationAuthService = Objects.requireNonNull(invitationAuthService);
        this.authenticationManager = Objects.requireNonNull(authenticationManager);
        this.securityContextRepository = Objects.requireNonNull(securityContextRepository);

        setWidthFull();
        setMaxWidth("42rem");
        setMargin(true);
        setSpacing(true);

        invitedEmailField.setReadOnly(true);
        signupPasswordField.setMinLength(10);
        signupPasswordField.setHelperText("Use pelo menos 10 caracteres.");
        otpField.setMaxLength(12);
        confirmButton.setEnabled(false);
        hideOnboardingControls();

        signupButton.addClickListener(event -> signUp());
        loginButton.addClickListener(event -> signInExistingAccount());
        verifyOtpButton.addClickListener(event -> verifyOtp());
        resendOtpButton.addClickListener(event -> resendOtp());
        switchAccountButton.addClickListener(event -> switchAccount());
        confirmButton.addClickListener(event -> acceptInvitation());

        add(new H1("Convite de acesso"), resultMessage,
                invitedEmailField,
                signupPasswordField, signupButton,
                loginPasswordField, loginButton,
                otpField, verifyOtpButton, resendOtpButton,
                switchAccountButton, confirmButton);
    }

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        token = parameter == null || parameter.isBlank() ? null : parameter;
        invitedEmail = onboardingQueryService.invitedEmail(token, Instant.now()).orElse(null);
        invitedEmailField.setValue(invitedEmail == null ? "" : invitedEmail);
        if (invitedEmail == null) {
            hideOnboardingControls();
            resultMessage.setText(UNAVAILABLE_MESSAGE);
            return;
        }
        renderCurrentState();
    }

    private void renderCurrentState() {
        hideOnboardingControls();
        Optional<SupabaseAuthenticationToken> current = currentAuthentication();
        if (current.isEmpty()) {
            showAccountOptions();
            resultMessage.setText(
                    "Este convite é para " + invitedEmail + ". Entre com essa conta ou crie uma conta para receber o código de verificação.");
            return;
        }

        Optional<br.com.taas.saas.gestaoproducao.platform.access.application.model.AuthenticatedIdentityProfile> profile =
                invitationAuthService.verifiedProfile(current.orElseThrow());
        if (profile.isPresent() && profile.orElseThrow().email().value().equals(invitedEmail)) {
            confirmButton.setEnabled(true);
            confirmButton.setVisible(true);
            resultMessage.setText("E-mail confirmado para " + invitedEmail + ". Confirme para aceitar o convite.");
            return;
        }
        if (profile.isPresent()) {
            switchAccountButton.setVisible(true);
            resultMessage.setText(MISMATCH_MESSAGE);
            return;
        }
        showOtpControls();
        switchAccountButton.setVisible(true);
        resultMessage.setText("Confirme o e-mail convidado antes de aceitar. Você pode receber um código ou trocar de conta.");
    }

    private void showAccountOptions() {
        invitedEmailField.setVisible(true);
        signupPasswordField.setVisible(true);
        signupButton.setVisible(true);
        loginPasswordField.setVisible(true);
        loginButton.setVisible(true);
        resendOtpButton.setVisible(true);
    }

    private void showOtpControls() {
        invitedEmailField.setVisible(true);
        loginPasswordField.setVisible(true);
        loginButton.setVisible(true);
        otpField.setVisible(true);
        verifyOtpButton.setVisible(true);
        resendOtpButton.setVisible(true);
    }

    private void hideOnboardingControls() {
        invitedEmailField.setVisible(false);
        signupPasswordField.setVisible(false);
        signupButton.setVisible(false);
        loginPasswordField.setVisible(false);
        loginButton.setVisible(false);
        otpField.setVisible(false);
        verifyOtpButton.setVisible(false);
        resendOtpButton.setVisible(false);
        switchAccountButton.setVisible(false);
        confirmButton.setVisible(false);
        confirmButton.setEnabled(false);
    }

    private void signUp() {
        if (!hasValidInvitation() || signupPasswordField.getValue() == null
                || signupPasswordField.getValue().length() < 10) {
            resultMessage.setText("Informe uma senha com pelo menos 10 caracteres.");
            return;
        }
        signupButton.setEnabled(false);
        try {
            invitationAuthService.signUp(invitedEmail, signupPasswordField.getValue());
            showOtpControls();
            resultMessage.setText("Se o e-mail ainda não estiver verificado, digite o código enviado para " + invitedEmail + ". Se sua conta já estiver confirmada, entre com ela.");
        } catch (RuntimeException exception) {
            showOtpControls();
            resultMessage.setText("Não foi possível iniciar o cadastro. Entre com a conta existente ou solicite um código para verificar o e-mail.");
        } finally {
            signupButton.setEnabled(true);
            signupPasswordField.clear();
        }
    }

    private void resendOtp() {
        if (!hasValidInvitation()) {
            showUnavailableResult();
            return;
        }
        try {
            invitationAuthService.resendSignupOtp(invitedEmail);
            showOtpControls();
            resultMessage.setText("Se a conta puder receber verificação, enviaremos um código para " + invitedEmail + ". Se o e-mail já estiver confirmado, entre com sua senha.");
        } catch (RuntimeException exception) {
            showOtpControls();
            resultMessage.setText("Não foi possível confirmar o envio do código. Você pode entrar com a conta existente ou tentar novamente.");
        }
    }

    private void verifyOtp() {
        if (!hasValidInvitation() || otpField.getValue() == null || otpField.getValue().isBlank()) {
            resultMessage.setText("Informe o código recebido por e-mail.");
            return;
        }
        verifyOtpButton.setEnabled(false);
        try {
            SupabaseAuthenticationToken verified = invitationAuthService.verifySignupOtp(
                    invitedEmail, otpField.getValue().trim());
            saveAuthentication(verified);
            otpField.clear();
            renderCurrentState();
        } catch (RuntimeException exception) {
            showOtpControls();
            resultMessage.setText("Não foi possível verificar o código. Confira o código ou solicite outro.");
        } finally {
            verifyOtpButton.setEnabled(true);
        }
    }

    private void signInExistingAccount() {
        if (!hasValidInvitation() || loginPasswordField.getValue() == null
                || loginPasswordField.getValue().isBlank()) {
            resultMessage.setText("Informe a senha da conta que recebeu o convite.");
            return;
        }
        loginButton.setEnabled(false);
        try {
            Authentication authenticated = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            invitedEmail, loginPasswordField.getValue()));
            if (!(authenticated instanceof SupabaseAuthenticationToken supabase)) {
                throw new IllegalStateException("unexpected authentication type");
            }
            saveAuthentication(supabase);
            loginPasswordField.clear();
            renderCurrentState();
        } catch (RuntimeException exception) {
            showAccountOptions();
            resultMessage.setText("Não foi possível entrar com essa conta. Confira a senha ou solicite um código para verificar o e-mail.");
        } finally {
            loginButton.setEnabled(true);
            loginPasswordField.clear();
        }
    }

    private void switchAccount() {
        if (!hasValidInvitation()) {
            showUnavailableResult();
            return;
        }
        currentAuthentication().ifPresent(authentication -> {
            try {
                invitationAuthService.signOut(authentication.getJwt().getTokenValue());
            } catch (RuntimeException ignored) {
                // The local session is cleared even if the provider cannot confirm revocation.
            }
        });
        SecurityContextHolder.clearContext();
        try {
            VaadinServletRequest request = VaadinServletRequest.getCurrent();
            if (request != null) {
                jakarta.servlet.http.HttpSession session =
                        request.getHttpServletRequest().getSession(false);
                if (session != null) {
                    session.removeAttribute(
                            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                }
            }
        } catch (RuntimeException ignored) {
            // An absent/expired session is already signed out locally.
        }
        renderCurrentState();
        resultMessage.setText("Sessão encerrada. O convite para " + invitedEmail + " continua aberto; entre ou cadastre essa conta.");
    }

    private void saveAuthentication(SupabaseAuthenticationToken authentication) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        VaadinServletRequest request = VaadinServletRequest.getCurrent();
        VaadinServletResponse response = VaadinServletResponse.getCurrent();
        if (request != null && response != null) {
            request.getHttpServletRequest().changeSessionId();
            securityContextRepository.saveContext(
                    context,
                    request.getHttpServletRequest(),
                    response.getHttpServletResponse());
        }
    }

    private Optional<SupabaseAuthenticationToken> currentAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof SupabaseAuthenticationToken supabase
                && supabase.isAuthenticated()) {
            return Optional.of(supabase);
        }
        return Optional.empty();
    }

    void acceptInvitation() {
        Optional<SupabaseAuthenticationToken> authentication = currentAuthentication();
        if (!hasValidInvitation() || authentication.isEmpty()) {
            showUnavailableResult();
            return;
        }
        confirmButton.setEnabled(false);
        try {
            SupabaseAuthenticationToken supabaseAuthentication = authentication.orElseThrow();
            Optional<br.com.taas.saas.gestaoproducao.platform.access.application.model.AuthenticatedIdentityProfile> profile =
                    invitationAuthService.verifiedProfile(supabaseAuthentication);
            if (profile.isEmpty() || !profile.orElseThrow().email().value().equals(invitedEmail)) {
                resultMessage.setText(MISMATCH_MESSAGE);
                switchAccountButton.setVisible(true);
                return;
            }
            ExternalSubject subject = (ExternalSubject) supabaseAuthentication.getPrincipal();
            invitationAcceptanceService.acceptInvitation(new AcceptInvitationCommand(
                    token,
                    new AccessTokenContext(subject, supabaseAuthentication.getJwt().getTokenValue()),
                    Instant.now()));
            resultMessage.setText("Convite aceito. Seu acesso está ativo.");
        } catch (RuntimeException exception) {
            showUnavailableResult();
        }
    }

    private boolean hasValidInvitation() {
        return token != null && invitedEmail != null;
    }

    private void showUnavailableResult() {
        hideOnboardingControls();
        resultMessage.setText(UNAVAILABLE_MESSAGE);
    }
}
