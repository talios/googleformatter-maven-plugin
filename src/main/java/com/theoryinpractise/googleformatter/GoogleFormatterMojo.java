package com.theoryinpractise.googleformatter;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/** Reformat all source files using the Google Code Formatter */
@Mojo(name = "format", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class GoogleFormatterMojo extends AbstractFormatter {

    @Override
    public void handleFormattedSource(File file, String formattedSource) throws MojoExecutionException {
        // overwrite existing file
        try {
            Files.write(file.toPath(), formattedSource.getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        getLog().info(String.format("Reformatted file %s", file.getPath()));
    }
}
