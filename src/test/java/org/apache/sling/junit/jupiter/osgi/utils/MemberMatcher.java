/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.junit.jupiter.osgi.utils;

import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.function.Function;

import static org.hamcrest.Condition.matched;

public class MemberMatcher<S, T> extends TypeSafeDiagnosingMatcher<S> {

    private final String memberName;

    private final Function<S, T> memberAccessor;

    private final Matcher<T> valueMatcher;

    private MemberMatcher(String memberName, Function<S,T> memberAccessor, Matcher<T> valueMatcher) {
        this.memberName = memberName;
        this.memberAccessor = memberAccessor;
        this.valueMatcher = valueMatcher;
    }

    @Override
    public boolean matchesSafely(S object, Description mismatch) {
        T value = memberAccessor.apply(object);
        return matched(value, mismatch)
                .matching(valueMatcher);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("(").appendText(memberName).appendText(", ")
                .appendDescriptionOf(valueMatcher).appendText(")");
    }


    /**
     * Creates a matcher that matches when the examined object has a JavaBean property
     * with the specified name whose value satisfies the specified matcher.
     * <p/>
     * For example:
     * <pre>assertThat(myBean, hasProperty("foo", equalTo("bar"))</pre>
     *
     * @param memberAccessor
     *     the name of the JavaBean property that examined beans should possess
     * @param valueMatcher
     *     a matcher for the value of the specified property of the examined bean
     */
    @Factory
    public static <S, T> Matcher<S> hasMember(String name, Function<S, T> memberAccessor, Matcher<T> valueMatcher) {
        return new MemberMatcher<>(name, memberAccessor, valueMatcher);
    }
}

