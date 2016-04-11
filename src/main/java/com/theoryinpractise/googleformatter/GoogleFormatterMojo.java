package com.theoryinpractise.googleformatter;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import static com.google.common.io.Files.getFileExtension;
import static com.google.common.io.Files.getNameWithoutExtension;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Joiner;
import com.google.common.io.CharStreams;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ResourceInfo;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

import com.google.googlejavaformat.java.Formatter;

import org.codehaus.plexus.compiler.util.scan.InclusionScanException;
import org.codehaus.plexus.compiler.util.scan.SimpleSourceInclusionScanner;
import org.codehaus.plexus.compiler.util.scan.SourceInclusionScanner;
import org.codehaus.plexus.compiler.util.scan.StaleSourceScanner;
import org.codehaus.plexus.compiler.util.scan.mapping.SourceMapping;
import org.codehaus.plexus.compiler.util.scan.mapping.SuffixMapping;

// import com.google.googlejavaformat.java.JavaFormatterOptions.JavadocFormatter;

// import com.google.googlejavaformat.java.JavaFormatterOptions.SortImports;
// import com.google.googlejavaformat.java.JavaFormatterOptions.Style;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.io.File;
import java.io.FileWriter;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;

/**
 * Reformat all source files using the Google Code Formatter
 */
@Mojo(name = "googleformatter", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GoogleFormatterMojo  extends AbstractMojo
{

  @Parameter(required = true, readonly = true, property = "project")
  protected MavenProject project;

  @Parameter(required = true, readonly = true, property = "project.build.sourceDirectory")
  protected File sourceDirectory;

  @Parameter(required = true, property = "project.build.outputDirectory")
  protected File outputDirectory;

  @Parameter(required = true, defaultValue = "true")
  protected boolean sortImports;

  @Parameter(required = true, defaultValue = "true")
  protected boolean includeStale;

    public void execute()
        throws MojoExecutionException
    {
      try {
        File f = outputDirectory;

        SourceInclusionScanner scanner = getSourceInclusionScanner(includeStale);
        scanner.addSourceMapping(new SuffixMapping(".java", new HashSet(Arrays.asList(".java", ".class"))));

        final Set<File> sourceFiles;

        try {
            sourceFiles = scanner.getIncludedSources(sourceDirectory, outputDirectory);
        } catch (InclusionScanException e) {
            throw new MojoExecutionException("Error scanning source path: \'" + sourceDirectory.getPath() + "\' " + "for  files to reformat.", e);
        }

        // Formatter formatter = new Formatter(JavadocFormatter.NONE, Style.GOOGLE,
          //  sortImports ? SortImports.YES : SortImports.NO);
        Formatter formatter = new Formatter();

        for (File file : sourceFiles) {
          String source = CharStreams.toString(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
          String formattedSource = formatter.formatSource(source);

          // overwrite existing file

        }
    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage());
    }
    }

        protected SourceInclusionScanner getSourceInclusionScanner(boolean includeStale) {
        return includeStale
                ? new SimpleSourceInclusionScanner(Collections.singleton("**/*"), Collections.EMPTY_SET)
                : new StaleSourceScanner(1024);
    }

}
