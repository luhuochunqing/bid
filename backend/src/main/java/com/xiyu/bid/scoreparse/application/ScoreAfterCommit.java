package com.xiyu.bid.scoreparse.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** A dispatch failure must never masquerade as rollback of already committed data. */
@Slf4j
final class ScoreAfterCommit {
    private ScoreAfterCommit() {}
    static void dispatch(Runnable work) {
        Runnable safe=()->{try{work.run();}catch(RuntimeException ex){log.warn("Score follow-up dispatch failed; committed state retained, type={}",ex.getClass().getSimpleName());}};
        if(TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){safe.run();}});
        } else safe.run();
    }
}
