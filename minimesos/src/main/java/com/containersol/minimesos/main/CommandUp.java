package com.containersol.minimesos.main;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.containersol.minimesos.cluster.ClusterRepository;
import com.containersol.minimesos.cluster.MesosCluster;
import com.containersol.minimesos.marathon.Marathon;
import com.containersol.minimesos.mesos.ClusterArchitecture;
import com.containersol.minimesos.mesos.ClusterContainers;
import com.containersol.minimesos.mesos.DockerClientFactory;
import com.containersol.minimesos.mesos.MesosContainer;
import com.containersol.minimesos.mesos.MesosMaster;
import com.containersol.minimesos.mesos.MesosMasterExtended;
import com.containersol.minimesos.mesos.MesosAgent;
import com.containersol.minimesos.mesos.ZooKeeper;
import com.github.dockerjava.api.DockerClient;

import java.io.PrintStream;
import java.util.TreeMap;

/**
 * Parameters for the 'up' command
 */
@Parameters(separators = "=", commandDescription = "Create a minimesos cluster")
public class CommandUp implements Command {

    public static final String CLINAME = "up";

    @Parameter(names = "--exposedHostPorts", description = "Expose the Mesos and Marathon UI ports on the host level (we recommend to enable this on Mac (e.g. when using docker-machine) and disable on Linux).")
    private boolean exposedHostPorts = false;

    @Parameter(names = "--marathonImageTag", description = "The tag of the Marathon Docker image.")
    private String marathonImageTag = Marathon.MARATHON_IMAGE_TAG;

    @Parameter(names = "--mesosImageTag", description = "The tag of the Mesos master and agent Docker images.")
    private String mesosImageTag = MesosContainer.MESOS_IMAGE_TAG;

    @Parameter(names = "--zooKeeperImageTag", description = "The tag of the ZooKeeper Docker images.")
    private String zooKeeperImageTag = ZooKeeper.ZOOKEEPER_IMAGE_TAG;

    public String getMarathonImageTag() {
        return marathonImageTag;
    }

    @Parameter(names = "--timeout", description = "Time to wait for a container to get responsive, in seconds.")
    private int timeout = MesosCluster.DEFAULT_TIMEOUT_SECS;

    @Parameter(names = "--num-agents", description = "Number of agents to start")
    private int numAgents = 1;

    @Parameter(names = "--consul", description = "Start consul container")
    private boolean startConsul = false;

    private MesosCluster startedCluster = null;
    private PrintStream output = System.out;

    public CommandUp() {
    }

    public CommandUp(PrintStream ps) {
        output = ps;
    }

    public String getMesosImageTag() {
        return mesosImageTag;
    }

    public String getZooKeeperImageTag() {
        return zooKeeperImageTag;
    }

    public boolean isExposedHostPorts() {
        return exposedHostPorts;
    }

    public int getTimeout() {
        return timeout;
    }

    public int getNumAgents() {
        return numAgents;
    }

    public boolean getStartConsul() {
        return startConsul;
    }

    @Override
    public void execute() {

        MesosCluster cluster = getCluster();
        if (cluster != null) {
            output.println("Cluster " + cluster.getClusterId() + " is already running");
            return;
        }

        DockerClient dockerClient = DockerClientFactory.build();

        ClusterArchitecture.Builder configBuilder = new ClusterArchitecture.Builder(dockerClient)
                .withZooKeeper(getZooKeeperImageTag())
                .withMaster(zooKeeper -> new MesosMasterExtended(dockerClient, zooKeeper, MesosMaster.MESOS_MASTER_IMAGE, getMesosImageTag(), new TreeMap<>(), isExposedHostPorts()))
                .withContainer(zooKeeper -> new Marathon(dockerClient, zooKeeper, getMarathonImageTag(), isExposedHostPorts()), ClusterContainers.Filter.zooKeeper());

        for (int i = 0; i < getNumAgents(); i++) {
            configBuilder.withAgent(zooKeeper -> new MesosAgent(dockerClient, "ports(*):[33000-34000]", 5051, zooKeeper, MesosAgent.MESOS_AGENT_IMAGE, getMesosImageTag()));
        }

        if (getStartConsul()) {
            configBuilder.withConsul();
        }

        startedCluster = new MesosCluster(configBuilder.build());
        startedCluster.start(getTimeout());
        startedCluster.waitForState(state -> state != null, 60);
        startedCluster.setExposedHostPorts( isExposedHostPorts() );

        startedCluster.printServiceUrls(output);

        ClusterRepository.saveClusterFile(startedCluster);

    }

    public MesosCluster getCluster() {
        return (startedCluster != null) ? startedCluster : ClusterRepository.loadCluster();
    }

    @Override
    public boolean validateParameters() {
        return true;
    }

    @Override
    public String getName() {
        return CLINAME;
    }

}


