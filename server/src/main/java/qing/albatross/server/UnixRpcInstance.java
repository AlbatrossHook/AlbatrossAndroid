
/*
 * Copyright 2025 QingWan (qingwanmail@foxmail.com)
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


package qing.albatross.server;

import static qing.albatross.server.UnixRpcMethodFactory.ARG_BYTE;

import android.util.ArrayMap;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public abstract class UnixRpcInstance {
  Map<Long, UnixRpcServer> servers = new ArrayMap<>();

  void setServer(UnixRpcServer server) {
    servers.put(server.serverObj, server);
  }

  public int broadcastMessage(byte cmd, String msg) {
    int count = 0;
    byte[] data = msg.getBytes(StandardCharsets.UTF_8);
    int dataLen = data.length;
    for (UnixRpcServer server : servers.values()) {
      count += server.broadcastMessage(cmd, data, dataLen);
    }
    return count;
  }

  protected abstract Class<?> getApi();

  public UnixRpcServer createServer(String socketPath, boolean isAbstract) {
    Class<?> api = getApi();
    UnixRpcServer server = UnixRpcServer.create(socketPath, this, isAbstract, api);
    if (server != null) {
      setServer(server);
      server.setName(api.getName());
      server.start();
      return server;
    }
    return null;
  }


  public int getSubscriberSize() {
    int count = 0;
    for (UnixRpcServer server : servers.values()) {
      count += server.getSubscriberSize();
    }
    return count;
  }

  public byte receiveByte(byte cmd, String msg, byte defaultValue) {
    byte[] data = msg.getBytes(StandardCharsets.UTF_8);
    int dataLen = data.length;
    for (UnixRpcServer server : servers.values()) {
      Object result = server.broadcastWithResult(server.serverObj, cmd, data, dataLen, ARG_BYTE);
      if (result != null) {
        defaultValue = (Byte) result;
        if (defaultValue < 0)
          return (byte) -defaultValue;
      }
    }
    return defaultValue;
  }

  Map<Integer, UnixSession> clientMaps = new HashMap<>();

  void onNewConnection(long serverObj, int clientFd) {
    UnixSession session = new UnixSession(servers.get(serverObj), clientFd);
    clientMaps.put(clientFd, session);
  }

  void onConnectionClose(long serverObj, int clientFd) {
    if (clientFd > 0)
      clientMaps.remove(clientFd);
    else {
      servers.remove(serverObj);
    }
  }

  String onReceiveMsg(int clientFd, Method method, int idx) {
    return null;
  }

}
