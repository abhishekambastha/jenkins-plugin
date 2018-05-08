package com.cisco.buildanalytics;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.console.AnnotatedLargeText;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;

public class ConsoleLogToFilebeats {

    private static final Logger LOG = Logger.getLogger(BuildAnalyticsNotifier.class.getName());
    /**
     * Function to write console logs to filebeats directory
     *
     * @param build                 Jenkins Build Instance
     * @param workspace             Build Job Workspace
     * @param listener              Task Listener
     * @param fileName              Console log file
     * @param filebeatsDirectory    Filebeats directory to write logs to
     */
    public static boolean perform(Run<?, ?> build, FilePath workspace, TaskListener listener,
                                  String fileName, String filebeatsDirectory) {
        final OutputStream os;

        try {
            final EnvVars env = build.getEnvironment(listener);
            fileName=env.expand(fileName);

            log("wrote to bap.log", listener);

            // Create and Cleans Filebeats Directory
            // FilePath consoleFilePath = createAndCleanFilebeatsDir(filebeatsDirectory);

            // Writes Console log to Filebeats Directory in the workspace
            os = workspace.child(fileName).write();
            writeLogFile(build.getLogText(), os);
            os.close();
            log("wrote console log to workspace file " + fileName + " successfully", listener);
        } catch (IOException |InterruptedException e) {
            build.setResult(Result.UNSTABLE);
            log("writing to console log to workspace file " + fileName + " failed", listener);
        }
        return true;
    }

    /**
     * Function to write message to Console Log
     *
     * @param message   Message to write to console log file
     * @param listener  Task Listener
     */
    private static void log(String message, TaskListener listener) {
        listener.getLogger().println("BuildAnalyticsPlatform " + message);
    }

    /**
     * Function to write create Filebeats directory if doesn't exist
     * and cleans up *bap.log files older than 2 days
     *
     * @param filebeatsDirectory   Filebeats Driectory to write console logs
     */
    private static FilePath createAndCleanFilebeatsDir(String filebeatsDirectory) throws IOException,
            InterruptedException {

        File filebeatsDir = new File(filebeatsDirectory);

        // Checks if Filebeats Directory already exists
        if (filebeatsDir.exists()) {

            File[] listFiles = filebeatsDir.listFiles();
            long eligibleForDeletion = System.currentTimeMillis() - (2 * 24 * 60 * 60 * 1000);
            for (File file: listFiles) {

                // Deletes any files bap.log older than 2 days
                if (file.getName().endsWith("bap.log") && file.lastModified() < eligibleForDeletion) {
                    LOG.info("File Found for Deletion: " + file.toString());
                    if (!file.delete()) {
                        LOG.info("Failed to Delete File: " + file.toString());
                    };
                }
            }
        } else {
            // Create Filebeats directory
            if (!filebeatsDir.mkdirs()) {
                LOG.info("Failed to create BAP log directory: " + filebeatsDir.toString());
            }
        }

        return new FilePath(filebeatsDir);
    }

    /**
     * Function to write console log to a file
     *
     * @param logText   Console Log Text
     * @param out       OutputStream onject
     */
    private static void writeLogFile(AnnotatedLargeText logText,
                                     OutputStream out) throws IOException, InterruptedException {
        long pos = 0;
        long prevPos = pos;
        do {
            prevPos = pos;
            pos = logText.writeLogTo(pos, out);

            if (prevPos >= pos) {
                break;
            }
            Thread.sleep(1000);
        } while(true);
    }
}
