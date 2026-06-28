package com.firstpay.reporting.api;

import com.firstpay.reporting.dto.Reports;
import com.firstpay.reporting.infra.ReportStore;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {

    private final ReportStore store;

    public ReportsController(ReportStore store) {
        this.store = store;
    }

    @GetMapping("/summary")
    public Mono<Reports.SummaryStats> summary(@RequestHeader("X-Tenant-Id") UUID tenantId) {
        return store.summary(tenantId);
    }

    @GetMapping("/daily")
    public Flux<Reports.DailyStat> daily(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(defaultValue = "30") int days) {
        return store.daily(tenantId, days);
    }

    @GetMapping("/transactions")
    public Flux<Reports.TxView> transactions(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(defaultValue = "100") int limit) {
        return store.transactions(tenantId, limit);
    }

    @GetMapping(value = "/live-stats", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Reports.LiveStats>> liveStats(@RequestHeader("X-Tenant-Id") UUID tenantId) {
        return Flux.interval(Duration.ofSeconds(1))
            .concatMap(t -> store.liveStats(tenantId))
            .map(s -> ServerSentEvent.<Reports.LiveStats>builder().event("stats").data(s).build());
    }
}
