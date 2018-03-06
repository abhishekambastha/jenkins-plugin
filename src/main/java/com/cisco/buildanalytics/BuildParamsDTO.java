package com.cisco.buildanalytics;

import java.util.List;

public class BuildParamsDTO {

    private String jenkins_base_url;
    private String build_type;
    private String build_name;
    private String build_cause;
    private int build_number;
    private String build_user_prefix;
    private String build_log_index;
    private String build_url;
    private List<String> artifactList;

    public String getJenkins_base_url() {
        return jenkins_base_url;
    }

    public void setJenkins_base_url(String jenkins_base_url) {
        this.jenkins_base_url = jenkins_base_url;
    }

    public String getBuild_type() {
        return build_type;
    }

    public void setBuild_type(String build_type) {
        this.build_type = build_type;
    }

    public String getBuild_name() {
        return build_name;
    }

    public void setBuild_name(String build_name) {
        this.build_name = build_name;
    }

    public String getBuild_cause() {
        return build_cause;
    }

    public void setBuild_cause(String build_cause) {
        this.build_cause = build_cause;
    }

    public int getBuild_number() {
        return build_number;
    }

    public void setBuild_number(int build_number) {
        this.build_number = build_number;
    }

    public String getBuild_user_prefix() {
        return build_user_prefix;
    }

    public void setBuild_user_prefix(String build_user_prefix) {
        this.build_user_prefix = build_user_prefix;
    }

    public String getBuild_log_index() {
        return build_log_index;
    }

    public void setBuild_log_index(String build_log_index) {
        this.build_log_index = build_log_index;
    }

    public List<String> getArtifactList() {
        return artifactList;
    }

    public void setArtifactList(List<String> artifactList) {
        this.artifactList = artifactList;
    }

    public String getBuild_url() {
        return build_url;
    }

    public void setBuild_url(String build_url) {
        this.build_url = build_url;
    }
}
