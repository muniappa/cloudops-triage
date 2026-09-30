package com.cloudops.service;

import com.cloudops.domain.enums.ServiceHealthStatus;
import com.cloudops.domain.model.MonitoredService;

/**
 * Spring application event published when a service's health status changes.
 * Consumed by the agentic incident loop.
 */
public class ServiceStatusChangedEvent {

    private final MonitoredService service;
    private final ServiceHealthStatus previousStatus;
    private final ServiceHealthStatus newStatus;

    public ServiceStatusChangedEvent(MonitoredService service,
                                     ServiceHealthStatus previousStatus,
                                     ServiceHealthStatus newStatus) {
        this.service        = service;
        this.previousStatus = previousStatus;
        this.newStatus      = newStatus;
    }

    public MonitoredService getService()        { return service; }
    public ServiceHealthStatus getPreviousStatus() { return previousStatus; }
    public ServiceHealthStatus getNewStatus()    { return newStatus; }
}
