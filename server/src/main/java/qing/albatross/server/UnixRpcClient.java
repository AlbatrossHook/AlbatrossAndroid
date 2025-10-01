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

import static qing.albatross.server.UnixRpcServer.createUnixClient;
import static qing.albatross.server.UnixRpcServer.destroyUnixClient;
import static qing.albatross.server.UnixRpcServer.registerClientBroadcast;
import static qing.albatross.server.UnixRpcServer.registerClientMethod;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import qing.albatross.core.Albatross;
import qing.albatross.reflection.ReflectUtils;


public class UnixRpcClient extends Thread {


  long serverObj, subscriberObj;

  public String socketPath;


  static UnixRpcClient create(String socketPath, UnixRpcClientInstance owner, boolean isAbstract, Class<?> api) {
    try {
      if (!ReflectUtils.isInterfaceOf(owner.getClass(), api)) {
        Albatross.log(owner.getClass() + " is not instance of " + api.getName());
        return null;
      }
      UnixRpcClient client = new UnixRpcClient(socketPath, isAbstract, owner);
      if (client.serverObj > 40960 || client.serverObj < 0) {
        client.subscriberObj = createUnixClient(socketPath, owner, isAbstract, true);
        if (client.registerApi(0, owner, api)) {
          return client;
        } else {
          destroyUnixClient(client.serverObj);
          destroyUnixClient(client.subscriberObj);
        }
      }
    } catch (Exception e) {
      Albatross.log("Unix Rpc client create fail", e);
    }
    return null;
  }

  LinkedBlockingQueue<Runnable> messages;
  public boolean continuable = true;

  public UnixRpcClient(String socketPath, boolean isAbstract, Object owner) {
    this.socketPath = socketPath;
    serverObj = createUnixClient(socketPath, owner, isAbstract, false);
    messages = new LinkedBlockingQueue<>(1000);
  }

  public void request(Runnable run) {
    try {
      messages.put(run);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }


  @Override
  public void run() {
    while (continuable) {
      try {
        Runnable task = messages.take();
        task.run();
      } catch (Exception ignored) {
      }
    }
    messages = null;
    if (serverObj != 0) {
      destroyUnixClient(serverObj);
      serverObj = 0;
    }
    if (subscriberObj != 0) {
      destroyUnixClient(subscriberObj);
      subscriberObj = 0;
    }
  }

  public boolean isClosed() {
    return serverObj == 0;
  }


  boolean registerApi(int id, Object owner, Class<?> apiInterface) {
    try {
      Map<String, UnixRpcMethodFactory.RpcMethod> rpcMethods = UnixRpcMethodFactory.generateRpcMethods(
          apiInterface);
      for (Map.Entry<String, UnixRpcMethodFactory.RpcMethod> entry : rpcMethods.entrySet()) {
        UnixRpcMethodFactory.RpcMethod rpcMethod = entry.getValue();
        Method method = rpcMethod.method;
        String rpcMethodName = rpcMethod.getName();
        Method targetMethod = ReflectUtils.findMethod(owner.getClass(), method.getName(), method.getParameterTypes());
        if (method.getAnnotation(Broadcast.class) == null) {
          registerClientMethod(serverObj, rpcMethodName, targetMethod, rpcMethod.args, rpcMethod.ret);
        } else {
          if (Modifier.isNative(targetMethod.getModifiers())) {
            throw new RuntimeException("rpc broadcast method " + rpcMethodName + " should not be native");
          }
          registerClientBroadcast(id, subscriberObj, rpcMethodName, targetMethod, rpcMethod.args, rpcMethod.ret);
        }
      }
      return true;
    } catch (Exception e) {
      Albatross.log("Unix Rpc Client init", e);
      return false;
    }
  }

}
