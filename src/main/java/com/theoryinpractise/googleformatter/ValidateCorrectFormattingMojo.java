package com.theoryinpractise.googleformatter;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;

@Mojo(name = "check", defaultPhase = LifecyclePhase.VALIDATE)
public class ValidateCorrectFormattingMojo extends AbstractFormatter {
    @Override
    void handleFormattedSource(File file, String formattedSource) throws MojoExecutionException {
        throw new MojoExecutionException("Project needs formatting, please run mvn process-resources");
    }
}
