package com.containersol.minimesos.mesos;

import com.containersol.minimesos.container.AbstractContainer;
import com.github.dockerjava.api.DockerClient;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONObject;

import java.util.TreeMap;

/**
 * Superclass for Mesos images
 */
public abstract class MesosContainer extends AbstractContainer {

    public static final String MESOS_IMAGE_TAG = "0.25.0-0.2.70.ubuntu1404";
    public static final String DEFAULT_MESOS_ZK_PATH = "/mesos";

    private String mesosImageTag = MESOS_IMAGE_TAG;

    protected ZooKeeper zooKeeperContainer;

    protected MesosContainer(DockerClient dockerClient, ZooKeeper zooKeeperContainer) {
        super(dockerClient);
        this.zooKeeperContainer = zooKeeperContainer;
    }

    public MesosContainer(DockerClient dockerClient, String clusterId, String uuid, String containerId) {
        super(dockerClient, clusterId, uuid, containerId);
    }

    public abstract String getMesosImageName();
    public abstract int getPortNumber();

    protected abstract TreeMap<String, String> getDefaultEnvVars();

    @Override
    protected void pullImage() {
        pullImage(getMesosImageName(), getMesosImageTag());
    }

    public String getMesosImageTag() {
        return mesosImageTag;
    }

    public void setMesosImageTag(String mesosImageTag) {
        this.mesosImageTag = mesosImageTag;
    }

    protected String[] createMesosLocalEnvironment() {
        TreeMap<String, String> map = getDefaultEnvVars();
        map.putAll(getSharedEnvVars());
        return map.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toArray(String[]::new);
    }

    protected TreeMap<String, String> getSharedEnvVars() {
        TreeMap<String,String> envs = new TreeMap<>();
        envs.put("GLOG_v", "1");
        envs.put("MESOS_EXECUTOR_REGISTRATION_TIMEOUT", "5mins");
        envs.put("MESOS_CONTAINERIZERS", "docker,mesos");
        envs.put("MESOS_ISOLATOR", "cgroups/cpu,cgroups/mem");
        envs.put("MESOS_LOG_DIR", "/var/log");
        envs.put("MESOS_LOGGING_LEVEL", "INFO");
        envs.put("MESOS_WORK_DIR", "/tmp/mesos");
        return envs;
    }

    public void setZooKeeperContainer(ZooKeeper zooKeeperContainer) {
        this.zooKeeperContainer = zooKeeperContainer;
    }

    public ZooKeeper getZooKeeperContainer() {
        return zooKeeperContainer;
    }

    public String getFormattedZKAddress() {
        return zooKeeperContainer.getFormattedZKAddress() + DEFAULT_MESOS_ZK_PATH;
    }

    public String getStateUrl() {
        return "http://" + getIpAddress() + ":" + getPortNumber() + "/state.json";
    }

    public JSONObject getStateInfoJSON() throws UnirestException {
        return Unirest.get(getStateUrl()).asJson().getBody().getObject();
    }

}
