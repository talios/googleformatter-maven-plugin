package com.theoryinpractise.googleformatter;

import com.google.common.base.MoreObjects;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.CharStreams;
import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.JavaFormatterOptions;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
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
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

import static com.theoryinpractise.googleformatter.Constants.DIRECTORY_MISSING;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public abstract class AbstractFormatter extends AbstractMojo {

    public static final SuffixMapping SOURCE_MAPPING = new SuffixMapping(".java", new HashSet<>(Arrays.asList(".java", ".class")));
    @Component
    ScmManager scmManager;

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
    protected JavaFormatterOptions.Style style;

    @Parameter(defaultValue = "false", property = "formatter.skip")
    protected boolean skip;

    @Parameter(defaultValue = "true", property = "formatter.main")
    protected boolean formatMain;

    @Parameter(defaultValue = "true", property = "formatter.test")
    protected boolean formatTest;

    @Parameter(defaultValue = "false", property = "formatter.modified")
    protected boolean filterModified;

    @Parameter(defaultValue = "false", property = "formatter.fixImports")
    protected boolean fixImports;

    @Parameter(defaultValue = "100", property = "formatter.maxLineLength")
    protected int maxLineLength;

    abstract void handleFormattedSource(File file, String formattedSource) throws MojoExecutionException;

    @Override
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

            if (formatMain) {
                sourceFiles.addAll(findFilesToReformat(sourceDirectory, outputDirectory));
            }
            if (formatTest) {
                sourceFiles.addAll(findFilesToReformat(testSourceDirectory, testOutputDirectory));
            }

            Set<File> sourceFilesToProcess = filterModified ? filterUnchangedFiles(sourceFiles) : sourceFiles;

            JavaFormatterOptions options = getJavaFormatterOptions();

            for (File file : sourceFilesToProcess) {
                String source = CharStreams.toString(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));

                com.google.googlejavaformat.java.Formatter formatter = new Formatter(options);
                String formattedSource = fixImports ? formatter.formatSourceAndFixImports(source) : formatter.formatSource(source);

                HashCode sourceHash = Hashing.sha256().hashString(source, StandardCharsets.UTF_8);
                HashCode formattedHash = Hashing.sha256().hashString(formattedSource, StandardCharsets.UTF_8);

                if (!formattedHash.equals(sourceHash)) {
                    handleFormattedSource(file, formattedSource);
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

    }

    private JavaFormatterOptions getJavaFormatterOptions() {
        JavaFormatterOptions options = spy(JavaFormatterOptions.builder().style(style).build());
        doReturn(maxLineLength).when(options).maxLineLength();
        return options;
    }

    private Set<File> filterUnchangedFiles(Set<File> originalFiles) throws MojoExecutionException {
        MavenProject topLevelProject = session.getTopLevelProject();
        try {
            if (topLevelProject.getScm().getConnection() == null && topLevelProject.getScm().getDeveloperConnection() == null) {
                throw new MojoExecutionException(
                        "You must supply at least one of scm.connection or scm.developerConnection in your POM file if you " +
                                "specify the filterModified or filter.modified option.");
            }
            String connectionUrl = MoreObjects.firstNonNull(topLevelProject.getScm().getConnection(), topLevelProject.getScm().getDeveloperConnection());
            ScmRepository repository = scmManager.makeScmRepository(connectionUrl);
            ScmFileSet scmFileSet = new ScmFileSet(topLevelProject.getBasedir());
            String basePath = topLevelProject.getBasedir().getAbsoluteFile().getPath();
            List<String> changedFiles =
                    scmManager.status(repository, scmFileSet).getChangedFiles().stream()
                            .map(f -> new File(basePath, f.getPath()).toString())
                            .collect(Collectors.toList());

            return originalFiles.stream().filter(f -> changedFiles.contains(f.getPath())).collect(Collectors.toSet());

        } catch (ScmException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private Set<File> findFilesToReformat(File sourceDirectory, File outputDirectory) throws MojoExecutionException {
        if (sourceDirectory.exists()) {
            try {
                SourceInclusionScanner scanner = getSourceInclusionScanner(includeStale);
                scanner.addSourceMapping(SOURCE_MAPPING);
                Set<File> sourceFiles = scanner.getIncludedSources(sourceDirectory, outputDirectory);
                getLog().info(String.format(Constants.FOUND_UNCOMPILED, sourceFiles.size(), sourceDirectory.getPath()));
                return sourceFiles;
            } catch (InclusionScanException e) {
                throw new MojoExecutionException(String.format(Constants.ERROR_SCANNING_PATH, sourceDirectory.getPath()), e);
            }
        } else {
            getLog().info(String.format(DIRECTORY_MISSING, sourceDirectory.getPath()));
            return Collections.emptySet();
        }
    }

    protected SourceInclusionScanner getSourceInclusionScanner(boolean includeStale) {
        return includeStale ? new SimpleSourceInclusionScanner(Collections.singleton("**/*"), Collections.emptySet()) : new StaleSourceScanner(1024);
    }
}
