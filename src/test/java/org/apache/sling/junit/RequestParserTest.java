/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.junit;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Function;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class RequestParserTest {

    @ParameterizedTest
    @MethodSource("configs")
    void testSelector(String pathInfo, String expectedTestSelector, String expectedExtension, String expectedMethodSelector) {
        assertEquals(expectedTestSelector, new RequestParser(pathInfo).getTestSelectorString());
    }

    @ParameterizedTest
    @MethodSource("configs")
    void testExtension(String pathInfo, String expectedTestSelector, String expectedExtension, String expectedMethodSelector) {
        assertEquals(expectedExtension, new RequestParser(pathInfo).getExtension());
    }

    @ParameterizedTest
    @MethodSource("configs")
    void testMethodName(String pathInfo, String expectedTestSelector, String expectedExtension, String expectedMethodSelector) {
        assertEquals(expectedMethodSelector, new RequestParser(pathInfo).getMethodName());
    }

    @ParameterizedTest
    @MethodSource("acceptTestNameArguments")
    void testAccepTestName(String pathInfo, String testName, boolean isValid) {
        final RequestParser parser = new RequestParser(pathInfo);
        assertEquals(isValid, parser.acceptTestName(testName), String.format("accept test named \"%s\"", testName));
    }
    @SuppressWarnings("unused") // test arguments
    static Stream<Arguments> acceptTestNameArguments() {
        return concatStreams(
                toArguments("/org.example.FooTest/testBar.html",
                    array("org.example.FooTest"),
                    array("org.example.FooTest$1")),
                toArguments("/org.example.FooTest.html",
                    array("org.example.FooTest"),
                    array("org.example.FooTest$1", "org.example.bar.BarTest")),
                toArguments("/org.example.html",
                    array("org.example.FooTest", "org.example.FooTest$1", "org.example.bar.BarTest"),
                    array("org.acme.FooTest", "org.examplebar.BarTest")));
    }

    @SafeVarargs
    private static <T> Stream<T> concatStreams(Stream<T>... streams) {
        return Stream.of(streams).flatMap(Function.identity());
    }

    private static Stream<Arguments> toArguments(String pathInfo, String[] valid, String[] invalid) {
        return Stream.concat(
            Stream.of(valid).map(name -> arguments(pathInfo, name, true)),
            Stream.of(invalid).map(name -> arguments(pathInfo, name, false)));
    }

    @SafeVarargs
    static <T>  T[] array(T... elements) {
        return elements;
    }


    @SuppressWarnings("unused") // test arguments
    static Stream<Arguments> configs() {
        return Stream.of(
            arguments(EMPTY, EMPTY, EMPTY, EMPTY),
            arguments("/.html", EMPTY, "html", EMPTY),
            arguments("/someTests.here.html", "someTests.here", "html", EMPTY),
            arguments("someTests.here.html", "someTests.here", "html", EMPTY),
            arguments("someTests.here.html.json", "someTests.here.html", "json", EMPTY),
            arguments("someTests.here.html.json/TEST_METHOD_NAME.txt", "someTests.here.html.json", "txt", "TEST_METHOD_NAME"),
            arguments(".json/TEST_METHOD_NAME", "", "json/TEST_METHOD_NAME", ""),
            arguments(".json/TEST_METHOD_NAME.txt", ".json", "txt", "TEST_METHOD_NAME"),
            arguments("/.json/TEST_METHOD_NAME.txt", ".json", "txt", "TEST_METHOD_NAME"),
            arguments("/.json/TEST_METHOD_NAME.txt", ".json", "txt", "TEST_METHOD_NAME"),
            arguments("/.html.json/TEST_METHOD_NAME.txt", ".html.json", "txt", "TEST_METHOD_NAME")
        );
     }
}