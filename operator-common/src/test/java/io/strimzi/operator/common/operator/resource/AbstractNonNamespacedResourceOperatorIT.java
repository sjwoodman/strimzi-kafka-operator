/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.operator.resource;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The main purpose of the Integration Tests for the operators is to test them against a real Kubernetes cluster.
 * Real Kubernetes cluster has often some quirks such as some fields being immutable, some fields in the spec section
 * being created by the Kubernetes API etc. These things are hard to test with mocks. These IT tests make it easy to
 * test them against real clusters.
 */
@RunWith(VertxUnitRunner.class)
public abstract class AbstractNonNamespacedResourceOperatorIT<C extends KubernetesClient, T extends HasMetadata, L extends KubernetesResourceList/*<T>*/, D, R extends Resource<T, D>> {
    public static final String RESOURCE_NAME = "my-resource";
    protected static Vertx vertx;
    protected static KubernetesClient client;

    @BeforeClass
    public static void before() {
        vertx = Vertx.vertx();
        client = new DefaultKubernetesClient();
    }

    @AfterClass
    public static void after() {
        vertx.close();
    }

    abstract AbstractNonNamespacedResourceOperator operator();
    abstract T getOriginal();
    abstract T getModified();
    abstract void assertResources(TestContext context, T expected, T actual);

    @Test
    public void testFullCycle(TestContext context) {
        int milisecondsBetweenOperationAndResultCheck = 1000;
        Async async = context.async();
        AbstractNonNamespacedResourceOperator op = operator();

        T newResource = getOriginal();
        T modResource = getModified();

        Future<ReconcileResult<T>> createFuture = op.reconcile(RESOURCE_NAME, newResource);

        createFuture.setHandler(create -> {
            if (create.succeeded()) {
                try {
                    Thread.sleep(milisecondsBetweenOperationAndResultCheck);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                T created = (T) op.get(RESOURCE_NAME);

                if (created == null)    {
                    context.fail("Failed to get created Resource");
                    async.complete();
                } else  {
                    assertResources(context, newResource, created);

                    Future<ReconcileResult<T>> modifyFuture = op.reconcile(RESOURCE_NAME, modResource);
                    modifyFuture.setHandler(modify -> {
                        if (modify.succeeded()) {
                            try {
                                Thread.sleep(milisecondsBetweenOperationAndResultCheck);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            T modified = (T) op.get(RESOURCE_NAME);

                            if (modified == null)    {
                                context.fail("Failed to get modified Resource");
                                async.complete();
                            } else {
                                assertResources(context, modResource, modified);

                                Future<ReconcileResult<T>> deleteFuture = op.reconcile(RESOURCE_NAME, null);
                                deleteFuture.setHandler(delete -> {
                                    if (delete.succeeded()) {
                                        try {
                                            Thread.sleep(milisecondsBetweenOperationAndResultCheck);
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                        T deleted = (T) op.get(RESOURCE_NAME);

                                        if (deleted == null)    {
                                            async.complete();
                                        } else {
                                            context.fail("Failed to delete Resource");
                                            async.complete();
                                        }
                                    } else {
                                        context.fail(delete.cause());
                                        async.complete();
                                    }
                                });
                            }
                        } else {
                            context.fail(modify.cause());
                            async.complete();
                        }
                    });
                }

            } else {
                context.fail(create.cause());
                async.complete();
            }
        });
    }
}

