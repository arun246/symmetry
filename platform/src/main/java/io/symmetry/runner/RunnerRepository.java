package io.symmetry.runner;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RunnerRepository extends JpaRepository<Runner, UUID> {

    Optional<Runner> findByPublicKey(String publicKey);
}
