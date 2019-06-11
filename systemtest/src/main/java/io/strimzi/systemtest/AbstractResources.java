/*
 * Copyright 2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.systemtest;

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.DoneableService;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.api.model.apps.DoneableDeployment;
import io.fabric8.kubernetes.api.model.extensions.DoneableIngress;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressList;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingList;
import io.fabric8.kubernetes.api.model.rbac.DoneableClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.DoneableRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import io.strimzi.api.kafka.Crds;
import io.strimzi.api.kafka.KafkaConnectList;
import io.strimzi.api.kafka.KafkaConnectS2IList;
import io.strimzi.api.kafka.KafkaList;
import io.strimzi.api.kafka.KafkaMirrorMakerList;
import io.strimzi.api.kafka.KafkaTopicList;
import io.strimzi.api.kafka.KafkaUserList;
import io.strimzi.api.kafka.model.DoneableKafka;
import io.strimzi.api.kafka.model.DoneableKafkaConnect;
import io.strimzi.api.kafka.model.DoneableKafkaConnectS2I;
import io.strimzi.api.kafka.model.DoneableKafkaMirrorMaker;
import io.strimzi.api.kafka.model.DoneableKafkaTopic;
import io.strimzi.api.kafka.model.DoneableKafkaUser;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.KafkaConnect;
import io.strimzi.api.kafka.model.KafkaConnectS2I;
import io.strimzi.api.kafka.model.KafkaMirrorMaker;
import io.strimzi.api.kafka.model.KafkaTopic;
import io.strimzi.api.kafka.model.KafkaUser;
import io.strimzi.test.k8s.KubeClient;

abstract class AbstractResources {

    private final KubeClient client;

    AbstractResources(KubeClient client) {
        this.client = client;
    }

    KubeClient client() {
        return client;
    }

    MixedOperation<Kafka, KafkaList, DoneableKafka, Resource<Kafka, DoneableKafka>> kafka() {
        return customResourcesWithCascading(Kafka.class, KafkaList.class, DoneableKafka.class);
    }

    // This logic is necessary only for the deletion of resources with `cascading: true`
    private <T extends HasMetadata, L extends KubernetesResourceList, D extends Doneable<T>> MixedOperation<T, L, D, Resource<T, D>> customResourcesWithCascading(Class<T> resourceType, Class<L> listClass, Class<D> doneClass) {
        return new CustomResourceOperationsImpl<T, L, D>(((BaseClient) client().getClient()).getHttpClient(), client().getClient().getConfiguration(), Crds.kafka().getSpec().getGroup(), Crds.kafka().getSpec().getVersion(), "kafkas", true, client().getNamespace(), null, true, null, null, false, resourceType, listClass, doneClass);
    }

    MixedOperation<KafkaConnect, KafkaConnectList, DoneableKafkaConnect, Resource<KafkaConnect, DoneableKafkaConnect>> kafkaConnect() {
        return client()
                .customResources(Crds.kafkaConnect(),
                        KafkaConnect.class, KafkaConnectList.class, DoneableKafkaConnect.class);
    }

    MixedOperation<KafkaConnectS2I, KafkaConnectS2IList, DoneableKafkaConnectS2I, Resource<KafkaConnectS2I, DoneableKafkaConnectS2I>> kafkaConnectS2I() {
        return client()
                .customResources(Crds.kafkaConnectS2I(),
                        KafkaConnectS2I.class, KafkaConnectS2IList.class, DoneableKafkaConnectS2I.class);
    }

    MixedOperation<KafkaMirrorMaker, KafkaMirrorMakerList, DoneableKafkaMirrorMaker, Resource<KafkaMirrorMaker, DoneableKafkaMirrorMaker>> kafkaMirrorMaker() {
        return client()
                .customResources(Crds.mirrorMaker(),
                        KafkaMirrorMaker.class, KafkaMirrorMakerList.class, DoneableKafkaMirrorMaker.class);
    }

    MixedOperation<KafkaTopic, KafkaTopicList, DoneableKafkaTopic, Resource<KafkaTopic, DoneableKafkaTopic>> kafkaTopic() {
        return client()
                .customResources(Crds.topic(),
                        KafkaTopic.class, KafkaTopicList.class, DoneableKafkaTopic.class);
    }

    MixedOperation<KafkaUser, KafkaUserList, DoneableKafkaUser, Resource<KafkaUser, DoneableKafkaUser>> kafkaUser() {
        return client()
                .customResources(Crds.kafkaUser(),
                        KafkaUser.class, KafkaUserList.class, DoneableKafkaUser.class);
    }

    MixedOperation<Deployment, DeploymentList, DoneableDeployment, Resource<Deployment, DoneableDeployment>> deployment() {
        return customResourcesWithCascading(Deployment.class, DeploymentList.class, DoneableDeployment.class);
    }

    MixedOperation<ClusterRoleBinding, ClusterRoleBindingList, DoneableClusterRoleBinding, Resource<ClusterRoleBinding, DoneableClusterRoleBinding>> clusterRoleBinding() {
        return customResourcesWithCascading(ClusterRoleBinding.class, ClusterRoleBindingList.class, DoneableClusterRoleBinding.class);
    }

    MixedOperation<RoleBinding, RoleBindingList, DoneableRoleBinding, Resource<RoleBinding, DoneableRoleBinding>> roleBinding() {
        return customResourcesWithCascading(RoleBinding.class, RoleBindingList.class, DoneableRoleBinding.class);
    }

    MixedOperation<Service, ServiceList, DoneableService, Resource<Service, DoneableService>> service() {
        return customResourcesWithCascading(Service.class, ServiceList.class, DoneableService.class);
    }

    MixedOperation<Ingress, IngressList, DoneableIngress, Resource<Ingress, DoneableIngress>> ingress() {
        return customResourcesWithCascading(Ingress.class, IngressList.class, DoneableIngress.class);
    }

}
