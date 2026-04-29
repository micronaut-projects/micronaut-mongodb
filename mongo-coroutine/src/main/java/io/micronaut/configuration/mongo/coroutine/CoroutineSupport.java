/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.configuration.mongo.coroutine;

import io.micronaut.core.annotation.Internal;
import kotlin.ResultKt;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.coroutines.intrinsics.IntrinsicsKt;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Bridges the coroutine Mongo driver suspend API to Java callers.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@Internal
public final class CoroutineSupport {

    private static final long DEFAULT_TIMEOUT_SECONDS = 10;

    private CoroutineSupport() {
    }

    /**
     * Execute a suspend operation and wait for the result.
     *
     * @param operation The operation
     * @param <T> The result type
     * @return The result
     */
    public static <T> T await(SuspendOperation<T> operation) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Object result;
        try {
            result = operation.invoke(new Continuation<>() {
                @Override
                public CoroutineContext getContext() {
                    return EmptyCoroutineContext.INSTANCE;
                }

                @Override
                public void resumeWith(Object resumeResult) {
                    complete(future, resumeResult);
                }
            });
        } catch (Throwable e) {
            future.completeExceptionally(e);
            result = IntrinsicsKt.getCOROUTINE_SUSPENDED();
        }
        if (result != IntrinsicsKt.getCOROUTINE_SUSPENDED()) {
            complete(future, result);
        }
        try {
            return future.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting Mongo coroutine operation", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Mongo coroutine operation failed", cause);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out awaiting Mongo coroutine operation", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void complete(CompletableFuture<T> future, Object result) {
        try {
            ResultKt.throwOnFailure(result);
            future.complete((T) result);
        } catch (Throwable e) {
            future.completeExceptionally(e);
        }
    }

    /**
     * A suspendable Mongo coroutine operation.
     *
     * @param <T> The result type
     */
    @FunctionalInterface
    public interface SuspendOperation<T> {

        /**
         * @param continuation The continuation
         * @return The immediate result or {@code COROUTINE_SUSPENDED}
         * @throws Throwable if the invocation fails synchronously
         */
        Object invoke(Continuation<? super T> continuation) throws Throwable;
    }
}
