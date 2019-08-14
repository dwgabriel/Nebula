package nebula.nebulaserver;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;

import java.io.IOException;
import java.util.ArrayList;

//import javax.servlet.http.HttpServlet;

/**
 * Created by Daryl Wong on 3/29/2019.
 */

//
//@WebServlet(
//        name = "com.nebula.Test",
//        urlPatterns = {"/test"}
//)

public class Test {

    public static void main(String[] arg) throws IOException {


        String output = "/c/users/daryl wong/Desktop/Nebula/Code/nebulaserver/nebuladatabase/tasks/054873/originaltask/";
        String originalTaskFile = "originaltask/blendertest.blend";
        String tileScript = "originaltask/054873_tileScript__001.py";

//        docker create --name tester123 -v /c/users/daryl/desktop/test:/test ikester/blender test/bmwgpu.blend --python test/thescript.py -o test/frame_### -f 1

        ArrayList<String> blenderCL = new ArrayList<>();

        blenderCL.add(0, "bin/bash");
        blenderCL.add(0, "-it");
        blenderCL.add(1, "--rm");
        blenderCL.add(2, "-v");
//        blenderCL.add(3, bindVolume);
        blenderCL.add(4, "ikester/blender");
        blenderCL.add(5, originalTaskFile);
        blenderCL.add(6, "--python");
        blenderCL.add(7, tileScript);
        blenderCL.add(8, "-o");
        blenderCL.add(9, output);
        blenderCL.add(10, "-f");
        blenderCL.add(11, "1");

            String commandLine = String.format(blenderCL.get(0) + " "
                    + blenderCL.get(1) + " "
                    + blenderCL.get(2) + " "
                    + blenderCL.get(3) + " "
                    + blenderCL.get(4) + " "
                    + blenderCL.get(5) + " "
                    + blenderCL.get(6) + " "
                    + blenderCL.get(7) + " "
                    + blenderCL.get(8) + " "
                    + blenderCL.get(9) + " "
                    + blenderCL.get(10) + " "
                    + blenderCL.get(11));

            String command = String.format(blenderCL.toString());

        try {
            final DockerClient docker = DefaultDockerClient.fromEnv().build();

//            docker.pull("ikester/blender");
//            final String[] ports = {"8080"};
//            final Map<String, List<PortBinding>> portBindings = new HashMap<>();
//            for (String port : ports) {
//                List<PortBinding> hostPorts = new ArrayList<>();
//                hostPorts.add(PortBinding.of("0.0.0.0", port));
//                portBindings.put(port, hostPorts);
//            }
//
//            List<PortBinding> randomPort = new ArrayList<>();
//            randomPort.add(PortBinding.randomPort("0.0.0.0"));
//            portBindings.put("443", randomPort);

            final HostConfig hostConfig = HostConfig.builder()
//                    .portBindings(portBindings)
                    .appendBinds("/c/users/daryl/desktop/googledrive/render:/render").readonlyRootfs(false)
                    .build();

            final ContainerCreation container = docker.createContainer(ContainerConfig.builder()
                    .hostConfig(hostConfig)
                    .image("ikester/blender")
                    .cmd("render/blendfile.blend"
                            , "--python", "render/thescript.py"
                            , "-o", "render/frame_###"
                            , "-f", "-1")
                    .build());




        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
