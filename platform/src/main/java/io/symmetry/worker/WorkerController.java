package io.symmetry.worker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.symmetry.api.WorkerAcceptanceView;
import io.symmetry.contract.Acceptance;
import io.symmetry.contract.AcceptanceRepository;
import io.symmetry.contract.Contract;
import io.symmetry.contract.ContractRepository;
import io.symmetry.identity.Actor;
import io.symmetry.identity.CurrentUserService;

@RestController
@RequestMapping("/worker")
@PreAuthorize("hasAnyRole('WORKER','ADMIN')")
public class WorkerController {

    private final CurrentUserService currentUser;
    private final AcceptanceRepository acceptances;
    private final ContractRepository contracts;

    public WorkerController(CurrentUserService currentUser, AcceptanceRepository acceptances,
            ContractRepository contracts) {
        this.currentUser = currentUser;
        this.acceptances = acceptances;
        this.contracts = contracts;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Actor actor = currentUser.require();
        List<Acceptance> mine = acceptances.findByWorkerIdOrderByAcceptedAtDesc(actor.workerId());
        long verified = mine.stream().filter(Acceptance::isVerified).count();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("acceptances", mine.size());
        summary.put("verified", verified);
        summary.put("pendingSubmissions", 0);
        summary.put("earningsPendingCents", 0);
        summary.put("evaluator", "offline");
        return summary;
    }

    @GetMapping("/acceptances")
    public List<WorkerAcceptanceView> acceptances() {
        Actor actor = currentUser.require();
        List<WorkerAcceptanceView> views = new ArrayList<>();
        for (Acceptance acceptance : acceptances.findByWorkerIdOrderByAcceptedAtDesc(actor.workerId())) {
            Contract contract = contracts.findById(acceptance.getContractId()).orElse(null);
            if (contract == null) {
                continue;
            }
            views.add(new WorkerAcceptanceView(acceptance.getId(), contract.getId(),
                    contract.getModuleId(), contract.getVersion(),
                    contract.getLifecycleState().name().toLowerCase(),
                    acceptance.getTaskDocumentHash(), acceptance.isVerified(),
                    acceptance.getAcceptedAt()));
        }
        return views;
    }

    @GetMapping("/submissions")
    public Map<String, Object> submissions() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of());
        body.put("note", "The evaluator is offline in this phase; submissions appear here once it is connected.");
        return body;
    }

    @GetMapping("/earnings")
    public Map<String, Object> earnings() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("currency", "USD");
        body.put("pendingCents", 0);
        body.put("paidCents", 0);
        body.put("items", List.of());
        return body;
    }
}
