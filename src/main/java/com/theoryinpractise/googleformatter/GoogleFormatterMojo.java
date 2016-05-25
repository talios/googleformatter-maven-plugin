package com.theoryinpractise.googleformatter;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.JavaFormatterOptions;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.compiler.util.scan.InclusionScanException;
import org.codehaus.plexus.compiler.util.scan.SimpleSourceInclusionScanner;
import org.codehaus.plexus.compiler.util.scan.SourceInclusionScanner;
import org.codehaus.plexus.compiler.util.scan.StaleSourceScanner;
import org.codehaus.plexus.compiler.util.scan.mapping.SuffixMapping;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.google.googlejavaformat.java.JavaFormatterOptions.JavadocFormatter;
import static com.google.googlejavaformat.java.JavaFormatterOptions.SortImports;
import static com.google.googlejavaformat.java.JavaFormatterOptions.Style;

/**
 * Reformat all source files using the Google Code Formatter
 */
@Mojo(name = "format", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class GoogleFormatterMojo extends AbstractMojo {

  @Parameter(required = true, readonly = true, property = "project")
  protected MavenProject project;

  @Parameter(required = true, readonly = true, property = "project.build.sourceDirectory")
  protected File sourceDirectory;

  @Parameter(required = true, readonly = true, property = "project.build.testSourceDirectory")
  protected File testSourceDirectory;

  @Parameter(required = true, readonly = true, property = "project.build.outputDirectory")
  protected File outputDirectory;

  @Parameter(required = true, readonly = true, property = "project.build.testOutputDirectory")
  protected File testOutputDirectory;

  @Parameter(defaultValue = "false")
  protected boolean includeStale;

  @Parameter(defaultValue = "ALSO")
  protected SortImports sortImports;

  @Parameter(defaultValue = "NONE")
  protected JavadocFormatter javadocFormatter;

  @Parameter(defaultValue = "GOOGLE")
  protected Style style;

  @Parameter(defaultValue = "100", property = "formatter.length")
  protected int maxWidth;

  @Parameter(defaultValue = "false", property = "formatter.skip")
  protected boolean skip;

  public static class JavaFormatterOptionsWithCustomLength extends JavaFormatterOptions {
    int maxLineLength;

    public JavaFormatterOptionsWithCustomLength(JavadocFormatter javadocFormatter, Style style, SortImports sortImports, int maxLineLength) {
      super(javadocFormatter, style, sortImports);
      this.maxLineLength = maxLineLength;
    }

    @Override
    public int maxLineLength() {
      return maxLineLength;
    }
  }

  public void execute() throws MojoExecutionException {

    if ("pom".equals(project.getPackaging())) {
      getLog().info("Project packaging is POM, skipping...");
      return;
    }

    if (skip) {
      getLog().info("Skipping source reformatting due to plugin configuration.");
      return;
    }

    try {
      Set<File> sourceFiles = new HashSet<>();

      sourceFiles.addAll(findFilesToReformat(sourceDirectory, outputDirectory));
      sourceFiles.addAll(findFilesToReformat(testSourceDirectory, testOutputDirectory));

      for (File file : sourceFiles) {
        String source = CharStreams.toString(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));

        JavaFormatterOptions options = new JavaFormatterOptionsWithCustomLength(javadocFormatter, style, sortImports, maxWidth);
        Formatter formatter = new Formatter(options);
        String formattedSource = formatter.formatSource(source);

        HashCode sourceHash = Hashing.sha1().hashString(source, StandardCharsets.UTF_8);
        HashCode formattedHash = Hashing.sha1().hashString(formattedSource, StandardCharsets.UTF_8);

        if (!formattedHash.equals(sourceHash)) {
          // overwrite existing file
          Files.write(formattedSource, file, StandardCharsets.UTF_8);
          getLog().info("Reformatted file " + file.getPath());
        }
      }
    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage());
    }
  }

  private Set<File> findFilesToReformat(File sourceDirectory, File outputDirectory) throws MojoExecutionException {
    if (sourceDirectory.exists()) {
      try {
        SourceInclusionScanner scanner = getSourceInclusionScanner(includeStale);
        scanner.addSourceMapping(new SuffixMapping(".java", new HashSet(Arrays.asList(".java", ".class"))));
        Set<File> sourceFiles = scanner.getIncludedSources(sourceDirectory, outputDirectory);
        getLog().info("Found " + sourceFiles.size() + " uncompiled/modified files in " + sourceDirectory.getPath() + " to reformat.");
        return sourceFiles;
      } catch (InclusionScanException e) {
        throw new MojoExecutionException("Error scanning source path: \'" + sourceDirectory.getPath() + "\' " + "for  files to reformat.", e);
      }
    } else {
      getLog().info(String.format("Directory %s does not exist, skipping file collection.", sourceDirectory.getPath()));
      return Collections.emptySet();
    }
  }

  protected SourceInclusionScanner getSourceInclusionScanner(boolean includeStale) {
    return includeStale ? new SimpleSourceInclusionScanner(Collections.singleton("**/*"), Collections.EMPTY_SET) : new StaleSourceScanner(1024);
  }
}
