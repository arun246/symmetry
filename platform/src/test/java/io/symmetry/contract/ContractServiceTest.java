package io.symmetry.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import io.symmetry.build.JavaToolchain;
import io.symmetry.build.RustToolchain;
import io.symmetry.build.ToolchainRegistry;
import io.symmetry.identity.Actor;
import io.symmetry.identity.Role;
import io.symmetry.task.LifecycleState;
import io.symmetry.task.WorkKind;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link ContractService}: real validator/disclosure/signing, with
 * the database mocked. No Spring context and no Docker required.
 */
@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    private static final String VALID_PAYLOAD = """
            {
              "signature": {"name": "resize"},
              "invariants": ["dims_match", "alpha_preserved"],
              "resourceBudget": {"timeMs": 12, "peakMemMb": 8, "artifactKb": 64},
              "oracle": {"holdout": "hidden", "properties": "ref"},
              "scoring": {"gate": "all_pass"}
            }
            """;

    @Mock
    private ContractRepository contracts;

    @Mock
    private AcceptanceRepository acceptances;

    private final ObjectMapper mapper = new ObjectMapper();
    private ContractService service;

    @BeforeEach
    void setUp() {
        ToolchainRegistry registry =
                new ToolchainRegistry(List.of(new JavaToolchain(), new RustToolchain()));
        service = new ContractService(contracts, acceptances, registry,
                new ContractDocumentValidator(), new DisclosureService(), mapper, "honesty,scope");
    }

    private JsonNode payload() {
        return mapper.readTree(VALID_PAYLOAD);
    }

    private Contract sampleContract() {
        Contract contract = new Contract(UUID.randomUUID(), "module.image.resize", "1.0.0", "optimize",
                "java", "L1", WorkKind.COMPUTE, "task doc", "{}");
        contract.publish();
        return contract;
    }

    @Test
    void rejectsUnknownLanguageBeforeSaving() {
        ContractRequest request = new ContractRequest("m", "1.0.0", "optimize", "go", "L1",
                null, "task doc", payload());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unsupported language");
        verify(contracts, never()).save(any());
    }

    @Test
    void rejectsInvalidPayload() {
        JsonNode bad = mapper.readTree("{\"signature\": {}}");
        ContractRequest request = new ContractRequest("m", "1.0.0", "optimize", "java", "L1",
                null, "task doc", bad);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid contract document");
    }

    @Test
    void rejectsDuplicatePublish() {
        when(contracts.findByModuleIdAndVersion("module.image.resize", "1.0.0"))
                .thenReturn(Optional.of(sampleContract()));

        ContractRequest request = new ContractRequest("module.image.resize", "1.0.0", "optimize",
                "java", "L1", null, "task doc", payload());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void mapsLostRaceToConflict() {
        when(contracts.findByModuleIdAndVersion(any(), any())).thenReturn(Optional.empty());
        when(contracts.save(any(Contract.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        ContractRequest request = new ContractRequest("module.image.resize", "1.0.0", "optimize",
                "java", "L1", null, "task doc", payload());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createsAndNormalisesFields() {
        when(contracts.findByModuleIdAndVersion(any(), any())).thenReturn(Optional.empty());
        when(contracts.save(any(Contract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContractRequest request = new ContractRequest("module.image.resize", "1.0.0", "optimize",
                "JAVA", "L1", "service", "task doc", payload());

        ContractView view = service.create(request);

        assertThat(view.language()).isEqualTo("java");
        assertThat(view.workKind()).isEqualTo("service");
        assertThat(view.lifecycleState()).isEqualTo("draft");
        assertThat(view.taskKind()).isEqualTo("optimize");
        assertThat(view.payload().has("invariants")).isTrue();
    }

    @Test
    void acceptRejectsMissingAgreements() {
        Contract contract = sampleContract();
        when(contracts.findById(contract.getId())).thenReturn(Optional.of(contract));

        AcceptRequest request = new AcceptRequest("worker-1", Set.of("honesty"), "pub", "sig");

        assertThatThrownBy(() -> service.accept(contract.getId(), request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Missing required agreements");
        verify(acceptances, never()).save(any());
    }

    @Test
    void acceptRejectsBadSignature() {
        Contract contract = sampleContract();
        when(contracts.findById(contract.getId())).thenReturn(Optional.of(contract));

        AcceptRequest request = new AcceptRequest("worker-1", Set.of("honesty", "scope"),
                "not-base64", "not-base64");

        assertThatThrownBy(() -> service.accept(contract.getId(), request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Signature does not match");
    }

    @Test
    void acceptStoresVerifiedAcceptanceAndAdvancesLifecycle() throws Exception {
        Contract contract = sampleContract();
        when(contracts.findById(contract.getId())).thenReturn(Optional.of(contract));
        when(acceptances.saveAndFlush(any(Acceptance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String hash = MessageSigner.sha256("task doc");
        String message = MessageSigner.canonicalAcceptMessage(
                "module.image.resize", "1.0.0", hash);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(message.getBytes(StandardCharsets.UTF_8));
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String signature = Base64.getEncoder().encodeToString(signer.sign());

        Acceptance saved = service.accept(contract.getId(),
                new AcceptRequest("worker-1", Set.of("honesty", "scope"), publicKey, signature));

        assertThat(saved.isVerified()).isTrue();
        assertThat(saved.getTaskDocumentHash()).isEqualTo(hash);
        assertThat(saved.getWorkerId()).isEqualTo("worker-1");
        assertThat(saved.getAgreements()).contains("honesty").contains("scope");
        assertThat(contract.getLifecycleState()).isEqualTo(LifecycleState.ACCEPTED);
        verify(contracts).save(contract);
    }

    private Acceptance sampleAcceptance(Contract contract, String workerId) {
        return new Acceptance(UUID.randomUUID(), contract.getId(), workerId, "hash", "sig", "pub",
                true, "[\"honesty\",\"scope\"]");
    }

    private static Actor workerActor(String email) {
        return new Actor(UUID.randomUUID(), email, "Worker", Role.WORKER, null, null);
    }

    private void givenAcceptance(Contract contract, Acceptance acceptance) {
        when(acceptances.findByContractIdAndWorkerId(contract.getId(), acceptance.getWorkerId()))
                .thenReturn(Optional.of(acceptance));
    }

    @Test
    void withdrawNullifiesTheAcceptanceButKeepsTheSignedProof() {
        Contract contract = sampleContract();
        Acceptance acceptance = sampleAcceptance(contract, "worker@symmetry.local");
        givenAcceptance(contract, acceptance);
        when(acceptances.save(any(Acceptance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        LifecycleState lifecycleBefore = contract.getLifecycleState();

        WithdrawalView view = service.withdraw(contract.getId(),
                new WithdrawRequest(List.of("task-unclear", "cannot-test"), "no reference suite"),
                workerActor("worker@symmetry.local"));

        assertThat(view.status()).isEqualTo("WITHDRAWN");
        assertThat(view.reasons()).containsExactly("task-unclear", "cannot-test");
        assertThat(view.comment()).isEqualTo("no reference suite");
        assertThat(view.withdrawnAt()).isNotNull();

        assertThat(acceptance.isWithdrawn()).isTrue();
        assertThat(acceptance.getSignature()).isEqualTo("sig");
        assertThat(acceptance.getWorkerPublicKey()).isEqualTo("pub");
        assertThat(acceptance.getTaskDocumentHash()).isEqualTo("hash");
        assertThat(acceptance.getAgreements()).contains("honesty").contains("scope");
        // Nullifying one worker's acceptance never rewrites the contract lifecycle.
        assertThat(contract.getLifecycleState()).isEqualTo(lifecycleBefore);
        verify(acceptances).save(acceptance);
    }

    @Test
    void withdrawRequiresARecordedReason() {
        Contract contract = sampleContract();
        Acceptance acceptance = sampleAcceptance(contract, "worker@symmetry.local");
        givenAcceptance(contract, acceptance);

        assertThatThrownBy(() -> service.withdraw(contract.getId(),
                new WithdrawRequest(List.of("   "), null), workerActor("worker@symmetry.local")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("at least one reason");
        verify(acceptances, never()).save(any());
    }

    @Test
    void withdrawRejectsUnknownReasonCodes() {
        Contract contract = sampleContract();
        Acceptance acceptance = sampleAcceptance(contract, "worker@symmetry.local");
        givenAcceptance(contract, acceptance);

        assertThatThrownBy(() -> service.withdraw(contract.getId(),
                new WithdrawRequest(List.of("bored"), null), workerActor("worker@symmetry.local")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown reason");
        verify(acceptances, never()).save(any());
    }

    @Test
    void withdrawRequiresACommentWhenOtherIsSelected() {
        Contract contract = sampleContract();
        Acceptance acceptance = sampleAcceptance(contract, "worker@symmetry.local");
        givenAcceptance(contract, acceptance);

        assertThatThrownBy(() -> service.withdraw(contract.getId(),
                new WithdrawRequest(List.of("other"), "   "), workerActor("worker@symmetry.local")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("other");
        verify(acceptances, never()).save(any());
    }

    @Test
    void withdrawIsRejectedTwice() {
        Contract contract = sampleContract();
        Acceptance acceptance = sampleAcceptance(contract, "worker@symmetry.local");
        acceptance.withdraw("[\"not-enough-time\"]", null);
        givenAcceptance(contract, acceptance);

        assertThatThrownBy(() -> service.withdraw(contract.getId(),
                new WithdrawRequest(List.of("cannot-test"), null), workerActor("worker@symmetry.local")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already nullified");
    }

    @Test
    void withdrawOnlyTouchesTheCallersOwnAcceptance() {
        Contract contract = sampleContract();
        when(acceptances.findByContractIdAndWorkerId(contract.getId(), "someone-else@symmetry.local"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.withdraw(contract.getId(),
                new WithdrawRequest(List.of("cannot-test"), null), workerActor("someone-else@symmetry.local")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No acceptance");
        verify(acceptances, never()).save(any());
    }
}
