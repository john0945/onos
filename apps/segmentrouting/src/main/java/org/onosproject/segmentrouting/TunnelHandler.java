/*
 * Copyright 2015-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*

Notes for porting of multiple label support from spring-open project.
This file is called SegmentRoutingTennel in spring-open.

DeviceID replaces Dpid

 */
package org.onosproject.segmentrouting;

import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.link.LinkService;
import org.onosproject.segmentrouting.config.DeviceConfiguration;
import org.onosproject.segmentrouting.grouphandler.DefaultGroupHandler;
import org.onosproject.segmentrouting.grouphandler.NeighborSet;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Tunnel Handler.
 */
public class TunnelHandler {
    protected final Logger log = getLogger(getClass());

    private final DeviceConfiguration config;
    private final EventuallyConsistentMap<String, Tunnel> tunnelStore;
    private Map<DeviceId, DefaultGroupHandler> groupHandlerMap;
    private LinkService linkService;

    private List<Integer> stitchedGroupIDs;

    private List<TunnelRouteInfo> routes;
   // private final int max_num_labels = 3;

    /**
     * Result of tunnel creation or removal.
     */
    public enum Result {
        /**
         * Success.
         */
        SUCCESS,

        /**
         * More than one router needs to specified to created a tunnel.
         */
        WRONG_PATH,

        /**
         * The same tunnel exists already.
         */
        TUNNEL_EXISTS,

        /**
         * The same tunnel ID exists already.
         */
        ID_EXISTS,

        /**
         * Tunnel not found.
         */
        TUNNEL_NOT_FOUND,

        /**
         * Cannot remove the tunnel used by a policy.
         */
        TUNNEL_IN_USE,

        /**
         * Failed to create/remove groups for the tunnel.
         */
        INTERNAL_ERROR
    }

    /**
     * Constructs tunnel handler.
     *
     * @param linkService link service
     * @param deviceConfiguration device configuration
     * @param groupHandlerMap group handler map
     * @param tunnelStore tunnel store
     */
    public TunnelHandler(LinkService linkService,
                         DeviceConfiguration deviceConfiguration,
                         Map<DeviceId, DefaultGroupHandler> groupHandlerMap,
                         EventuallyConsistentMap<String, Tunnel> tunnelStore) {
        this.linkService = linkService;
        this.config = deviceConfiguration;
        this.groupHandlerMap = groupHandlerMap;
        this.tunnelStore = tunnelStore;
    }

    /**
     * Creates a tunnel.
     *
     * @param tunnel tunnel reference to create a tunnel
     * @return WRONG_PATH if the tunnel path is wrong, ID_EXISTS if the tunnel ID
     * exists already, TUNNEL_EXISTS if the same tunnel exists, INTERNAL_ERROR
     * if the tunnel creation failed internally, SUCCESS if the tunnel is created
     * successfully
     */
    public Result createTunnel(Tunnel tunnel) {

        if (tunnel.labelIds().isEmpty() || tunnel.labelIds().size() < 3) {
            log.error("More than one router needs to specified to created a tunnel");
            return Result.WRONG_PATH;
        }

        if (tunnelStore.containsKey(tunnel.id())) {
            log.warn("The same tunnel ID exists already");
            return Result.ID_EXISTS;
        }

        if (tunnelStore.containsValue(tunnel)) {
            log.warn("The same tunnel exists already");
            return Result.TUNNEL_EXISTS;
        }

        if (tunnel.labelIds().size() == 3) {
            int groupId = createGroupsForTunnel(tunnel);
            if (groupId < 0) {
                log.error("Failed to create groups for the tunnel");
                return Result.INTERNAL_ERROR;
            }

            tunnel.setGroupId(groupId);
            tunnelStore.put(tunnel.id(), tunnel);

            return Result.SUCCESS;

        } else {
            stitchedGroupIDs = createStitchedGroupsForTunnel(tunnel);

            for (int groupId : stitchedGroupIDs) {
                tunnel.addStitchedGroupIDs(groupId);
            }
            tunnelStore.put(tunnel.id(), tunnel);

            return Result.SUCCESS;

        }
    }

    /**
     * Removes the tunnel with the tunnel ID given.
     *
     * @param tunnelInfo tunnel information to delete tunnels
     * @return TUNNEL_NOT_FOUND if the tunnel to remove does not exists,
     * INTERNAL_ERROR if the tunnel creation failed internally, SUCCESS
     * if the tunnel is created successfully.
     */
    public Result removeTunnel(Tunnel tunnelInfo) {

        Tunnel tunnel = tunnelStore.get(tunnelInfo.id());
        if (tunnel != null) {
            DeviceId deviceId = config.getDeviceId(tunnel.labelIds().get(0));
            if (tunnel.isAllowedToRemoveGroup()) {
                if (groupHandlerMap.get(deviceId).removeGroup(tunnel.groupId())) {
                    tunnelStore.remove(tunnel.id());
                } else {
                    log.error("Failed to remove the tunnel {}", tunnelInfo.id());
                    return Result.INTERNAL_ERROR;
                }
            } else {
                log.debug("The group is not removed because it is being used.");
                tunnelStore.remove(tunnel.id());
            }
        } else {
            log.error("No tunnel found for tunnel ID {}", tunnelInfo.id());
            return Result.TUNNEL_NOT_FOUND;
        }

        return Result.SUCCESS;
    }

    /**
     * Returns the tunnel with the tunnel ID given.
     *
     * @param tid Tunnel ID
     * @return Tunnel reference
     */
    public Tunnel getTunnel(String tid) {
        return tunnelStore.get(tid);
    }

    /**
     * Returns all tunnels.
     *
     * @return list of Tunnels
     */
    public List<Tunnel> getTunnels() {
        List<Tunnel> tunnels = new ArrayList<>();
        tunnelStore.values().forEach(tunnel -> tunnels.add(
                new DefaultTunnel((DefaultTunnel) tunnel)));

        return tunnels;
    }

    private List<Integer> createStitchedGroupsForTunnel(Tunnel tunnel) {

        List<Integer> groupIDs = new ArrayList<Integer>();
        List<String> ids = new ArrayList<String>();
        for (Integer label : tunnel.labelIds()) {
            ids.add(label.toString());
        }
        List<TunnelRouteInfo> stitchingRule = getStitchingRule(ids);
        if (stitchingRule == null) {
            log.error("Failed to stitch tunnel");
         }

        for (TunnelRouteInfo route: stitchingRule) {

            DeviceId deviceId = config.getDeviceId(route.getsrcSwSID());
            if (deviceId == null) {
                log.warn("No device found for SID {}", tunnel.labelIds().get(0));
            } else if (groupHandlerMap.get(deviceId) == null) {
                log.warn("group handler not found for {}", deviceId);
            }

            Set<DeviceId> deviceIds = new HashSet<>();
            //int sid = tunnel.labelIds().get(1);
            deviceIds.add(config.getDeviceId(route.getRoute().get(0)));


            NeighborSet ns = new NeighborSet(deviceIds, route.getRoute().get(1));

            int groupId = -1;
            boolean allow;

            if (groupHandlerMap.get(deviceId).hasNextObjectiveId(ns)) {
                allow = false;
            } else {
                allow = true;
            }

            groupId = groupHandlerMap.get(deviceId).getNextObjectiveId(ns, null);
            tunnel.allowToRemoveStitchedGroup(groupId, allow);
            route.setGroupId(groupId);

            groupIDs.add(groupId);
        }

        return groupIDs;
    }

    private int createGroupsForTunnel(Tunnel tunnel) {

        Set<Integer> portNumbers;
        final int groupError = -1;

        DeviceId deviceId = config.getDeviceId(tunnel.labelIds().get(0));
        if (deviceId == null) {
            log.warn("No device found for SID {}", tunnel.labelIds().get(0));
            return groupError;
        } else if (groupHandlerMap.get(deviceId) == null) {
            log.warn("group handler not found for {}", deviceId);
            return groupError;

        }
        Set<DeviceId> deviceIds = new HashSet<>();
        int sid = tunnel.labelIds().get(1);
        if (config.isAdjacencySid(deviceId, sid)) {
            portNumbers = config.getPortsForAdjacencySid(deviceId, sid);
            for (Link link: linkService.getDeviceEgressLinks(deviceId)) {
                for (Integer port: portNumbers) {
                    if (link.src().port().toLong() == port) {
                        deviceIds.add(link.dst().deviceId());
                    }
                }
            }
        } else {
            deviceIds.add(config.getDeviceId(sid));
        }

        NeighborSet ns = new NeighborSet(deviceIds, tunnel.labelIds().get(2));

        // If the tunnel reuses any existing groups, then tunnel handler
        // should not remove the group.
        if (groupHandlerMap.get(deviceId).hasNextObjectiveId(ns)) {
            tunnel.allowToRemoveGroup(false);
        } else {
            tunnel.allowToRemoveGroup(true);
        }
//this line is the one that will create the groups
        return groupHandlerMap.get(deviceId).getNextObjectiveId(ns, null);
    }

    /**
     * Split the nodes IDs into multiple tunnel if Segment Stitching is required.
     * We assume that the first node ID is the one of source router, and the last
     * node ID is that of the destination router.
     *
     * @param route list of node IDs
     * @return List of the TunnelRoutInfo
     */
    private List<TunnelRouteInfo> getStitchingRule(List<String> route) {

        if (route.isEmpty() || route.size() < 3) {
            return null;
        }

        List<TunnelRouteInfo> rules = new ArrayList<TunnelRouteInfo>();

        //this takes the SID (string), converts it to an int, getDeviceID
        // then returns the DeviceID, which is then converted to a string
        int srcSID = Integer.parseInt(route.get(0));


        int i = 0;
        TunnelRouteInfo routeInfo = new TunnelRouteInfo();
        boolean checkNeighbor = false;

        for (String nodeId: route) {
            // The first node ID is always the source router.
            if (i == 0) {
                if (srcSID == 0) {
                    srcSID = Integer.parseInt(route.get(0));
                }
                routeInfo.setsrcSwSID(srcSID);
                checkNeighbor = true;
                i++;
            } else if (i == 1) {
// if this is the first node ID to put the label stack.
                //if (checkNeighbor) {
//                    List<DeviceId> fwdSws = getDpidIfNeighborOf(nodeId, srcSw);
                    // if nodeId is NOT the neighbor of srcSw..
//                    if (fwdSws.isEmpty()) {
//                        fwdSws = srManager.getForwardingSwitchForNodeId(srcSw,nodeId);
//                        if (fwdSws == null || fwdSws.isEmpty()) {
//                            log.warn("There is no route from node {} to node {}",
//                                     srcSw.getDpid(), nodeId);
//                            return null;
//                        }
//                        routeInfo.addRoute(nodeId);
//                        i++;
////                    }
//                    DeviceId fwdSws = config.getDeviceId(Integer.parseInt(route.get(0)));
//                    routeInfo.setFwdSwDeviceId(fwdSws);
//                    // we check only the next node ID of the source router
//                    checkNeighbor = false;
// //               } else  { // if neighbor check is already done, then just add it
                routeInfo.addRoute(Integer.parseInt(nodeId));
                    i++;
                //}
            } else {
                // if i > 1

                routeInfo.addRoute(Integer.parseInt(nodeId));
                i++;
            }

                 // If the number of labels reaches the limit, start over the procedure
            if (i == 3 + 1) {

                rules.add(routeInfo);
                routeInfo = new TunnelRouteInfo();
                srcSID = Integer.parseInt(route.get(0));

                routeInfo.setsrcSwSID(srcSID);
                i = 1;
                checkNeighbor = true;
            }
        }


        if (i < 3 + 1 && (routeInfo.getFwdSwDeviceId() != null &&
                !(routeInfo.getFwdSwDeviceId() == null))) {
            rules.add(routeInfo);
            // NOTE: empty label stack can happen, but forwarding destination should be set
        }

        return rules;
    }


    public class TunnelRouteInfo {

        private int srcSwSID;
        //changed this from a list of deviceIds to just one, since I'm not supporting ECMP for now
        private DeviceId fwdSwDeviceIds;
        private List<Integer> route;
        private int gropuId;
        private String srcAdjSid;

        public TunnelRouteInfo() {
//            fwdSwDeviceIds = new ArrayList<DeviceId>();
            fwdSwDeviceIds = null;
            route = new ArrayList<Integer>();
        }

        public void setSrcAdjacencySid(String nodeId) {
            this.srcAdjSid = nodeId;
        }

        private void setsrcSwSID(int sid) {
            this.srcSwSID = sid;
        }

        //changed this from a list of deviceIds to just one, since I'm not supporting ECMP for now
        private void setFwdSwDeviceId(DeviceId deviceId) {
            this.fwdSwDeviceIds = deviceId;
        }

        private void addRoute(int id) {
            route.add(id);
        }

        private void setGroupId(int groupId) {
            this.gropuId = groupId;
        }

        private String getSrcAdjanceySid() {
            return this.srcAdjSid;
        }

        public int getsrcSwSID() {
            return this.srcSwSID;
        }

        //changed this from a list of deviceIds to just one, since I'm not supporting ECMP for now
        public DeviceId getFwdSwDeviceId() {
            return this.fwdSwDeviceIds;
        }

        public List<Integer> getRoute() {
            return this.route;
        }

        public int getGroupId() {
            return this.gropuId;
        }

    }

}
