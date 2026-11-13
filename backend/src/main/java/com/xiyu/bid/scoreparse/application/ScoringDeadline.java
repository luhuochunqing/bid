package com.xiyu.bid.scoreparse.application;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** One wall-clock deadline from task creation, including queue wait and response-body reads. */
public final class ScoringDeadline {
    private static final ScheduledExecutorService CLOSER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread=new Thread(r,"scoring-body-deadline");thread.setDaemon(true);return thread;
    });
    private ScoringDeadline() {}
    public static long from(LocalDateTime createdAt) {
        if(createdAt==null)throw new IllegalStateException("SCORING_CREATED_AT_MISSING");
        return createdAt.atZone(ZoneId.systemDefault()).toInstant().plusSeconds(1800).toEpochMilli();
    }
    public static Duration remaining(long deadline,long configuredMillis) {
        long left=deadline-System.currentTimeMillis();
        if(left<=0)throw new IllegalStateException("SCORING_DEADLINE_EXCEEDED");
        return Duration.ofMillis(Math.min(left,configuredMillis));
    }
    public static void check(long deadline){remaining(deadline,Long.MAX_VALUE);}
    static InputStream bounded(InputStream raw,long maxBytes,long deadline) {
        long left=remaining(deadline,Long.MAX_VALUE).toMillis();
        var close=CLOSER.schedule(()->{try{raw.close();}catch(IOException ignored){}},left,TimeUnit.MILLISECONDS);
        return new FilterInputStream(raw) {
            long count;
            @Override public int read() throws IOException {check(deadline);int n=in.read();if(n>=0)limit(1);return n;}
            @Override public int read(byte[] b,int o,int l)throws IOException {check(deadline);int n=in.read(b,o,l);if(n>0)limit(n);return n;}
            void limit(int n)throws IOException {count+=n;if(count>maxBytes)throw new IOException("SCORING_RESPONSE_TOO_LARGE");check(deadline);}
            @Override public void close()throws IOException {close.cancel(false);super.close();}
        };
    }
}
