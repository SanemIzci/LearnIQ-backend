package com.learniq.ai.messaging;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Kafka mesajını aktif DB transaction'ı COMMIT edilene kadar bekletir;
 * böylece consumer, commit edilmemiş (bayat) veriyi okumaz; rollback olursa mesaj hiç gönderilmez.
 * Aktif transaction yoksa (ör. birim test) mesaj hemen gönderilir.
 */
public final class KafkaAfterCommit {

    private KafkaAfterCommit() {}

    public static void send(KafkaTemplate<String, Object> kafkaTemplate, String topic, Object payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    kafkaTemplate.send(topic, payload);
                }
            });
        } else {
            kafkaTemplate.send(topic, payload);
        }
    }
}
