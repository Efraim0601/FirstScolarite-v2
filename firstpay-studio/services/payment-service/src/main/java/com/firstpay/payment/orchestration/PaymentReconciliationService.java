package com.firstpay.payment.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstpay.payment.connector.trustpayway.TrustPayWayClient;
import com.firstpay.payment.dto.AggregatorConfig;
import com.firstpay.payment.dto.PaymentResult;
import com.firstpay.payment.dto.PendingPayment;
import com.firstpay.payment.infra.AggregatorConfigProvider;
import com.firstpay.payment.infra.PendingPaymentStore;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.util.UUID;

/**
 * Réconciliation des paiements TrustPayWay : interroge {@code get-status} pour les
 * transactions INITIATED / PENDING et publie le résultat final sur Kafka.
 */
@Service
public class PaymentReconciliationService {

    private static final String TOPIC_OK = "transactions.processed";
    private static final String TOPIC_KO = "transactions.failed";

    private final PendingPaymentStore pendingStore;
    private final AggregatorConfigProvider config;
    private final TrustPayWayClient tpw;
    private final KafkaSender<String, String> sender;
    private final ObjectMapper json;

    public PaymentReconciliationService(PendingPaymentStore pendingStore, AggregatorConfigProvider config,
                                        TrustPayWayClient tpw, KafkaSender<String, String> sender,
                                        ObjectMapper json) {
        this.pendingStore = pendingStore;
        this.config = config;
        this.tpw = tpw;
        this.sender = sender;
        this.json = json;
    }

    @Scheduled(fixedDelayString = "${firstpay.payment.reconcile-interval-ms:30000}")
    public void reconcilePending() {
        config.get()
            .filter(AggregatorConfig::isReady)
            .flatMapMany(cfg -> pendingStore.all().flatMap(p -> checkOne(p, cfg)))
            .subscribe();
    }

    /** Traite une notification webhook TrustPayWay (complément à la réconciliation). */
    public Mono<Void> handleWebhook(String network, UUID firstpayId, String status, String description) {
        return pendingStore.all()
            .filter(p -> p.firstpayId().equals(firstpayId))
            .next()
            .flatMap(p -> finalizePayment(p, mapStatus(status), description))
            .then();
    }

    private Mono<Void> checkOne(PendingPayment p, AggregatorConfig cfg) {
        return tpw.getStatus(p.network(), p.aggregatorTxId(), cfg)
            .flatMap(status -> switch (status.toUpperCase()) {
                case "SUCCESSFUL" -> finalizePayment(p, true, null);
                case "FAILED" -> finalizePayment(p, false, "Échec agrégateur");
                default -> Mono.empty();
            });
    }

    private Mono<Void> finalizePayment(PendingPayment p, boolean success, String reason) {
        PaymentResult result = success
            ? new PaymentResult(p.firstpayId(), p.tenantId(), p.amount(), "SUCCESS", null, p.aggregatorTxId())
            : new PaymentResult(p.firstpayId(), p.tenantId(), p.amount(), "FAILED",
                reason != null ? reason : "Paiement refusé", p.aggregatorTxId());
        return publish(result).then(pendingStore.remove(p.firstpayId()));
    }

    private static boolean mapStatus(String status) {
        return "SUCCESSFUL".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status);
    }

    private Mono<Void> publish(PaymentResult result) {
        String topic = result.isSuccess() ? TOPIC_OK : TOPIC_KO;
        String payload = write(result);
        SenderRecord<String, String, UUID> out = SenderRecord.create(
            new ProducerRecord<>(topic, result.id().toString(), payload), result.id());
        return sender.send(Mono.just(out)).then();
    }

    private String write(PaymentResult r) {
        try { return json.writeValueAsString(r); } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
