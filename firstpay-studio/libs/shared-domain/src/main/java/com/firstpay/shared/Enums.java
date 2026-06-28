package com.firstpay.shared;

/** Enums & constantes partagés du domaine FirstPay. */
public final class Enums {
    private Enums() {}

    public enum TransactionStatus { PENDING, SUCCESS, FAILED, REFUNDED }
    public enum PaymentMethod { orange, mtn, card, transfer }
    public enum InterfaceStatus { brouillon, actif }
    public enum AmountType { fixed, preset, free }
    public enum Role {
        bank_admin, bank_cashier,
        partner_admin, partner_manager, partner_accountant, partner_viewer
    }

    public static final String DEFAULT_CURRENCY = "XAF";
}
