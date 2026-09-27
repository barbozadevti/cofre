package dev.barboza.cofre.caixinha;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaixinhaRepository extends JpaRepository<Caixinha, Long> {

    List<Caixinha> findByContaIdOrderByIdAsc(Long contaId);

    long countByContaId(Long contaId);

    @Query("select coalesce(sum(c.saldo), 0) from Caixinha c where c.conta.id = :contaId")
    BigDecimal totalDaConta(@Param("contaId") Long contaId);

    @Query("select coalesce(sum(c.saldo), 0) from Caixinha c")
    BigDecimal totalGeral();
}
