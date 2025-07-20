
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
import static qing.albatross.server.UnixRpcMethodFactory.ARG_INT;
import static qing.albatross.server.UnixRpcMethodFactory.ARG_STR;

import java.lang.reflect.Method;
import java.util.Map;

import qing.albatross.core.Albatross;
import qing.albatross.reflection.ReflectUtils;


public class UnixRpcServer extends Thread {


  static {
    isInit = false;
    Albatross.initRpcClass(UnixRpcServer.class);
    initLibrary();
  }


  long serverObj;

  public String socketPath;


  private static volatile boolean isInit;

  public static boolean initLibrary() {
    if (isInit)
      return true;
    try {
      UnixRpcServer.registerCallback(UnixRpcInstance.class.getDeclaredMethod("onNewConnection", long.class, int.class),
          UnixRpcInstance.class.getDeclaredMethod("onConnectionClose", long.class, int.class),
          UnixRpcInstance.class.getDeclaredMethod("onReceiveMsg", int.class, Method.class, int.class)
      );
      isInit = true;
      return true;
    } catch (Throwable e) {
      return false;
    }
  }

  static UnixRpcServer create(String socketPath, UnixRpcInstance owner, boolean isAbstract, Class<?> api) {
    if (!isInit)
      return null;
    try {
      if (!ReflectUtils.isInterfaceOf(owner.getClass(), api)) {
        Albatross.log(owner.getClass() + " is not instance of " + api.getName());
        return null;
      }
      UnixRpcServer server = new UnixRpcServer(socketPath, isAbstract, owner);
      if (server.serverObj > 40960) {
        if (server.init(owner, api)) {
          owner.setServer(server);
          return server;
        }
      }
    } catch (Exception e) {
      Albatross.log("Unix Rpc Server create", e);
    }
    return null;
  }

  public UnixRpcServer(String socketPath, boolean isAbstract, Object owner) {
    this.socketPath = socketPath;
    serverObj = createUnixServer(socketPath, owner, isAbstract);
  }


  public void log(String msg) {

  }


  @Override
  public void run() {
    runUnixServer(serverObj);
    destroyUnixServer(serverObj);
  }

  boolean init(UnixRpcInstance owner, Class<?> apiInterface) {
    try {
      Map<String, UnixRpcMethodFactory.RpcMethod> rpcMethods = UnixRpcMethodFactory.generateRpcMethods(
          apiInterface);
      for (Map.Entry<String, UnixRpcMethodFactory.RpcMethod> entry : rpcMethods.entrySet()) {
        UnixRpcMethodFactory.RpcMethod rpcMethod = entry.getValue();
        Method method = rpcMethod.method;
        if (method.getAnnotation(Broadcast.class) == null) {
          registerMethod(serverObj, rpcMethod.getName(), method, rpcMethod.args, rpcMethod.ret);
        } else {
          Method targetMethod = ReflectUtils.findMethod(owner.getClass(), method.getName(), method.getParameterTypes());
          registerBroadcast(serverObj, rpcMethod.getName(), targetMethod, rpcMethod.args, rpcMethod.ret);
        }
      }
      return true;
    } catch (Exception e) {
      Albatross.log("Unix Rpc Server init", e);
      throw new RuntimeException(e);
    }
  }

  static native byte registerMethod(long serverObj, String name, Object method, byte[] args, byte retType);

  static native byte registerBroadcast(long serverObj, String name, Object method, byte[] args, byte retType);

  static native long createUnixServer(String path, Object owner, boolean isAbstract);

  static native void destroyUnixServer(long serverObj);

  static native boolean runUnixServer(long serverObj);

  private native static int getSubscriberSize(long serverObj);

  static native void registerCallback(Method onNewConnection, Method onConnectionClose, Method onReceiveMsg);

  private static native int broadcastMessage(long serverObj, byte cmd, byte[] data, int dataLen);


  public static native Object broadcastWithResult(long serverObj, byte cmd, byte[] data, int dataLen, byte retType);


  public int broadcastMessage(byte cmd, byte[] data, int dataLen) {
    return broadcastMessage(serverObj, cmd, data, dataLen);
  }


  public int getSubscriberSize() {
    return getSubscriberSize(serverObj);
  }

  public byte receiveByte(byte cmd, byte[] data, int dataLen, byte default_value) {
    Object result = broadcastWithResult(serverObj, cmd, data, dataLen, ARG_BYTE);
    if (result == null)
      return default_value;
    return (Byte) result;
  }

  public int receiveInt(byte cmd, byte[] data, int dataLen, int default_value) {
    Object result = broadcastWithResult(serverObj, cmd, data, dataLen, ARG_INT);
    if (result == null)
      return default_value;
    return (Integer) result;
  }

  public String receiveString(byte cmd, byte[] data, int dataLen, String default_value) {
    Object result = broadcastWithResult(serverObj, cmd, data, dataLen, ARG_STR);
    if (result == null)
      return default_value;
    return (String) result;
  }
}
