package org.openmrs.module.ugandaemr.web.customdto;

import org.openmrs.module.patientqueueing.model.PatientQueue;

public class QueueKioskEntry {

    private PatientQueue patientQueue;

    public QueueKioskEntry() {
    }

    public QueueKioskEntry(PatientQueue patientQueue) {
        this.patientQueue = patientQueue;
    }

    public PatientQueue getPatientQueue() {
        return patientQueue;
    }

    public void setPatientQueue(PatientQueue patientQueue) {
        this.patientQueue = patientQueue;
    }
}