package dev.barboza.cofre.cartao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CartaoRepository extends JpaRepository<Cartao, Long> {

    Optional<Cartao> findByContaId(Long contaId);

    boolean existsByNumero(String numero);
}
