package dev.barboza.cofre.pix;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChavePixRepository extends JpaRepository<ChavePix, Long> {

    List<ChavePix> findByContaIdOrderByIdAsc(Long contaId);

    long countByContaId(Long contaId);

    Optional<ChavePix> findFirstByValorIn(Collection<String> valores);

    boolean existsByValor(String valor);

    void deleteByContaId(Long contaId);
}
