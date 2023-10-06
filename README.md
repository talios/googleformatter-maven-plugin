# Google Formatter Plugin for Apache Maven

A simple [Apache Maven](http://maven.apache.org) plugin to reformat
a projects source/test-sources using the [google-java-format](https://github.com/google/google-java-format)
project to conform with the [Google Code Style Guide](https://google.github.io/styleguide/javaguide.html).

By default, the plugin will only process _stale_ source files ( comparing
against their respective `.class` files existence/timestamp ).

After processing each file, the contents `sha1` is compared against the
original and only rewritten if they no longer match.
```xml
    <plugin>
      <groupId>com.theoryinpractise</groupId>
      <artifactId>googleformatter-maven-plugin</artifactId>
      <version>1.8.0</version>
      <executions>
        <execution>
          <id>reformat-sources</id>
          <configuration>
            <includeStale>false</includeStale>
            <style>GOOGLE</style>
            <formatMain>true</formatMain>
            <formatTest>true</formatTest>
            <filterModified>false</filterModified>
            <skip>false</skip>
            <fixImports>false</fixImports>
            <maxLineLength>100</maxLineLength>
          </configuration>
          <goals>
            <goal>format</goal>
          </goals>
          <phase>process-sources</phase>
        </execution>
      </executions>
    </plugin>
```

To fail build if formatting is needed

```mvn com.theoryinpractise:googleformatter-maven-plugin:1.8.0:check```

# Changes

* 1.8.0 - Sun 30 Jul 2023 11:00:23 AEST
  * Failing build if format is needed
* 1.7.4 - Wed 19 Jun 2019 23:52:15 NZST
  * Add formatMain / formatTest options*
* 1.7.3 -Tue  4 Jun 2019 12:31:34 NZST
  * Restored maxLineLenght and formatter.maxLineLength property
* 1.0.6 - Tue 31 May 2016 10:51:16 NZST
  * Exposed `formatter.modified` to reformat only changed SCM files.
  * Requires Java 8 to run now.
* 1.0.5 - Thu 26 May 2016 11:35:33 NZST
  * Exposed `formatter.length` as a property
* 1.0.4 - Fri 15 Apr 2016 22:13:39 NZST
  * Dropped required flag on configuration values.
* 1.0.3 - Fri 15 Apr 2016 20:45:58 NZST
  * Added `<skip>` ( and `-Dformatter.skip` ) configuration setting to skip reformatting code.
* 1.0.2 - Thu 14 Apr 2016 12:58:00 NZST
  * Handle missing test directories.
