/*
package org.openmrs.module.ugandaemr.tasks;

import java.util.Date;
import java.util.List;


import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.PatientProgram;
import org.openmrs.Program;
import org.openmrs.api.ProgramWorkflowService;
import org.openmrs.api.context.Context;
import org.openmrs.module.ugandaemr.metadata.core.Programs;
import org.openmrs.module.ugandaemr.tasks.ExitMCHProgramASLostToFollowUpTask;
import org.openmrs.util.LocaleUtility;
import org.openmrs.web.test.jupiter.BaseModuleWebContextSensitiveTest;;import static org.junit.jupiter.api.Assertions.assertEquals;
@Ignore
public class ExitMCHProgramASLostToFollowUpTaskTest extends BaseModuleWebContextSensitiveTest {
	
	protected static final String UGANDAEMR_STANDARD_DATASET_XML = "org/openmrs/module/ugandaemr/include/standardTestDataset.xml";

	@BeforeEach
	public void setup() throws Exception {
		executeDataSet(UGANDAEMR_STANDARD_DATASET_XML);
	}
	
	@Test
	public void shoudExitPatientsWhoAreLostToFollowUp() {
		
		Patient patient = new Patient(8);
		ProgramWorkflowService service = Context.getService(ProgramWorkflowService.class);
		Program mchProgram = service.getProgramByUuid(Programs.MCH_PROGRAM.uuid());
		
		//should be enrolled in the mch program
		List<PatientProgram> patientPrograms = service.getPatientPrograms(patient, mchProgram, null, null, null, null, false);
		assertEquals(1, patientPrograms.size());
		
		new ExitMCHProgramASLostToFollowUpTask().execute();
		
		//should not be enrolled in mch program
        patientPrograms = service.getPatientPrograms(patient, mchProgram, null, null, null, null, false);
		assertEquals(0, patientPrograms.size());
	}
	
	@Test
	public void shoudNotExitPatientsWhoAreNotLostToFollowUp() {
		
		Patient patient = new Patient(8);
		ProgramWorkflowService service = Context.getService(ProgramWorkflowService.class);
		Program mchProgram = service.getProgramByUuid(Programs.MCH_PROGRAM.uuid());
		
		//should be enrolled in the mch program
		List<PatientProgram> patientPrograms = service.getPatientPrograms(patient, mchProgram, null, null, null, null, false);
		assertEquals(1, patientPrograms.size());
		
		//adjust the latest encounter date to less than the lost to follow up days
		List<Encounter> encounters = Context.getEncounterService().getEncountersByPatient(patient);
		Encounter latestEncounter = encounters.get(encounters.size() - 1);
		latestEncounter.setEncounterDatetime(new Date());
		
		//Fixes StackOverflowError caused by hibernate flush that originates 
		//from https://github.com/openmrs/openmrs-core/blob/2.2.0/api/src/main/java/org/openmrs/api/impl/EncounterServiceImpl.java#L179
		//and https://github.com/openmrs/openmrs-core/blob/2.2.0/api/src/main/java/org/openmrs/util/LocaleUtility.java#L59
		//because of observations that are not yet saved but dirtied by the EncounterService to match the encounterDatetime
		//NOTE: I have been able to reproduce the exact same error in the core platform when i run this test without others
		//https://github.com/openmrs/openmrs-core/blob/2.2.0/api/src/test/java/org/openmrs/api/EncounterServiceTest.java#L603
		//This core test passes if you run it with others simply because they set defaultLocaleCache to a non null value
		//such that by the time this test runs, the problematic code block in LocaleUtility.java#L59 is not run.
		LocaleUtility.getDefaultLocale();
		
		Context.getEncounterService().saveEncounter(latestEncounter);
		
		new ExitMCHProgramASLostToFollowUpTask().execute();
		
		//should still be enrolled in mch program
        patientPrograms = service.getPatientPrograms(patient, mchProgram, null, null, null, null, false);
		assertEquals(1, patientPrograms.size());
	}
}
*/
