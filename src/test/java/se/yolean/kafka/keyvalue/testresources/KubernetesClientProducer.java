package se.yolean.kafka.keyvalue.testresources;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@Singleton
public class KubernetesClientProducer {

  private KubernetesClient client;

  @Produces
  @Singleton
  @IfBuildProfile("test")
  public KubernetesClient kubernetesClient(KubernetesSerialization serialization) {
    if (client == null) {
      String yamlConfig = System.getProperty("test.kubeconfig");

      Config config = Config.fromKubeconfig(yamlConfig);
      client = new KubernetesClientBuilder()
          .withKubernetesSerialization(serialization)
          .withConfig(config)
          .build();
    }

    return client;
  }

  @PreDestroy
  public void destroy() {
    if (client != null) {
      client.close();
    }
  }

}
