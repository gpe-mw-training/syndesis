/*
 * Copyright (C) 2016 Red Hat, Inc.
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
package io.syndesis.server.update.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import io.syndesis.common.model.ChangeEvent;
import io.syndesis.common.model.Kind;
import io.syndesis.common.util.EventBus;
import io.syndesis.common.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ResourceUpdateController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceUpdateController.class);

    private final ResourceUpdateConfiguration configuration;
    private final EventBus eventBus;
    private final List<ResourceUpdateHandler> handlers;
    private final AtomicBoolean running;
    private final List<ChangeEvent> allEvents;

    private ScheduledExecutorService scheduler;

    public ResourceUpdateController(ResourceUpdateConfiguration configuration, EventBus eventBus, List<ResourceUpdateHandler> handlers) {
        this.configuration = configuration;
        this.eventBus = eventBus;
        this.handlers = new ArrayList<>(handlers);
        this.running = new AtomicBoolean(false);

        this.allEvents = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            allEvents.add(new ChangeEvent.Builder().kind(kind.getModelName()).build());
        }
    }

    @SuppressWarnings({"FutureReturnValueIgnored", "PMD.DoNotUseThreads"})
    public void start() {
        if (configuration.isEnabled()) {
            running.set(true);

            scheduler = Executors.newScheduledThreadPool(1, r -> new Thread(null, r, "ResourceUpdateController (scheduler)"));
            scheduler.scheduleWithFixedDelay(this::run, 0, configuration.getCheckInterval(), configuration.getCheckIntervalUnit());

            eventBus.subscribe(getClass().getName(), this::onEvent);
        }
    }

    public void stop() {
        running.set(false);

        eventBus.unsubscribe(getClass().getName());

        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void onEvent(String event, String data) {
        if (!running.get()) {
            return;
        }

        // Never do anything that could block in this callback!
        if (Objects.equals(event, EventBus.Type.CHANGE_EVENT)) {
            try {
                ChangeEvent changeEvent = Json.reader().forType(ChangeEvent.class).readValue(data);
                if (changeEvent != null) {
                    scheduler.execute(() -> run(changeEvent));
                }
            } catch (IOException e) {
                LOGGER.error("Error while processing change-event {}", data, e);
            }
        }
    }

    private void run() {
        for (int i = 0; i < allEvents.size(); i++) {
            run(allEvents.get(i));
        }
    }

    private void run(ChangeEvent event) {
        if (!running.get()) {
            return;
        }

        for (int i = 0; i < handlers.size(); i++) {
            if (handlers.get(i).canHandle(event)) {
                handlers.get(i).process(event);
            }
        }
    }
}
