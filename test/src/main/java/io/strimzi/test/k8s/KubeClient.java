/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.test.k8s;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DoneableStatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.openshift.client.OpenShiftClient;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public class KubeClient {

    private static final Logger LOGGER = LogManager.getLogger(KubeClient.class);
    protected final KubernetesClient client;
    protected String namespace;

    public KubeClient(KubernetesClient client, String namespace) {
        this.client = client;
        this.namespace = namespace;
    }

    public KubernetesClient getClient() {
        return client;
    }

    public KubeClient namespace(String futureNamespace) {
        return new KubeClient(this.client, futureNamespace);
    }

    public String getNamespace() {
        return namespace;
    }

    public Namespace getNamespace(String namespace) {
        return client.namespaces().withName(namespace).get();
    }

    public void createNamespace(String name) {
        Namespace ns = new NamespaceBuilder().withNewMetadata().withName(name).endMetadata().build();
        client.namespaces().createOrReplace(ns);
    }

    public void deleteNamespace(String name) {
        client.namespaces().withName(name).delete();
    }

    /**
     * Gets namespace status
     */
    public boolean getNamespaceStatus(String namespaceName) {
        return client.namespaces().withName(namespaceName).isReady();
    }


    public void deleteConfigMap(String configMapName) {
        client.configMaps().inNamespace(getNamespace()).withName(configMapName).delete();
    }

    public ConfigMap getConfigMap(String configMapName) {
        return client.configMaps().inNamespace(getNamespace()).withName(configMapName).get();
    }

    public boolean getConfigMapStatus(String configMapName) {
        return client.configMaps().inNamespace(getNamespace()).withName(configMapName).isReady();
    }

    public List<ConfigMap> listConfigMaps() {
        return client.configMaps().inNamespace(getNamespace()).list().getItems();
    }


    public String execInPod(String podName, String container, String... command) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        LOGGER.info("Running command on pod {}: {}", podName, command);
        CountDownLatch execLatch = new CountDownLatch(1);

        try {
            client.pods().inNamespace(getNamespace())
                .withName(podName).inContainer(container)
                .readingInput(null)
                .writingOutput(baos)
                .usingListener(new SimpleListener(execLatch))
                .exec(command);
            boolean wait = execLatch.await(1, TimeUnit.MINUTES);
            if (wait) {
                LOGGER.info("Await for command execution was finished");
            }
            return baos.toString("UTF-8");
        } catch (UnsupportedEncodingException | InterruptedException e) {
            LOGGER.warn("Exception running command {} on pod: {}", command, e.getMessage());
        }
        return "";
    }

    public List<Pod> listPods(LabelSelector selector) {
        return client.pods().inNamespace(getNamespace()).withLabelSelector(selector).list().getItems();
    }

    public List<Pod> listPods(Map<String, String> labelSelector) {
        return client.pods().inNamespace(getNamespace()).withLabels(labelSelector).list().getItems();
    }

    public List<Pod> listPods(String key, String value) {
        return listPods(Collections.singletonMap(key, value));
    }

    public List<String> listPodNames(String key, String value) {
        return listPods(Collections.singletonMap(key, value)).stream()
                .map(pod -> pod.getMetadata().getName())
                .collect(Collectors.toList());
    }


    public List<PersistentVolumeClaim> listPersistentVolumeClaims() {
        return client.persistentVolumeClaims().inNamespace(getNamespace()).list().getItems();
    }

    public List<Pod> listPods() {
        return client.pods().inNamespace(getNamespace()).list().getItems();
    }

    /**
     * Returns list of pods by prefix in pod name
     * @param podNamePrefix prefix with which the name should begin
     * @return List of pods
     */
    public List<Pod> listPodsByPrefixInName(String podNamePrefix) {
        return listPods()
                .stream().filter(p -> p.getMetadata().getName().startsWith(podNamePrefix))
                .collect(Collectors.toList());
    }

    /**
     * Gets pod
     */
    public Pod getPod(String name) {
        return client.pods().inNamespace(getNamespace()).withName(name).get();
    }

    /**
     * Gets pod
     */
    public PodResource<Pod, DoneablePod> getPodResource(String name) {
        return client.pods().inNamespace(getNamespace()).withName(name);
    }

    /**
     * Gets pod Uid
     */
    public String getPodUid(String name) {
        return client.pods().inNamespace(getNamespace()).withName(name).get().getMetadata().getUid();
    }

    /**
     * Gets statefulset Uid
     */
    public String getSsUid(String name) {
        return client.apps().statefulSets().inNamespace(getNamespace()).withName(name).get().getMetadata().getUid();
    }

    /**
     * Deletes pod
     */
    public Boolean deletePod(Pod pod) {
        return client.pods().inNamespace(getNamespace()).delete(pod);
    }

    public Date getCreationTimestampForPod(String podName) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'kk:mm:ss'Z'");
        Pod pod = getPod(podName);
        Date parsedDate = null;
        try {
            parsedDate = df.parse(pod.getMetadata().getCreationTimestamp());
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return parsedDate;
    }

    /**
     * Gets stateful set
     */
    public StatefulSet getStatefulSet(String statefulSetName) {
        return  client.apps().statefulSets().inNamespace(getNamespace()).withName(statefulSetName).get();
    }

    /**
     * Gets stateful set
     */
    public RollableScalableResource<StatefulSet, DoneableStatefulSet> statefulSet(String statefulSetName) {
        return client.apps().statefulSets().inNamespace(getNamespace()).withName(statefulSetName);
    }

    /**
     * Gets stateful set selectors
     */
    public LabelSelector getStatefulSetSelectors(String statefulSetName) {
        return client.apps().statefulSets().inNamespace(getNamespace()).withName(statefulSetName).get().getSpec().getSelector();
    }

    /**
     * Gets stateful set status
     */
    public boolean getStatefulSetStatus(String statefulSetName) {
        return client.apps().statefulSets().inNamespace(getNamespace()).withName(statefulSetName).isReady();
    }

    /**
     * Gets stateful set Uid
     */
    public String getStatefulUid(String statefulSetName) {
        return client.apps().statefulSets().inNamespace(getNamespace()).withName(statefulSetName).get().getMetadata().getUid();
    }

    public void deleteStatefulSet(String statefulSetName) {
        client.apps().statefulSets().inNamespace(getNamespace()).withName(statefulSetName).delete();
    }

    public Deployment createOrReplaceDeployment(Deployment deployment) {
        return client.apps().deployments().inNamespace(getNamespace()).createOrReplace(deployment);
    }

    /**
     * Gets deployment
     */
    public Deployment getDeployment(String deploymentName) {
        return client.apps().deployments().inNamespace(getNamespace()).withName(deploymentName).get();
    }

    /**
     * Gets deployment status
     */
    public LabelSelector getDeploymentSelectors(String deploymentName) {
        return client.apps().deployments().inNamespace(getNamespace()).withName(deploymentName).get().getSpec().getSelector();
    }

    /**
     * Gets deployment status
     */
    public boolean getDeploymentStatus(String deploymentName) {
        return client.apps().deployments().inNamespace(getNamespace()).withName(deploymentName).isReady();
    }

    public void deleteDeployment(String deploymentName) {
        client.apps().deployments().inNamespace(getNamespace()).withName(deploymentName).delete();
    }

    public void deleteDeployment(Deployment deployment) {
        client.apps().deployments().inNamespace(getNamespace()).delete(deployment);
    }

    /**
     * Gets deployment config status
     */
    public boolean getDeploymentConfigStatus(String deploymentCofigName) {
        return client.adapt(OpenShiftClient.class).deploymentConfigs().inNamespace(getNamespace()).withName(deploymentCofigName).isReady();
    }

    public Secret createSecret(Secret secret) {
        return client.secrets().inNamespace(getNamespace()).create(secret);
    }

    public Secret patchSecret(String secretName, Secret secret) {
        return client.secrets().inNamespace(getNamespace()).withName(secretName).patch(secret);
    }


    public Secret getSecret(String secretName) {
        return client.secrets().inNamespace(getNamespace()).withName(secretName).get();
    }

    public Service createService(Service service) {
        return client.services().inNamespace(getNamespace()).create(service);
    }

    public Ingress createIngress(Ingress ingress) {
        return client.extensions().ingresses().inNamespace(getNamespace()).create(ingress);
    }

    public Boolean deleteIngress(Ingress ingress) {
        return client.extensions().ingresses().inNamespace(getNamespace()).delete(ingress);
    }

    public List<Node> listNodes() {
        return client.nodes().list().getItems();
    }

    public List<Secret> listSecrets() {
        return client.secrets().inNamespace(getNamespace()).list().getItems();
    }

    public Service getService(String serviceName) {
        return client.services().inNamespace(getNamespace()).withName(serviceName).get();
    }

    public boolean getServiceStatus(String serviceName) {
        return client.services().inNamespace(getNamespace()).withName(serviceName).isReady();
    }

    public void deleteService(String serviceName) {
        client.services().inNamespace(getNamespace()).withName(serviceName).delete();
    }

    public void deleteService(Service service) {
        client.services().inNamespace(getNamespace()).delete(service);
    }

    public String logs(String podName) {
        return client.pods().inNamespace(getNamespace()).withName(podName).getLog();
    }

    public String logs(String podName, String containerName) {
        return client.pods().inNamespace(getNamespace()).withName(podName).inContainer(containerName).getLog();
    }

    public List<Event> listEvents() {
        return client.events().inNamespace(getNamespace()).list().getItems();
    }

    public List<Event> listEvents(String resourceType, String resourceName) {
        return client.events().inNamespace(getNamespace()).list().getItems().stream()
                .filter(event -> event.getInvolvedObject().getKind().equals(resourceType))
                .filter(event -> event.getInvolvedObject().getName().equals(resourceName))
                .collect(Collectors.toList());
    }

    public RoleBinding createOrReplaceRoleBinding(RoleBinding roleBinding) {
        return client.rbac().roleBindings().inNamespace(getNamespace()).createOrReplace(roleBinding);
    }

    public ClusterRoleBinding createOrReplaceClusterRoleBinding(ClusterRoleBinding clusterRoleBinding) {
        return client.rbac().clusterRoleBindings().inNamespace(getNamespace()).createOrReplace(clusterRoleBinding);
    }

    public Boolean deleteClusterRoleBinding(ClusterRoleBinding clusterRoleBinding) {
        return client.rbac().clusterRoleBindings().inNamespace(getNamespace()).delete(clusterRoleBinding);
    }

    public <T extends HasMetadata, L extends KubernetesResourceList, D extends Doneable<T>> MixedOperation<T, L, D, Resource<T, D>> customResources(CustomResourceDefinition crd, Class<T> resourceType, Class<L> listClass, Class<D> doneClass) {
        return client.customResources(crd, resourceType, listClass, doneClass); //TODO namespace here
    }

    private static class SimpleListener implements ExecListener {
        CountDownLatch execLatch;
        SimpleListener(CountDownLatch execLatch) {
            this.execLatch = execLatch;
            execLatch.countDown();
        }

        @Override
        public void onOpen(Response response) {
            LOGGER.info("The shell will remain open for 10 seconds.");
            execLatch.countDown();
        }

        @Override
        public void onFailure(Throwable t, Response response) {
            LOGGER.info("shell barfed with code {} and message {}", response.code(), response.message());
            execLatch.countDown();
        }

        @Override
        public void onClose(int code, String reason) {
            LOGGER.info("The shell will now close with error code {} by reason {}", code, reason);
            execLatch.countDown();
        }
    }
}
