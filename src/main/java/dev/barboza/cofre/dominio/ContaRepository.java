package dev.barboza.cofre.dominio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ContaRepository extends JpaRepository<Conta, Long> {

    Optional<Conta> findByNumero(String numero);

    List<Conta> findAllByOrderByNumeroBaseAsc();

    @Query("select coalesce(max(c.numeroBase), 0) from Conta c")
    int maiorNumeroBase();
}
