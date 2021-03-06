package org.openmrs.module.ugandaemr.htmlformentry;

import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.CustomFormSubmissionAction;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.patientqueueing.api.PatientQueueingService;
import org.openmrs.module.patientqueueing.model.PatientQueue;
import org.openmrs.module.ugandaemr.api.UgandaEMRService;
import org.openmrs.util.OpenmrsUtil;

import java.util.Date;

public class DispensingFormSubmissionAction implements CustomFormSubmissionAction {
    @Override
    public void applyAction(FormEntrySession formEntrySession) {

        if (formEntrySession.getContext().getMode().equals(FormEntryContext.Mode.ENTER)) {
            if (formEntrySession.getEncounter().getEncounterDatetime().before(OpenmrsUtil.firstSecondOfDay(new Date()))) {
                return;
            } else {
                PatientQueueingService patientQueueingService = Context.getService(PatientQueueingService.class);
                UgandaEMRService ugandaEMRService = Context.getService(UgandaEMRService.class);
                PatientQueue patientQueue = patientQueueingService.getIncompletePatientQueue(formEntrySession.getPatient(), formEntrySession.getEncounter().getLocation());
                if (patientQueue != null) {
                    patientQueue.setEncounter(formEntrySession.getEncounter());
                    patientQueueingService.savePatientQue(patientQueue);
                    patientQueueingService.completePatientQueue(patientQueue);
                    ugandaEMRService.completePatientActiveVisit(patientQueue.getPatient());
                }
            }
        } else {
            return;
        }
    }
}
