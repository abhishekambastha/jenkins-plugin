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
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.logging.Logger;

public class BuildAnalyticsNotifier extends Notifier implements SimpleBuildStep {

	private static final Logger LOG = Logger.getLogger(BuildAnalyticsNotifier.class.getName());
	private static String CHARSET = "UTF-8";
	private static final Gson gson = new Gson();

	public String serverIp;
	public String buildStageType;
	public String filebeatsDirectory;
	public String userPrefix;
	public String jenkinsServer;
	public boolean uploadOnlyOnFail;
	public boolean failBuild;

	@DataBoundConstructor
	public BuildAnalyticsNotifier(String serverIp, String buildStageType, String filebeatsDirectory, String userPrefix,
								  String jenkinsServer, boolean uploadOblyOnFail, boolean failBuild) {
		this.serverIp = serverIp;
		this.buildStageType = buildStageType;
		this.filebeatsDirectory = filebeatsDirectory;
		this.userPrefix = userPrefix;
		this.jenkinsServer = jenkinsServer;
		this.uploadOnlyOnFail = uploadOblyOnFail;
		this.failBuild = failBuild;
	}

	@Override
	public boolean needsToRunAfterFinalized() {
		return true;
	}

	@Override
	public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
			throws InterruptedException, IOException {
		if (!perform(run, workspace, listener)) {
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

	private void invokeAnalyticsAPI(String buildUrl, String filename, String buildDisplayName, int buildNumber) {
		BuildParamsDTO dto = new BuildParamsDTO();

		dto.setBuildStageType(this.buildStageType);
		dto.setJenkinsServer(this.jenkinsServer);
		dto.setPrefixUser(this.userPrefix);
		dto.setFileName(filename);
		dto.setBuildUrl(buildUrl);
		dto.setBuildDisplayName(buildDisplayName);
		dto.setBuildNumber(buildNumber);

		String result = gson.toJson(dto);
		LOG.info("DTO: " + result);

		// Post to Build Analytics Service
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

	private boolean perform(Run<?, ?> run, FilePath workspace, TaskListener listener) {
		LOG.info("Build Analytics Plugin - Running");
		LOG.info("Parameters:  " + this.serverIp + ", " + this.uploadOnlyOnFail + ", " + this.buildStageType
				+ ", " + this.userPrefix);
		boolean success = false;
		Result r = run.getResult();
		if (r != null) {
			success = r.isCompleteBuild();
		} else {
			LOG.info("Result is null" + r.toString());
		}

		if (!this.uploadOnlyOnFail || (this.uploadOnlyOnFail && !success)) {
			File file = run.getLogFile();
			String filename = this.userPrefix + "_" + this.buildStageType + "_" + run.getId() + "_" +
					Long.toString(Instant.now().getEpochSecond()) + "_bap.log";

			// Writes Console Log to the Workspace
			ConsoleLogToFilebeats.perform(run, workspace, listener,
					filename, this.filebeatsDirectory);

			invokeAnalyticsAPI(run.getUrl(), filename, run.getFullDisplayName(), run.getNumber());
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
