package com.theoryinpractise.googleformatter;

import com.google.common.base.MoreObjects;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.JavaFormatterOptions;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.repository.ScmRepository;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.googlejavaformat.java.JavaFormatterOptions.Style;
import static com.theoryinpractise.googleformatter.Constants.DIRECTORY_MISSING;

/** Reformat all source files using the Google Code Formatter */
@Mojo(name = "format", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class GoogleFormatterMojo extends AbstractMojo {

  public static final SuffixMapping SOURCE_MAPPING =
      new SuffixMapping(".java", new HashSet<>(Arrays.asList(".java", ".class")));
  @Component ScmManager scmManager;

  @Parameter(required = true, readonly = true, property = "session")
  protected MavenSession session;

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

  @Parameter(defaultValue = "GOOGLE")
  protected Style style;

  @Parameter(defaultValue = "false", property = "formatter.skip")
  protected boolean skip;

  @Parameter(defaultValue = "false", property = "formatter.modified")
  protected boolean filterModified;

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

      Set<File> sourceFilesToProcess =
          filterModified ? filterUnchangedFiles(sourceFiles) : sourceFiles;

      JavaFormatterOptions options = JavaFormatterOptions.builder().style(style).build();

      for (File file : sourceFilesToProcess) {
        String source =
            CharStreams.toString(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));

        Formatter formatter = new Formatter(options);
        String formattedSource = formatter.formatSource(source);

        HashCode sourceHash = Hashing.sha1().hashString(source, StandardCharsets.UTF_8);
        HashCode formattedHash = Hashing.sha1().hashString(formattedSource, StandardCharsets.UTF_8);

        if (!formattedHash.equals(sourceHash)) {
          // overwrite existing file
          Files.write(formattedSource, file, StandardCharsets.UTF_8);
          getLog().info(String.format("Reformatted file %s", file.getPath()));
        }
      }
    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage());
    }
  }

  private Set<File> filterUnchangedFiles(Set<File> originalFiles) throws MojoExecutionException {
    MavenProject topLevelProject = session.getTopLevelProject();
    try {
      String connectionUrl =
          MoreObjects.firstNonNull(
              topLevelProject.getScm().getConnection(), topLevelProject.getScm().getDeveloperConnection());
      ScmRepository repository = scmManager.makeScmRepository(connectionUrl);
      ScmFileSet scmFileSet = new ScmFileSet(topLevelProject.getBasedir());
      String basePath = topLevelProject.getBasedir().getAbsoluteFile().getPath();
      List<String> changedFiles =
          scmManager
              .status(repository, scmFileSet)
              .getChangedFiles()
              .stream()
              .map(f -> String.format("%s/%s", basePath, f.getPath()))
              .collect(Collectors.toList());

      return originalFiles
          .stream()
          .filter(f -> changedFiles.contains(f.getPath()))
          .collect(Collectors.toSet());

    } catch (ScmException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  private Set<File> findFilesToReformat(File sourceDirectory, File outputDirectory)
      throws MojoExecutionException {
    if (sourceDirectory.exists()) {
      try {
        SourceInclusionScanner scanner = getSourceInclusionScanner(includeStale);
        scanner.addSourceMapping(SOURCE_MAPPING);
        Set<File> sourceFiles = scanner.getIncludedSources(sourceDirectory, outputDirectory);
        getLog()
            .info(
                String.format(
                    Constants.FOUND_UNCOMPILED, sourceFiles.size(), sourceDirectory.getPath()));
        return sourceFiles;
      } catch (InclusionScanException e) {
        throw new MojoExecutionException(
            String.format(Constants.ERROR_SCANNING_PATH, sourceDirectory.getPath()), e);
      }
    } else {
      getLog().info(String.format(DIRECTORY_MISSING, sourceDirectory.getPath()));
      return Collections.emptySet();
    }
  }

  protected SourceInclusionScanner getSourceInclusionScanner(boolean includeStale) {
    return includeStale
        ? new SimpleSourceInclusionScanner(Collections.singleton("**/*"), Collections.emptySet())
        : new StaleSourceScanner(1024);
  }
}
