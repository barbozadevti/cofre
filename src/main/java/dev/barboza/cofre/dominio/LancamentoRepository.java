package dev.barboza.cofre.dominio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {

    List<Lancamento> findByContaIdAndDataHoraGreaterThanEqualAndDataHoraLessThanOrderByDataHoraAscIdAsc(
            Long contaId, Instant inicio, Instant fim);

    /** Último lançamento antes do período, para saber o saldo inicial do extrato. */
    Optional<Lancamento> findFirstByContaIdAndDataHoraLessThanOrderByDataHoraDescIdDesc(Long contaId, Instant antes);
}
