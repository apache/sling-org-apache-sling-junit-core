#Integration tests for the server testing annotation @TestReference
This bundle provides server-side junit test `TestReferenceJITest.java` which can be run with SlingAnnotationsTestRunner via a `POST` request to `/system/sling/junit/org.apache.sling.junit.tests.TestReferenceJTest.html`
with a debugging breakpoint set with ReferenceIT.java `waitForSling()` method. 

Note: If additional tests of this type are needed, name the test class such that it satisfies the configured 
regular expression found in bnd.bnd `Sling-Test-Regexp: .*JITest`


##To run or debug the tests from this folder, use:

### Build
  mvn clean verify -Dannotations.bundle.version=VVV

Where VVV is the version of the junit-core bundle to test.

### Debug

Two separate remote debugger clients may be needed: 
`ReferenceIT.java` is a junit test class which runs outside of Sling, 
which can be debugged using the following commands... 
  
* `cd sling-org-apache-sling-junit-core/src/it/annotations-it`
* `mvn clean verify  -Dannotations.bundle.version=1.1.1-SNAPSHOT -Dmaven.failsafe.debug`
* The test will wait for debugger to connect before continuing.
* Set a breakpoint in `waitForSling` within  ReferenceIT.java   
* Connect a debugger client over 5005 (default) or the selected port [1]  
* Make note of the localhost URL and port (which is dynamically selected) by inspecting the `URI url` variable

With the test paused via this debugger, it is now possible to connect a browser to the running (temporary) Sling test instance,
or connect another debugger to access classes such as `TestReferenceJITest.java` or other classes, which run within Sling.
* Configure `annotations-it/pom.xml` with the following or equivalent  
```
   <pax.vm.options>
    -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5015
   </pax.vm.options>
```
* Reconnect the `ReferenceIT.java` debugger as described above

[1] https://maven.apache.org/surefire/maven-failsafe-plugin/examples/debugging.html
