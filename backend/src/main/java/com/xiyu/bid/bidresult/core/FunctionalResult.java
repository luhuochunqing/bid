package com.xiyu.bid.bidresult.core;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A functional Result wrapper representing either a Success (S) or a Failure (F).
 */
public final class FunctionalResult<S, F> {
    private final S successValue;
    private final F failureValue;
    private final boolean successState;

    private FunctionalResult(S success, F failure, boolean isSuccess) {
        this.successValue = success;
        this.failureValue = failure;
        this.successState = isSuccess;
    }

    public static <S, F> FunctionalResult<S, F> success(S value) {
        return new FunctionalResult<>(value, null, true);
    }

    public static <S, F> FunctionalResult<S, F> failure(F error) {
        return new FunctionalResult<>(null, Objects.requireNonNull(error), false);
    }

    public boolean isSuccess() {
        return successState;
    }

    public boolean isFailure() {
        return !successState;
    }

    public <T> FunctionalResult<T, F> map(Function<? super S, ? extends T> mapper) {
        if (successState) {
            return success(mapper.apply(successValue));
        }
        return failure(failureValue);
    }

    public <T> FunctionalResult<T, F> flatMap(Function<? super S, FunctionalResult<T, F>> mapper) {
        if (successState) {
            return mapper.apply(successValue);
        }
        return failure(failureValue);
    }

    public void ifSuccess(Consumer<? super S> action) {
        if (successState) {
            action.accept(successValue);
        }
    }

    public S orElseThrow(Function<? super F, ? extends RuntimeException> exceptionSupplier) {
        if (successState) {
            return successValue;
        }
        throw exceptionSupplier.apply(failureValue);
    }
}
