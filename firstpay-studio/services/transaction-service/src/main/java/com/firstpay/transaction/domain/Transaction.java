package com.firstpay.transaction.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root Transaction. Persistance via {@link TransactionStore} (DatabaseClient),
 * car la table est partitionnée avec une clé primaire composite (id, created_at) — cas
 * mal géré par les repositories CRUD R2DBC.
 */
public class Transaction {

    private UUID id;
    private UUID tenantId;
    private UUID interfaceId;
    private String externalRef;
    private BigDecimal amount;
    private String currency;
    private String status;       // PENDING / SUCCESS / FAILED / REFUNDED
    private String type;
    private String method;       // orange / mtn / card / transfer
    private String idempotencyKey;
    private java.util.Map<String, Object> metadata;
    private Instant createdAt;
    private Instant processedAt;

    public static Transaction create(UUID tenantId, String externalRef, BigDecimal amount,
                                     String currency, String type, String method, String idempotencyKey) {
        Transaction t = new Transaction();
        t.id = UUID.randomUUID();
        t.tenantId = tenantId;
        t.externalRef = externalRef;
        t.amount = amount;
        t.currency = currency != null ? currency : "XAF";
        t.type = type;
        t.method = method;
        t.idempotencyKey = idempotencyKey;
        t.status = "PENDING";
        t.createdAt = Instant.now();
        return t;
    }

    public void markProcessed() { this.status = "SUCCESS"; this.processedAt = Instant.now(); }
    public void markFailed() { this.status = "FAILED"; this.processedAt = Instant.now(); }
    public void markRefunded() { this.status = "REFUNDED"; this.processedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getInterfaceId() { return interfaceId; }
    public void setInterfaceId(UUID interfaceId) { this.interfaceId = interfaceId; }
    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public java.util.Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(java.util.Map<String, Object> metadata) { this.metadata = metadata; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
