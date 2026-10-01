package dev.barboza.cofre.salario;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PortabilidadeRepository extends JpaRepository<PortabilidadeSalario, Long> {

    List<PortabilidadeSalario> findByContaClienteIdOrderBySolicitadaEmDesc(Long clienteId);

    List<PortabilidadeSalario> findByCanceladaEmIsNull();

    long countByCanceladaEmIsNull();
}
