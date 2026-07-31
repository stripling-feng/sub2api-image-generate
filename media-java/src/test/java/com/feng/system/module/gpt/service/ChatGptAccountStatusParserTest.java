package com.feng.system.module.gpt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ChatGptAccountStatusParserTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatGptAccountStatusParser parser = new ChatGptAccountStatusParser(objectMapper);
    private final ChatGptAccountStatusParser.TokenClaims claims =
            new ChatGptAccountStatusParser.TokenClaims("user-1", "user@example.com", "Test User", LocalDateTime.now().plusDays(1));

    @Test
    void purchasablePlusOfferWithoutFreeEvidenceRequiresReview() throws Exception {
        ChatGptAccountStatusParser.ParsedAccount result = parser.parse(json("""
                {
                  "accounts": {
                    "account-1": {
                      "account": {"account_id":"account-1","plan_type":"free","is_deactivated":false},
                      "entitlement": {"has_active_subscription":false,"is_active_subscription_gratis":false,"subscription_plan":"chatgptfreeplan"},
                      "can_access_with_session": true,
                      "is_eligible_for_yearly_plus_new_user_subscription": true,
                      "eligible_promo_campaigns": {},
                      "eligible_offers": {
                        "offers":[{"id":"chatgptplusplan"},{"id":"chatgptfreeworkspaceplan"}],
                        "default_offer_id":"chatgptplusplan"
                      }
                    }
                  },
                  "account_ordering":"account-1"
                }
                """), claims);

        assertThat(result.planType()).isEqualTo("free");
        assertThat(result.plusEligibility()).isEqualTo("REVIEW");
        assertThat(result.eligibilityReason()).contains("没有免费或 100% 折扣证据");
    }

    @Test
    void explicitHundredPercentPlusOfferIsEligible() throws Exception {
        ChatGptAccountStatusParser.ParsedAccount result = parser.parse(json("""
                {
                  "accounts": {
                    "default": {
                      "account": {"account_id":"account-2","plan_type":"free"},
                      "entitlement": {"subscription_plan":"chatgptfreeplan"},
                      "eligible_offers": {"offers":[{"id":"chatgptplusplan","percent_off":100}]}
                    }
                  }
                }
                """), claims);

        assertThat(result.plusEligibility()).isEqualTo("ELIGIBLE");
    }

    @Test
    void currentPlusPlanIsNotReportedAsUpgradeEligible() throws Exception {
        ChatGptAccountStatusParser.ParsedAccount result = parser.parse(json("""
                {
                  "accounts": {
                    "default": {
                      "account": {"account_id":"account-3","plan_type":"plus"},
                      "entitlement": {"has_active_subscription":true,"subscription_plan":"chatgptplusplan"}
                    }
                  }
                }
                """), claims);

        assertThat(result.plusEligibility()).isEqualTo("ALREADY_PLUS");
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }
}
