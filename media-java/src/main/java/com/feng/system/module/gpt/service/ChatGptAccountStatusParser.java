package com.feng.system.module.gpt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ChatGptAccountStatusParser {
    private final ObjectMapper objectMapper;

    public ParsedAccount parse(JsonNode root, TokenClaims claims) {
        JsonNode accountContainer = selectAccount(root);
        JsonNode account = accountContainer.path("account");
        JsonNode entitlement = accountContainer.path("entitlement");
        String planType = text(account, "plan_type", "unknown");
        String subscriptionPlan = text(entitlement, "subscription_plan", "");
        Eligibility eligibility = determineEligibility(accountContainer, planType, subscriptionPlan);

        boolean deactivated = account.path("is_deactivated").asBoolean(false);
        boolean accessible = accountContainer.path("can_access_with_session").asBoolean(true);
        String status = deactivated ? "DEACTIVATED" : accessible ? "ACTIVE" : "INACCESSIBLE";
        String accountId = text(account, "account_id", "");
        if (accountId.isBlank()) {
            throw new BusinessException("状态响应中缺少 account_id");
        }

        return new ParsedAccount(
                accountId,
                claims.userId(),
                claims.email(),
                firstNonBlank(text(account, "name", ""), claims.name()),
                claims.expiresAt(),
                planType,
                subscriptionPlan,
                entitlement.path("has_active_subscription").asBoolean(false),
                entitlement.path("is_active_subscription_gratis").asBoolean(false),
                status,
                eligibility.status(),
                eligibility.reason(),
                json(accountContainer.path("eligible_offers")),
                json(accountContainer.path("eligible_promo_campaigns")),
                json(root)
        );
    }

    private JsonNode selectAccount(JsonNode root) {
        JsonNode accounts = root.path("accounts");
        if (!accounts.isObject() || accounts.isEmpty()) {
            throw new BusinessException("状态响应中没有账号数据");
        }

        JsonNode ordering = root.path("account_ordering");
        String preferredId = ordering.isTextual() ? ordering.asText() :
                ordering.isArray() && !ordering.isEmpty() ? ordering.get(0).asText("") : "";
        if (!preferredId.isBlank() && accounts.has(preferredId)) {
            return accounts.get(preferredId);
        }
        if (accounts.has("default")) {
            return accounts.get("default");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = accounts.fields();
        return fields.next().getValue();
    }

    private Eligibility determineEligibility(JsonNode container, String planType, String subscriptionPlan) {
        String currentPlan = (planType + " " + subscriptionPlan).toLowerCase(Locale.ROOT);
        if (currentPlan.contains("plus")) {
            return new Eligibility("ALREADY_PLUS", "当前账号已经是 Plus 套餐");
        }

        JsonNode promotions = container.path("eligible_promo_campaigns");
        JsonNode offers = container.path("eligible_offers");
        JsonNode trial = container.path("entitlement").path("trial");
        if (containsExplicitFreePlus(promotions) || containsExplicitFreePlus(offers) || containsExplicitFreePlus(trial)) {
            return new Eligibility("ELIGIBLE", "响应包含 Plus 免费、赠送或 100% 折扣资格");
        }

        boolean hasPlusOffer = containsText(offers, "plus") || containsText(promotions, "plus");
        boolean yearlyNewUser = container.path("is_eligible_for_yearly_plus_new_user_subscription").asBoolean(false);
        boolean yearlyExistingUser = container.path("is_eligible_for_yearly_plus_existing_user_subscription").asBoolean(false);
        if (hasPlusOffer || yearlyNewUser || yearlyExistingUser) {
            return new Eligibility("REVIEW", "可获取 Plus 报价，但该响应没有免费或 100% 折扣证据，需进入结算页确认");
        }
        return new Eligibility("NOT_ELIGIBLE", "响应未提供免费升级 Plus 的资格证据");
    }

    private boolean containsExplicitFreePlus(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsExplicitFreePlus(child)) return true;
            }
            return false;
        }
        if (!node.isObject()) {
            return false;
        }

        boolean offerLike = node.has("id") || node.has("name") || node.has("offer_id") || node.has("campaign_id")
                || node.has("discount") || node.has("percent_off") || node.has("discount_percentage");
        if (offerLike && containsText(node, "plus") && hasFreeSignal(node)) {
            return true;
        }
        Iterator<JsonNode> children = node.elements();
        while (children.hasNext()) {
            if (containsExplicitFreePlus(children.next())) return true;
        }
        return false;
    }

    private boolean hasFreeSignal(JsonNode node) {
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey().toLowerCase(Locale.ROOT);
            JsonNode value = field.getValue();
            if ((key.contains("percent_off") || key.contains("discount_percentage")) && value.asDouble(0) >= 100) {
                return true;
            }
            if ((key.equals("free") || key.contains("is_free") || key.contains("gratis") || key.contains("complimentary"))
                    && value.asBoolean(false)) {
                return true;
            }
            if (value.isTextual()) {
                String text = value.asText().toLowerCase(Locale.ROOT);
                if (text.contains("free_trial") || text.contains("free-plus") || text.contains("plus_free")
                        || text.contains("gratis") || text.contains("complimentary") || text.contains("100%")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsText(JsonNode node, String needle) {
        return node != null && node.toString().toLowerCase(Locale.ROOT).contains(needle);
    }

    private String json(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node == null || node.isMissingNode() ? objectMapper.createObjectNode() : node);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("").trim();
        return value.isBlank() ? fallback : value;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private record Eligibility(String status, String reason) {
    }

    public record TokenClaims(String userId, String email, String name, LocalDateTime expiresAt) {
    }

    public record ParsedAccount(
            String accountId,
            String userId,
            String email,
            String displayName,
            LocalDateTime tokenExpiresAt,
            String planType,
            String subscriptionPlan,
            boolean activeSubscription,
            boolean activeSubscriptionGratis,
            String accountStatus,
            String plusEligibility,
            String eligibilityReason,
            String eligibleOffers,
            String eligiblePromoCampaigns,
            String rawResponse
    ) {
    }
}
