package se.yolean.kafka.keyvalue.testresources;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class K3sTestResource implements QuarkusTestResourceLifecycleManager {

    K3sContainer k3s;
    String configYaml;

    @Override
    public Map<String, String> start() {
      k3s = new K3sContainer(DockerImageName.parse("rancher/k3s:v1.27.9-k3s1"));
      k3s.setPortBindings(List.of("6443:6443"));
      k3s.start();

      configYaml = k3s.getKubeConfigYaml();

      System.setProperty("test.kubeconfig", configYaml);
      Map<String, String> props = new HashMap<>();
      props.put("test.kubeconfig", configYaml);
      return props;
    }

    @Override
    public void stop() {
      k3s.stop();
    }

    @Override
    public void inject(TestInjector testInjector) {
      testInjector.injectIntoFields(this.k3s,
          new TestInjector.AnnotatedAndMatchesType(InjectK3sContainer.class, K3sContainer.class));
    }

}
