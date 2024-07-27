package com.example.reactornettytcpclient;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class ReactorNettyTcpClientApplicationTests {
    @Test
    public void testJobExecutionAccuracy() throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long expectedInterval = 100; // 100 milliseconds

        // Allow the job to execute for a while
        TimeUnit.SECONDS.sleep(5); // Run for 5 seconds

        // No need to validate exact intervals in the test
        // Instead, you may log or print timing information for review
        System.out.println("Test completed. Check console logs for execution timings.");
    }

    @Test
    void schedulerAccuracyTest() {
        Instant[] lastEmitTime = { Instant.now() };

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        scheduler.scheduleAtFixedRate(() -> {
            Instant now = Instant.now();
            long elapsedMillis = Duration.between(lastEmitTime[0], now).toMillis();
            System.out.println(elapsedMillis);
            lastEmitTime[0] = now;
        }, 0, 100, TimeUnit.MILLISECONDS); // Schedule task every 100 milliseconds

        // Keep the application running to observe the execution
        try {
            Thread.sleep(10000); // Run for 10 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        scheduler.shutdown();
    }

    @Test
    void fluxAccuracyTest() throws InterruptedException {
        Instant[] lastEmitTime = { Instant.now() };

        Flux.interval(Duration.ofMillis(100))
                .doOnNext(tick -> {
                    Instant now = Instant.now();
                    long elapsedMillis = Duration.between(lastEmitTime[0], now).toMillis();
                    System.out.println(elapsedMillis);
                    lastEmitTime[0] = now;
                })
                .onBackpressureDrop()
                .subscribe();

        // Run for a specific duration to observe the interval
        Thread.sleep(10000); // Run for 10 seconds
    }

    @Test
    void contextLoads() {
        // Create a Flux that emits signals at a fixed interval
        Flux<Long> intervalFlux = Flux.interval(Duration.ofSeconds(3));

        // Transform each tick into two separate signals with a delay
        Flux<String> signalFlux = intervalFlux.flatMap(tick -> {
            // Create two separate signals for each interval tick
            Mono<String> signal1 = Mono.just("Signal1 for tick " + tick);
            Mono<String> signal2 = Mono.just("Signal2 for tick " + tick);

            // Combine the two signals into a single Flux with a delay
            return Flux.concat(signal1, signal2)
                    .delayElements(Duration.ofMillis(1000)); // Add a delay of 500 milliseconds between emissions
        });

        // Subscribe to the combined Flux and print each emitted signal
        signalFlux.subscribe(signal -> {
            System.out.println("Received: " + signal);
        }, error -> {
            System.err.println("Error: " + error);
        }, () -> {
            System.out.println("Stream complete");
        });

        // Keep the application running for demonstration purposes
        try {
            Thread.sleep(10000); // Adjust duration as needed
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
