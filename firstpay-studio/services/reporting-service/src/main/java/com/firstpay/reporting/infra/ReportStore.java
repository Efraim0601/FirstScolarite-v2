package com.firstpay.reporting.infra;

import com.firstpay.reporting.dto.Reports;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Repository
public class ReportStore {

    private final DatabaseClient db;

    public ReportStore(DatabaseClient db) {
        this.db = db;
    }

    public Mono<Reports.SummaryStats> summary(UUID tenantId) {
        return db.sql("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE status = 'SUCCESS') AS ok,
                       count(*) FILTER (WHERE status = 'FAILED') AS ko,
                       coalesce(sum(amount) FILTER (WHERE status = 'SUCCESS'), 0) AS amt
                FROM transaction_projection WHERE tenant_id = :t
                """)
            .bind("t", tenantId)
            .map(r -> {
                long total = num(r.get("total"));
                long ok = num(r.get("ok"));
                long ko = num(r.get("ko"));
                BigDecimal amt = r.get("amt", BigDecimal.class);
                double rate = total == 0 ? 0 : Math.round((ok * 1000.0) / total) / 10.0;
                return new Reports.SummaryStats(total, ok, ko, amt != null ? amt : BigDecimal.ZERO, rate);
            })
            .one()
            .switchIfEmpty(fallbackSummary(tenantId));
    }

    /** Fallback : lit la table transactions si les projections ne sont pas encore alimentées. */
    private Mono<Reports.SummaryStats> fallbackSummary(UUID tenantId) {
        return db.sql("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE status = 'SUCCESS') AS ok,
                       count(*) FILTER (WHERE status = 'FAILED') AS ko,
                       coalesce(sum(amount) FILTER (WHERE status = 'SUCCESS'), 0) AS amt
                FROM transactions WHERE tenant_id = :t
                """)
            .bind("t", tenantId)
            .map(r -> {
                long total = num(r.get("total"));
                long ok = num(r.get("ok"));
                long ko = num(r.get("ko"));
                BigDecimal amt = r.get("amt", BigDecimal.class);
                double rate = total == 0 ? 0 : Math.round((ok * 1000.0) / total) / 10.0;
                return new Reports.SummaryStats(total, ok, ko, amt != null ? amt : BigDecimal.ZERO, rate);
            })
            .one()
            .defaultIfEmpty(new Reports.SummaryStats(0, 0, 0, BigDecimal.ZERO, 0));
    }

    public Flux<Reports.DailyStat> daily(UUID tenantId, int days) {
        return db.sql("""
                SELECT day, tx_count, success_count, failed_count, amount_total
                FROM tx_stats_daily
                WHERE tenant_id = :t AND day >= current_date - :days
                ORDER BY day DESC
                """)
            .bind("t", tenantId).bind("days", days)
            .map(r -> new Reports.DailyStat(
                r.get("day", LocalDate.class),
                num(r.get("tx_count")),
                num(r.get("success_count")),
                num(r.get("failed_count")),
                r.get("amount_total", BigDecimal.class)
            ))
            .all();
    }

    public Flux<Reports.TxView> transactions(UUID tenantId, int limit) {
        return db.sql("""
                SELECT id, amount, currency, method, status, created_at, processed_at
                FROM transaction_projection
                WHERE tenant_id = :t ORDER BY created_at DESC LIMIT :n
                """)
            .bind("t", tenantId).bind("n", limit)
            .map(r -> new Reports.TxView(
                r.get("id", UUID.class),
                r.get("amount", BigDecimal.class),
                r.get("currency", String.class),
                r.get("method", String.class),
                r.get("status", String.class),
                r.get("created_at", java.time.Instant.class),
                r.get("processed_at", java.time.Instant.class)
            ))
            .all()
            .switchIfEmpty(Flux.defer(() -> db.sql("""
                    SELECT id, amount, currency, method, status, created_at, processed_at
                    FROM transactions WHERE tenant_id = :t ORDER BY created_at DESC LIMIT :n
                    """)
                .bind("t", tenantId).bind("n", limit)
                .map(r -> new Reports.TxView(
                    r.get("id", UUID.class),
                    r.get("amount", BigDecimal.class),
                    r.get("currency", String.class),
                    r.get("method", String.class),
                    r.get("status", String.class),
                    r.get("created_at", java.time.Instant.class),
                    r.get("processed_at", java.time.Instant.class)
                )).all()));
    }

    public Mono<Reports.LiveStats> liveStats(UUID tenantId) {
        return db.sql("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE status = 'SUCCESS') AS ok
                FROM transactions
                WHERE tenant_id = :t AND created_at > now() - interval '1 minute'
                """)
            .bind("t", tenantId)
            .map(r -> {
                long total = num(r.get("total"));
                long ok = num(r.get("ok"));
                double rate = total == 0 ? 100 : Math.round((ok * 1000.0) / total) / 10.0;
                return new Reports.LiveStats(total, rate, 45);
            })
            .one()
            .defaultIfEmpty(new Reports.LiveStats(0, 100, 45));
    }

    private static long num(Object v) { return v == null ? 0 : ((Number) v).longValue(); }
}
