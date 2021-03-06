package com.containersol.minimesos;

import com.containersol.minimesos.cluster.MesosCluster;
import com.containersol.minimesos.docker.DockerContainersUtil;
import com.containersol.minimesos.mesos.*;
import com.github.dockerjava.api.DockerClient;
import org.junit.Test;

import java.util.TreeMap;

import static org.junit.Assert.*;

public class DynamicClusterTest {

    private static final boolean EXPOSED_PORTS = false;

    protected static final String resources = MesosAgent.DEFAULT_PORT_RESOURCES + "; cpus(*):0.2; mem(*):256; disk(*):200";
    protected static final DockerClient dockerClient = DockerClientFactory.build();

    @Test
    public void noMarathonTest() {

        ClusterArchitecture config = new ClusterArchitecture.Builder(dockerClient)
                .withZooKeeper()
                .withMaster(zooKeeper -> new MesosMasterExtended(dockerClient, zooKeeper, MesosMaster.MESOS_MASTER_IMAGE, MesosContainer.MESOS_IMAGE_TAG, new TreeMap<>(), EXPOSED_PORTS ))
                .withAgent(zooKeeper -> new MesosAgent(dockerClient, resources, 5051, zooKeeper, MesosAgent.MESOS_AGENT_IMAGE, MesosContainer.MESOS_IMAGE_TAG))
                .build();

        MesosCluster cluster = new MesosCluster(config);

        cluster.start();
        String clusterId = cluster.getClusterId();

        assertNotNull( "Cluster ID must be set", clusterId );

        // this should not throw any exceptions
        cluster.destroy();

    }

    @Test
    public void stopWithNewContainerTest() {

        ClusterArchitecture config = new ClusterArchitecture.Builder(dockerClient)
                .withZooKeeper()
                .withMaster(zooKeeper -> new MesosMasterExtended(dockerClient, zooKeeper, MesosMaster.MESOS_MASTER_IMAGE, MesosContainer.MESOS_IMAGE_TAG, new TreeMap<>(), EXPOSED_PORTS ))
                .withAgent(zooKeeper -> new MesosAgent(dockerClient, resources, 5051, zooKeeper, MesosAgent.MESOS_AGENT_IMAGE, MesosContainer.MESOS_IMAGE_TAG))
                .build();

        MesosCluster cluster = new MesosCluster(config);
        cluster.start();

        ZooKeeper zooKeeper = cluster.getZkContainer();
        MesosAgent extraAgent = new MesosAgent(dockerClient, resources, 5051, zooKeeper, MesosAgent.MESOS_AGENT_IMAGE, MesosContainer.MESOS_IMAGE_TAG);

        String containerId = cluster.addAndStartContainer(extraAgent);
        assertNotNull("freshly started container is not found", DockerContainersUtil.getContainer(dockerClient, containerId));

        cluster.stop();
        assertNull("new container should be stopped too", DockerContainersUtil.getContainer(dockerClient, containerId));

    }

}
