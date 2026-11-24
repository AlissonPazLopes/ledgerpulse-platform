package dev.ledgerpulse.core.reconciliation.repository;

import dev.ledgerpulse.core.reconciliation.domain.ReconciliationReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReconciliationReportRepository extends JpaRepository<ReconciliationReport, UUID> {

    Page<ReconciliationReport> findAllByOrderByCreatedAtDescIdAsc(Pageable pageable);

    List<ReconciliationReport> findAllByOrderByCreatedAtDescIdAsc();
}
