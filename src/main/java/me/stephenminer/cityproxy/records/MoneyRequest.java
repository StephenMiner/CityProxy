package me.stephenminer.cityproxy.records;

import java.util.UUID;

public record MoneyRequest(UUID requester, UUID target, double amount) {
}
