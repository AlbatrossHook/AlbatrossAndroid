
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

public abstract class UnixRpcClientInstance {

  UnixRpcClient client;

  protected abstract Class<?> getApi();

  public UnixRpcClient createClient(String socketPath, boolean isAbstract) {
    Class<?> api = getApi();
    client = UnixRpcClient.create(socketPath, this, isAbstract, api);
    if (client != null) {
      client.setName(api.getName());
      client.start();
      return client;
    }
    return null;
  }

  public void onClose() {
    close();
  }

  public void close() {
    if (client.messages != null) {
      client.continuable = false;
      client.request(() -> {
      });
    }
  }


}
