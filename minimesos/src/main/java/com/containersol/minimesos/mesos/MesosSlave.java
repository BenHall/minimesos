package com.containersol.minimesos.mesos;

import com.containersol.minimesos.MesosCluster;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Base MesosSlave class
 */
public class MesosSlave extends MesosContainer {
    private static final Logger LOGGER = Logger.getLogger(MesosSlave.class);
    public static final String MESOS_SLAVE_IMAGE = "containersol/mesos-agent";
    public static final int MESOS_SLAVE_PORT = 5051;
    public static final String DEFAULT_PORT_RESOURCES = "ports(*):[31000-32000]";
    public static final String DEFAULT_RESOURCES = DEFAULT_PORT_RESOURCES + "; cpus(*):0.2; mem(*):256; disk(*):200";

    protected MesosSlave(DockerClient dockerClient, ZooKeeper zooKeeperContainer) {
        super(dockerClient, zooKeeperContainer);
    }

    @Override
    public String getMesosImageName() {
        return MESOS_SLAVE_IMAGE;
    }

    @Override
    protected CreateContainerCmd dockerCommand() {
        ArrayList<ExposedPort> exposedPorts= new ArrayList<>();
        exposedPorts.add(new ExposedPort(MESOS_SLAVE_PORT));
        try {
            ArrayList<Integer> resourcePorts = parsePortsFromResource(DEFAULT_PORT_RESOURCES);
            for (Integer port : resourcePorts) {
                exposedPorts.add(new ExposedPort(port));
            }
        } catch (Exception e) {
            LOGGER.error("Port binding is incorrect: " + e.getMessage());
        }

        String dockerBin = "/usr/bin/docker";
        File dockerBinFile = new File(dockerBin);
        if (!(dockerBinFile.exists() && dockerBinFile.canExecute())) {
            dockerBin = "/usr/local/bin/docker";
            dockerBinFile = new File(dockerBin);
            if (!(dockerBinFile.exists() && dockerBinFile.canExecute() )) {
                LOGGER.error("Docker binary not found in /usr/local/bin or /usr/bin. Creating containers will most likely fail.");
            }
        }

        return dockerClient.createContainerCmd(MESOS_SLAVE_IMAGE + ":" + MesosMaster.MESOS_IMAGE_TAG)
                .withName("minimesos-agent-" + MesosCluster.getClusterId() + "-" + getRandomId())
                .withPrivileged(true)
                .withEnv(createMesosLocalEnvironment())
                .withPid("host")
                .withBinds(
                        Bind.parse("/var/lib/docker:/var/lib/docker"),
                        Bind.parse("/sys/fs/cgroup:/sys/fs/cgroup"),
                        Bind.parse(String.format("%s:/usr/bin/docker", dockerBin)),
                        Bind.parse("/var/run/docker.sock:/var/run/docker.sock")
                )
                .withExposedPorts(exposedPorts.toArray(new ExposedPort[exposedPorts.size()]))
                .withLabels(DEFAULT_LABELS);
    }

    public static ArrayList<Integer> parsePortsFromResource(String resources) throws Exception {
        String port = resources.replaceAll(".*ports\\(.+\\):\\[(.*)\\].*", "$1");
        ArrayList<String> ports = new ArrayList<>(Arrays.asList(port.split(",")));
        ArrayList<Integer> returnList = new ArrayList<>();
        for (String el : ports) {
            String firstPortFromBinding = el.trim().split("-")[0];
            if (Objects.equals(firstPortFromBinding, el.trim())) {
                throw new Exception("Port binding " + firstPortFromBinding + " is incorrect");
            }
            returnList.add(Integer.parseInt(firstPortFromBinding)); // XXXX-YYYY will return XXXX
        }
        return returnList;
    }

    @Override
    public TreeMap<String, String> getDefaultEnvVars() {
        TreeMap<String,String> envs = new TreeMap<>();
        envs.put("MESOS_RESOURCES", DEFAULT_RESOURCES);
        envs.put("MESOS_PORT", String.valueOf(MESOS_SLAVE_PORT));
        envs.put("MESOS_MASTER", getFormattedZKAddress());
        envs.put("MESOS_SWITCH_USER", "false");
        return envs;
    }
}
