package com.learniq.ai.rag;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * RAG ingestion/retrieval metrikleri (Micrometer).
 *
 * Ingestion hattının ilk fazından itibaren gözlemlenebilirlik sağlar:
 * embed sayısı, hash-skip, bayat event reddi, embedding latency, DLT.
 */
@Component
public class RagMetrics {

    private final Counter embedded;
    private final Counter hashSkipped;
    private final Counter staleRejected;
    private final Counter retries;
    private final Counter dlt;
    private final Counter retrievalEmpty;
    private final Counter retrievalModelMismatch;
    private final Timer embedLatency;

    public RagMetrics(MeterRegistry registry) {
        this.embedded = registry.counter("rag.ingest.embedded");
        this.hashSkipped = registry.counter("rag.ingest.hash.skipped");
        this.staleRejected = registry.counter("rag.ingest.stale.rejected");
        this.retries = registry.counter("rag.ingest.retries");
        this.dlt = registry.counter("rag.ingest.dlt");
        this.retrievalEmpty = registry.counter("rag.retrieval.empty");
        this.retrievalModelMismatch = registry.counter("rag.retrieval.model.mismatch");
        this.embedLatency = registry.timer("rag.ingest.embed.latency");
    }

    public void embedded() {
        embedded.increment();
    }

    public void hashSkipped() {
        hashSkipped.increment();
    }

    public void staleRejected() {
        staleRejected.increment();
    }

    public void retry() {
        retries.increment();
    }

    public void dlt() {
        dlt.increment();
    }

    public void retrievalEmpty() {
        retrievalEmpty.increment();
    }

    public void retrievalModelMismatch() {
        retrievalModelMismatch.increment();
    }

    public void recordEmbedLatency(long nanos) {
        embedLatency.record(nanos, TimeUnit.NANOSECONDS);
    }
}
