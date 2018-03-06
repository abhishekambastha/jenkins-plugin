/*
 * The MIT License
 *
 * Copyright 2014 Rusty Gerard
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cisco.buildanalytics;

import com.google.gson.Gson;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import jenkins.tasks.SimpleBuildStep;
import jenkins.util.VirtualFile;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.util.CollectionUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class BuildAnalyticsNotifier extends Notifier implements SimpleBuildStep {

    private static final Logger LOG = Logger.getLogger(BuildAnalyticsNotifier.class.getName());
    private static String CHARSET = "UTF-8";
    private static final Gson gson = new Gson();

    public String serverIp;
    public String buildStageType;
    public String filebeatsDirectory;
    public String userPrefix;
    public String jenkinsServerIp;
    public boolean uploadOnlyOnFail;
    public boolean failBuild;

    @DataBoundConstructor
    public BuildAnalyticsNotifier(String serverIp, String buildStageType, String filebeatsDirectory, String userPrefix,
                                  String jenkinsServerIp, boolean uploadOblyOnFail, boolean failBuild) {
        this.serverIp = serverIp;
        this.buildStageType = buildStageType;
        this.filebeatsDirectory = filebeatsDirectory;
        this.userPrefix = userPrefix;
        this.jenkinsServerIp = jenkinsServerIp;
        this.uploadOnlyOnFail = uploadOblyOnFail;
        this.failBuild = failBuild;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        return perform(build, listener);
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {
        if (!perform(run, listener)) {
            run.setResult(Result.FAILURE);
        }
    }

    private void createSymbolicLink(Path src, Path target) {
        LOG.info("Creating symbolic link from " + target + " to " + src);
        try {
            Files.createSymbolicLink(src, target);
        } catch (IOException e) {
            LOG.info(e.toString());
        }
    }

    private void invokeAnalyticsAPI(Run<?, ?> run, String log_index) {
        BuildParamsDTO dto = new BuildParamsDTO();

        dto.setBuild_type(this.buildStageType);
        dto.setBuild_log_index(log_index);
        dto.setJenkins_base_url(this.jenkinsServerIp);
        dto.setBuild_user_prefix(this.userPrefix);
        dto.setBuild_type(this.buildStageType);

        List<Cause> causes = run.getCauses();
        StringBuilder causeStr = new StringBuilder();
        if (!CollectionUtils.isEmpty(causes)) {
            for (Cause c : causes) {
                causeStr.append(c.toString());
                causeStr.append(".");
                LOG.info("Cause: " + c.toString());
            }
        }
        dto.setBuild_cause(causeStr.toString());

        dto.setBuild_name(run.getFullDisplayName());
        dto.setBuild_number(run.getNumber());

        dto.setBuild_url(run.getUrl());

        //Artifacts
        List<String> artifacts = new ArrayList<>();
        try {
            LOG.info("artifact root" + run.pickArtifactManager().root());
            for (VirtualFile l : run.pickArtifactManager().root().list()) {
                LOG.info("File Name" + l.getName());
                if(l.isDirectory()){
                    for(VirtualFile ff: l.list()){
                        artifacts.add(ff.getName() + "+" + ff.toURI());
                    }
                }else {
                    artifacts.add(l.getName() + "+" + l.toURI());
                }
            }
        } catch (IOException e) {
            LOG.info("error!!");
        }

        dto.setArtifactList(artifacts);

        String result = gson.toJson(dto);

        LOG.info("DTO: " + result);
        postRequest(result);
    }

    private void postRequest(String result) {
        try {
            URL url = new URL(this.serverIp);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            OutputStream os = conn.getOutputStream();
            os.write(result.getBytes());
            os.flush();

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));

            String output;
            LOG.info("Output from server");
            while ((output = br.readLine()) != null) {
                LOG.info(output);
            }

            conn.disconnect();
        } catch (MalformedURLException e) {
            LOG.warning(e.toString());
        } catch (IOException e) {
            LOG.warning(e.toString());
        }

    }

    private boolean perform(Run<?, ?> run, TaskListener listener) {
        LOG.info("Build Analytics Plugin - Running");
        LOG.info("Parameters " + this.serverIp + this.uploadOnlyOnFail + this.buildStageType + this.jenkinsServerIp
                + this.userPrefix);

        boolean success = false;
        Result r = run.getResult();
        if (r != null) {
            success = r.isCompleteBuild();
        } else {
            LOG.info("Result is null" + r.toString());
        }

        if (!this.uploadOnlyOnFail || (this.uploadOnlyOnFail && !success)) {
            File file = run.getLogFile();
            String filename = this.userPrefix + "-x-" + this.buildStageType + "-x-" + run.getId();
            Path newLink = Paths.get(this.filebeatsDirectory + "/" + filename + ".log");
            Path target = Paths.get(file.getAbsolutePath());
            createSymbolicLink(newLink, target);

            String buildUrl = run.getUrl();

            invokeAnalyticsAPI(run, filename);
        } else {
            LOG.info("Skipping upload as requested");
        }

        return !(failBuild);
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public Descriptor getDescriptor() {
        return (Descriptor) super.getDescriptor();
    }

    @Extension
    @Symbol("logstashSend")
    public static class Descriptor extends BuildStepDescriptor<Publisher> {

        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return true;
        }

        public String getDisplayName() {
            return "Upload Logs For Analysis";
        }
    }
}
