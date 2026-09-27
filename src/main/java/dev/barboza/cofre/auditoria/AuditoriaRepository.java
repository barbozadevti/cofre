package dev.barboza.cofre.auditoria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaRepository extends JpaRepository<EventoAuditoria, Long> {

    Page<EventoAuditoria> findAllByOrderByDataHoraDescIdDesc(Pageable pagina);
}
