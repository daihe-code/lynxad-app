package com.lynx.ad.modules.billing.api.dto;

public record BillItemView(
        String id,
        String billDate,
        String title,
        int amountFen,
        double amountYuan,
        String amountNote,
        String payMethod,
        String status,
        boolean canDownloadInvoice) {}
