package com.example.reactornettytcpclient.jobs;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class MyQuartzJob implements Job {
    static  Instant[] lastEmitTime = { Instant.now() };

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Instant now = Instant.now();
        long elapsedMillis = Duration.between(lastEmitTime[0], now).toMillis();
        System.out.println("quartz " + elapsedMillis);
        lastEmitTime[0] = now;
    }
}
