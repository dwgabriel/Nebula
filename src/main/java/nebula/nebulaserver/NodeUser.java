package nebula.nebulaserver;

import java.util.LinkedHashMap;


public class NodeUser {

        private String nodeEmail;
        private LinkedHashMap<String, Node> nodesMap = new LinkedHashMap<>();
        private LinkedHashMap<String, ResultReceiver.SubtaskCosts> nodeUserCompletedSubtasksMap = new LinkedHashMap<>();

        public NodeUser(String nodeEmail) {
            this.nodeEmail = nodeEmail;
        }

        public String getNodeEmail() {
            return nodeEmail;
        }

        public LinkedHashMap getNodesMap() {
            return nodesMap;
        }

        public LinkedHashMap<String, ResultReceiver.SubtaskCosts> getAllCompletedSubtasks() {
            return nodeUserCompletedSubtasksMap;
        }

        public void addNode(Node newNode) {
            boolean nodeExists = false;
            Node node = null;

            if (nodesMap.get(newNode.getIpAddress()) == null) {
                nodesMap.put(newNode.getIpAddress(), newNode);
            } else {
                System.out.println("[ERROR] Node IP (" + newNode.getIpAddress() + ") already exists.");
            }

            System.out.println("CHECK | " + nodeEmail + " Nodes Size : " + nodesMap.size());
        }

        // addToCompletedSubtasks adds completed subtasks and its details to the NodeUser that completed it for reference purposes.
        //  It also calls the addSubtaskToNode method to credit the completed subtask to the specific Node that completed it.
        public void addToCompletedSubtasks(String deviceID, String ipAddress, ResultReceiver.SubtaskCosts subtaskCost) {
        Node node = nodesMap.get(ipAddress);
        String subtaskID = subtaskCost.subtaskID;
        if (nodeUserCompletedSubtasksMap.get(subtaskID) == null) {
            nodeUserCompletedSubtasksMap.put(subtaskID, subtaskCost);
            System.out.println(subtaskCost.subtaskID + " added to " + nodeEmail + "'s list of Completed Subtasks." );

            if (node == null) {
                node = new Node(nodeEmail, "1.0.x", deviceID, "0", ipAddress);
                addNode(node);
            }
            node.addSubtasksToNode(subtaskCost);
            }
        }

        public class Node {

            private String nodeEmail;
            private String productVersion;
            private String deviceID;
            private String score;
            private String ipAddress;
            private int nodeQueue;
            private LinkedHashMap<String, ResultReceiver.SubtaskCosts> nodeCompletedSubtasksMap = new LinkedHashMap<>();

            public Node(String nodeEmail
                    , String productVersion
                    , String deviceID
                    , String score
                    , String ipAddress) {

                this.nodeEmail = nodeEmail;
                this.productVersion = productVersion;
                this.deviceID = deviceID;
                this.score = score;
                this.ipAddress = ipAddress;
            }

            public void addSubtasksToNode(ResultReceiver.SubtaskCosts subtaskCost) {

                if (nodeCompletedSubtasksMap.isEmpty()) {
                    nodeCompletedSubtasksMap.put(subtaskCost.subtaskID, subtaskCost);
                    System.out.println(subtaskCost.subtaskID + " added to " + nodeEmail + "'s [" + ipAddress + "] list of Completed Subtasks." );

                } else {
                    if (nodeCompletedSubtasksMap.get(subtaskCost.subtaskID) != null) {

                        System.out.println("ERROR | " + subtaskCost.subtaskID + " already exists. (Line 120)");
                    } else {
                        nodeCompletedSubtasksMap.put(subtaskCost.subtaskID, subtaskCost);
                        System.out.println(subtaskCost.subtaskID + " added to " + nodeEmail + "'s [" + ipAddress + "] list of Completed Subtasks." );
                    }
                }

                System.out.println("CHECK | Completed Subtasks for " + nodeEmail + " [" + ipAddress + "] : " + nodeCompletedSubtasksMap.size());
            }

            public LinkedHashMap<String, ResultReceiver.SubtaskCosts> getCompletedSubtasks() {
                return nodeCompletedSubtasksMap;
            }

            public String getNodeEmail() {
                return nodeEmail;
            }

            public String getProductVersion() {
                return productVersion;
            }

            public String getDeviceID() {
                return deviceID;
            }

            public String getScore() {
                return score;
            }

            public String getIpAddress() {
                return ipAddress;
            }

            public void setNodeQueue(int nodeQueue) {
                this.nodeQueue = nodeQueue;
            }

            public int getNodeQueue() {
                return nodeQueue;
            }
        }
    }

