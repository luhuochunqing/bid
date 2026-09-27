package com.xiyu.bid.scoreparse.application;
/** Explicit extraction HTTP semantics; controllers map without conflating input and CAS failures. */
public class ScoreExtractionException extends RuntimeException {
    private final int status;
    public ScoreExtractionException(int status,String message){super(message);this.status=status;}
    public int status(){return status;}
}
