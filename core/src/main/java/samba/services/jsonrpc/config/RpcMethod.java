/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package samba.services.jsonrpc.config;

import java.util.Collection;
import java.util.HashSet;

public enum RpcMethod {
  CLIENT_VERSION("clientVersion"),

  DISCV5_NODE_INFO ("discv5_nodeInfo"),
  DISCV5_UPDATE_NODE_INFO("discv5_updateNodeInfo"),

  DISCV5_ROUTING_TABLE_INFO("discv5_routingTableInfo"),

  DISCV5_ADD_ENR("discv5_addEnr"),
  DISCV5_GET_ENR("discv5_getEnr"),
  DISCV5_DELETE_ENR("discv5_deleteEnr"),
  DISCV5_LOOK_UP_ENR("discv5_lookupEnr"),

  DISCV5_PING("discv5_ping"),
  DISCV5_FIND_NODE("discv5_findNode"),
  DISCV5_TALK_REQ("discv5_talkReq"),
  DISCV5_RECURSIVE_FIND_NODES("discv5_recursiveFindNodes"),

  PORTAL_HISTORY_ROUTING_TABLE_INFO("portal_historyRoutingTableInfo"),

  PORTAL_HISTORY_ADD_ENR("portal_historyAddEnr"),
  PORTAL_HISTORY_GET_ENR("portal_historyGetEnr"),
  PORTAL_HISTORY_DELETE_ENR("portal_historyDeleteEnr"),
  PORTAL_HISTORY_LOOKUP_ENR("portal_historyLookupEnr"),

  PORTAL_HISTORY_PING("portal_historyPing"),
  PORTAL_HISTORY_FIND_NODES("portal_historyFindNodes"),
  PORTAL_HISTORY_FIND_CONTENT("portal_historyFindContent"),
  PORTAL_HISTORY_OFFER("portal_historyOffer"),

  PORTAL_HISTORY_RECURSIVE_FIND_NODES("portal_historyRecursiveFindNodes"),
  PORTAL_HISTORY_GOSSIP("portal_historyGossip"),

  PORTAL_HISTORY_GET_CONTENT("portal_historyGetContent"),
  PORTAL_HISTORYT_RACE_GET_CONTENT("portal_historyTraceGetContent"),

  PORTAL_HISTORY_STORE("portal_historyStore"),
  PORTAL_HISTORY_LOCAL_CONTENT("portal_historyLocalContent");


  private final String methodName;

  private static final Collection<String> allMethodNames;

  public String getMethodName() {
    return methodName;
  }

  static {
    allMethodNames = new HashSet<>();
    for (RpcMethod m : RpcMethod.values()) {
      allMethodNames.add(m.getMethodName());
    }
  }

  RpcMethod(final String methodName) {
    this.methodName = methodName;
  }

  public static boolean rpcMethodExists(final String rpcMethodName) {
    return allMethodNames.contains(rpcMethodName);
  }
}