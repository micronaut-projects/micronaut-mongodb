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
package io.micronaut.configuration.mongo.coroutine

import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.IntrinsicsKt
import spock.lang.Specification

class CoroutineSupportSpec extends Specification {

    void "test await returns immediate value"() {
        expect:
        CoroutineSupport.await(new CoroutineSupport.SuspendOperation<String>() {
            @Override
            Object invoke(Continuation<? super String> continuation) {
                return "value"
            }
        }) == "value"
    }

    void "test await returns suspended value"() {
        when:
        String value = CoroutineSupport.await(new CoroutineSupport.SuspendOperation<String>() {
            @Override
            Object invoke(Continuation<? super String> continuation) {
                new Thread(new Runnable() {
                    @Override
                    void run() {
                        continuation.resumeWith("value")
                    }
                }).start()
                return IntrinsicsKt.getCOROUTINE_SUSPENDED()
            }
        })

        then:
        value == "value"
    }

    void "test await rethrows runtime exception"() {
        when:
        CoroutineSupport.await(new CoroutineSupport.SuspendOperation<String>() {
            @Override
            Object invoke(Continuation<? super String> continuation) {
                throw new IllegalStateException("boom")
            }
        })

        then:
        IllegalStateException e = thrown()
        e.message == "boom"
    }
}
