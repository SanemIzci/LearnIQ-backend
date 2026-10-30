package com.learniq.ai.config;

import com.fasterxml.jackson.core.JsonProcessingException;
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
 * - 3 retry sonucu düzelmemiş hata → <topic>.DLT'ye yayınlanır (izlenebilirlik + yeniden işleme)
 *
 * DLT mesajları ayrı bir JsonSerializer'lı template ile yazılır; çünkü ana producer
 * StringSerializer kullandığı için Map tipinde event payload'ını serialize edemez.
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
    public DeadLetterPublishingRecoverer ragDeadLetterPublisher(KafkaTemplate<String, Object> dltKafkaTemplate) {
        return new DeadLetterPublishingRecoverer(dltKafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
    }

    @Bean
    public DefaultErrorHandler ragDefaultErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(8_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        // Kalıcı hatalar: retry anlamsız
        handler.addNotRetryableExceptions(
                JsonProcessingException.class,
                IllegalArgumentException.class,
                DataIntegrityViolationException.class);

        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> ragListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler ragDefaultErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(ragDefaultErrorHandler);
        factory.setConcurrency(2);
        return factory;
    }
}
