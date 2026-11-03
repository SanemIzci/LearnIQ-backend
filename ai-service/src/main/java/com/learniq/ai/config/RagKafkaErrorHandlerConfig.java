package com.learniq.ai.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

/**
 * RAG ingestion pipeline'ı için Kafka hata stratejisi:
 * - Geçici hatalar (DB bağlantısı, embedding sağlayıcı vs.) → üstel backoff ile 3 kez retry
 * - Kalıcı hatalar (bozuk mesaj, eksik alan, geçersiz kiracı) → retry edilmeden anında DLT
 * - 3 retry sonucu düzelmemiş hata → <topic>.DLT'ye yayınlanır (izlenebilirlik + kontrollü replay)
 *
 * DLT mesajları ayrı bir JsonSerializer'lı template ile yazılır; ayrıca her DLT
 * yayını için Micrometer counter artırılır (monitoring/alerting).
 */
@Configuration
public class RagKafkaErrorHandlerConfig {

    @Bean
    public KafkaTemplate<String, Object> dltKafkaTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties(null);
        DefaultKafkaProducerFactory<String, Object> producerFactory =
                new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new JsonSerializer<>());
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public DeadLetterPublishingRecoverer ragDeadLetterPublisher(
            KafkaTemplate<String, Object> dltKafkaTemplate, MeterRegistry meterRegistry) {
        Counter dltCounter = meterRegistry.counter("rag.ingest.dlt");
        return new DeadLetterPublishingRecoverer(dltKafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition())) {
            @Override
            public void accept(ConsumerRecord<?, ?> record, Exception ex) {
                dltCounter.increment();
                super.accept(record, ex);
            }
        };
    }

    @Bean
    public DefaultErrorHandler ragDefaultErrorHandler(DeadLetterPublishingRecoverer recoverer,
                                                      MeterRegistry meterRegistry) {
        Counter retryCounter = meterRegistry.counter("rag.ingest.retries");
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(8_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, ex) -> {
                    retryCounter.increment();
                    recoverer.accept(record, ex);
                }, backOff);

        // Kalıcı hatalar: retry anlamsız
        handler.addNotRetryableExceptions(
                JsonProcessingException.class,
                IllegalArgumentException.class,
                DataIntegrityViolationException.class);

        handler.setRetryListeners((record, ex, deliveryAttempt) -> {
            if (deliveryAttempt > 1) {
                retryCounter.increment();
            }
        });

        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> ragListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            DefaultErrorHandler ragDefaultErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(ragDefaultErrorHandler);
        factory.setConcurrency(2);
        return factory;
    }
}
