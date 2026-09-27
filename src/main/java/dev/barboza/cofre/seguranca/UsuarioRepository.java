package dev.barboza.cofre.seguranca;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Procura pelo e-mail ou, para clientes, pelo CPF. */
    @Query("select u from Usuario u left join u.cliente c where u.login = :login or c.cpf = :cpf")
    Optional<Usuario> porLoginOuCpf(@Param("login") String login, @Param("cpf") String cpf);

    Optional<Usuario> findByClienteId(Long clienteId);
}
