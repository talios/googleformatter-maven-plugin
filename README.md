# Google Formatter Plugin for Apache Maven

A simple [Apache Maven](http://maven.apache.org) plugin to reformat
a projects source/test-sources using the [google-java-format](https://github.com/google/google-java-format)
project.

By default the plugin will only process _stale_ source files ( comparing
against their respective `.class` files existence/timestamp ).

After processing each file, the contents `sha1` is compared against the
original and only rewritten if they no longer match.

    <plugin>
      <groupId>com.theoryinpractise</groupId>
      <artifactId>googleformatter-maven-plugin</artifactId>
      <version>1.0</version>
      <executions>
        <execution>
          <id>reformat-sources</id>
          <configuration>
            <includeStale>false</includeStale>
            <maxWidth>160</maxWidth>
            <sortImports>NO</sortImports>
            <javadocFormatter>NONE</javadocFormatter>
            <style>GOOGLE</style>
          </configuration>
          <goals>
            <goal>format</goal>
          </goals>
          <phase>process-sources</phase>
        </execution>
      </executions>
    </plugin>
