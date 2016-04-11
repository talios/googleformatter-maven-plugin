package com.theoryinpractise.googleformatter;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.googlejavaformat.FormatterDiagnostic;
import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import com.google.googlejavaformat.java.JavaCommentsHelper;
import com.google.googlejavaformat.java.JavaInput;
import com.google.googlejavaformat.java.JavaOutput;
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
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

  @Parameter(required = true, defaultValue = "false")
  protected boolean includeStale;

  @Parameter(required = true, defaultValue = "100")
  protected int maxWidth;

  public void execute() throws MojoExecutionException {

    if ("pom".equals(project.getPackaging())) {
      getLog().info("Project packaging is POM, skipping...");
      return;
    }

    if (!sourceDirectory.exists()) {
      getLog().info("Source directory does not exist, skipping reformat");
      return;
    }

    try {
      Set<File> sourceFiles = new HashSet<>();

      sourceFiles.addAll(findFilesToReformat(sourceDirectory, outputDirectory));
      sourceFiles.addAll(findFilesToReformat(testSourceDirectory, testOutputDirectory));

      for (File file : sourceFiles) {
        String source = CharStreams.toString(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        String formattedSource = formatSource(file.getPath(), source, maxWidth);

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
    try {
      SourceInclusionScanner scanner = getSourceInclusionScanner(includeStale);
      scanner.addSourceMapping(new SuffixMapping(".java", new HashSet(Arrays.asList(".java", ".class"))));
      Set<File> sourceFiles = scanner.getIncludedSources(sourceDirectory, outputDirectory);
      getLog().info("Found " + sourceFiles.size() + " uncompiled/modified files in " + sourceDirectory.getPath() + " to reformat.");
      return sourceFiles;
    } catch (InclusionScanException e) {
      throw new MojoExecutionException("Error scanning source path: \'" + sourceDirectory.getPath() + "\' " + "for  files to reformat.", e);
    }
  }

  protected SourceInclusionScanner getSourceInclusionScanner(boolean includeStale) {
    return includeStale ? new SimpleSourceInclusionScanner(Collections.singleton("**/*"), Collections.EMPTY_SET) : new StaleSourceScanner(1024);
  }

  /**
   * Format an input string (a Java compilation unit) into an output string.
   * <p>
   * Lifted from formatter-real
   *
   * @param input the input string
   * @return the output string
   * @throws FormatterException if the input string cannot be parsed
   */
  public String formatSource(String fileName, String input, int maxWidth) throws FormatterException {
    JavaInput javaInput = new JavaInput(fileName, input);
    JavaOutput javaOutput = new JavaOutput(javaInput, new JavaCommentsHelper());
    List<FormatterDiagnostic> errors = new ArrayList<>();
    Formatter.format(javaInput, javaOutput, maxWidth, errors, 1);
    if (!errors.isEmpty()) {
      throw new FormatterException(errors);
    }
    StringBuilder result = new StringBuilder(input.length());
    RangeSet<Integer> lineRangeSet = TreeRangeSet.create();
    lineRangeSet.add(Range.<Integer>all());
    try {
      javaOutput.writeMerged(result, lineRangeSet);
    } catch (IOException ignored) {
      throw new AssertionError("IOException impossible for StringWriter");
    }
    return result.toString();
  }

}
