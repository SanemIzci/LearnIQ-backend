package com.learniq.ai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RagEmbeddingConfigValidator Unit Tests")
class RagEmbeddingConfigValidatorTest {

    private static final String MODEL = "text-embedding-3-small";
    private static final String SIG = "text-embedding-3-small@1536";

    private MockEnvironment env(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    private RagEmbeddingConfigValidator validator(int dimensions, String sig, String allowed,
                                                  String apiKey, String... profiles) {
        return new RagEmbeddingConfigValidator(MODEL, dimensions, sig, allowed, apiKey, env(profiles));
    }

    @Test
    @DisplayName("dev - boş allowed-model-sigs uyarı ile kabul edilir")
    void dev_emptyAllowedModels_isAllowed() {
        assertThatCode(() -> validator(1536, SIG, "", "key", "dev").validate())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("prod - boş allowed-model-sigs fail-fast")
    void prod_emptyAllowedModels_fails() {
        assertThatThrownBy(() -> validator(1536, SIG, "", "key", "prod").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allowed-model-sigs");
    }

    @Test
    @DisplayName("prod - liste mevcut imzayı içermiyorsa fail-fast")
    void prod_allowedModelsWithoutCurrentSig_fails() {
        assertThatThrownBy(() -> validator(1536, SIG, "other-model@1536", "key", "prod").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("içermiyor");
    }

    @Test
    @DisplayName("prod - geçerli konfigürasyon kabul edilir")
    void prod_validConfig_passes() {
        assertThatCode(() -> validator(1536, SIG, "old@1536, " + SIG, "key", "prod").validate())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("prod - API anahtarı boşsa fail-fast")
    void prod_missingApiKey_fails() {
        assertThatThrownBy(() -> validator(1536, SIG, SIG, "  ", "prod").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("api-key");
    }

    @Test
    @DisplayName("kolon boyutu 1536 değilse her profilde fail-fast")
    void nonDefaultDimensions_fails() {
        assertThatThrownBy(() -> validator(768, "text-embedding-3-small@768", "", "key", "dev").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vector(1536)");
    }

    @Test
    @DisplayName("model-sig boyut eki uyuşmuyorsa fail-fast")
    void modelSigWithoutDimensionSuffix_fails() {
        assertThatThrownBy(() -> validator(1536, "text-embedding-3-small", "", "key", "dev").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model-sig");
    }
}
