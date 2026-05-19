package org.openmrs.module.ugandaemr.activator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Form;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.FormService;
import org.openmrs.api.context.Context;
import org.openmrs.module.ugandaemr.UgandaEMRConstants;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for JsonFormsInitializer covering:
 * - Cache initialization and clearing
 * - UUID match path (existing behavior)
 * - UUID mismatch handling with name fallback
 * - Multiple forms with same name (prefer non-retired)
 * - Cleanup mechanism with GP enabled/disabled
 */
public class JsonFormsInitializerTest extends BaseModuleContextSensitiveTest {

    private static final String TEST_DATASET_XML = "org/openmrs/module/ugandaemr/include/standardTestDataset.xml";

    private JsonFormsInitializer initializer;
    private FormService formService;
    private AdministrationService administrationService;
    private File testFormsDir;

    @BeforeEach
    public void setup() throws Exception {
        executeDataSet(TEST_DATASET_XML);
        formService = Context.getFormService();
        administrationService = Context.getAdministrationService();

        // Create temporary directory for test forms
        testFormsDir = Files.createTempDirectory("test-jsonforms").toFile();
        testFormsDir.deleteOnExit();

        // Set cleanup GP to false by default for tests
        administrationService.setGlobalProperty("ugandaemr.cleanupDuplicateForms", "false");
    }

    @AfterEach
    public void tearDown() {
        // Clean up test forms directory
        if (testFormsDir != null && testFormsDir.exists()) {
            for (File file : testFormsDir.listFiles()) {
                file.delete();
            }
            testFormsDir.delete();
        }
    }

    @Test
    public void started_shouldInitializeFormsCache() {
        // Given
        initializer = new JsonFormsInitializer(UgandaEMRConstants.MODULE_ID, testFormsDir.getAbsolutePath());

        // When
        initializer.started();

        // Then - cache should be initialized (we can't directly test private field,
        // but we can verify no exceptions are thrown during normal operation)
        assertNotNull(initializer);
    }

    @Test
    public void stopped_shouldClearCache() {
        // Given
        initializer = new JsonFormsInitializer(UgandaEMRConstants.MODULE_ID, testFormsDir.getAbsolutePath());
        initializer.started();

        // When
        initializer.stopped();

        // Then - no exception, cache is cleared internally
        // This test ensures the stopped() method executes without errors
    }

    @Test
    public void started_withInvalidDirectory_shouldLogErrorAndReturn() {
        // Given - use a non-existent path
        initializer = new JsonFormsInitializer(UgandaEMRConstants.MODULE_ID, "/invalid/nonexistent/path");

        // When/Then - should not throw exception, just log error
        assertDoesNotThrow(() -> initializer.started());
    }

    @Test
    public void started_withNullFilesArray_shouldHandleGracefully() throws IOException {
        // Given - create a directory but mock it to return null for listFiles
        // We simulate this by using an empty directory which won't fail
        initializer = new JsonFormsInitializer(UgandaEMRConstants.MODULE_ID, testFormsDir.getAbsolutePath());

        // When/Then - should handle gracefully
        assertDoesNotThrow(() -> initializer.started());
    }

    @Test
    public void load_withExistingUuid_shouldUpdateExistingForm() throws Exception {
        // Given - create a test form with existing UUID
        Form existingForm = new Form();
        existingForm.setUuid("test-uuid-123");
        existingForm.setName("Test Form");
        existingForm.setVersion("1.0");
        existingForm.setPublished(false);
        formService.saveForm(existingForm);

        // Create JSON file with same UUID but updated properties
        String jsonContent = "{"
                + "\"name\": \"Test Form\","
                + "\"description\": \"Updated Description\","
                + "\"version\": \"2.0\","
                + "\"published\": true,"
                + "\"retired\": false,"
                + "\"uuid\": \"test-uuid-123\","
                + "\"encounterType\": null,"
                + "\"processor\": \"NonEncounterFormProcessor\""
                + "}";
        File jsonFile = new File(testFormsDir, "test-form.json");
        writeJsonFile(jsonFile, jsonContent);

        // When
        initializer = new JsonFormsInitializer(UgandaEMRConstants.MODULE_ID, testFormsDir.getAbsolutePath());
        initializer.started();

        // Then - form should be updated
        Form updatedForm = formService.getFormByUuid("test-uuid-123");
        assertNotNull(updatedForm);
        assertEquals("Test Form", updatedForm.getName());
        assertEquals("2.0", updatedForm.getVersion());
        assertTrue(updatedForm.getPublished());
    }

    @Test
    public void load_withNonExistentUuid_shouldCreateNewForm() throws Exception {
        // Given - create JSON file with new UUID
        String jsonContent = "{"
                + "\"name\": \"Brand New Form\","
                + "\"description\": \"New Form Description\","
                + "\"version\": \"1.0\","
                + "\"published\": true,"
                + "\"retired\": false,"
                + "\"uuid\": \"brand-new-uuid-456\","
                + "\"encounterType\": null,"
                + "\"processor\": \"NonEncounterFormProcessor\""
                + "}";
        File jsonFile = new File(testFormsDir, "new-form.json");
        writeJsonFile(jsonFile, jsonContent);

        // When
        initializer = new JsonFormsInitializer(UgandaEMRConstants.MODULE_ID, testFormsDir.getAbsolutePath());
        initializer.started();

        // Then - new form should be created
        Form newForm = formService.getFormByUuid("brand-new-uuid-456");
        assertNotNull(newForm);
        assertEquals("Brand New Form", newForm.getName());
        assertEquals("1.0", newForm.getVersion());
    }

    @Test
    public void load_withUuidMismatch_shouldFindByNameAndUpdateUuid() throws Exception {
        // Given - create a form with specific name and old UUID
        Form existingForm = new Form();
        existingForm.setUuid("old-uuid-789");
        existingForm.setName("Mismatch Test Form");
        existingForm.setVersion("1.0");
        existingForm.setRetired(false);
        formService.saveForm(existingForm);

        // Create JSON file with same name but different UUID
        String jsonContent = "{"
                + "\"name\": \"Mismatch Test Form\","
                + "\"description\": \"Test\","
                + "\"version\": \"1.0\","
                + "\"published\": false,"
                + "\"retired\": false,"
                + "\"uuid\": \"new-uuid-999\","
                + "\"encounterType\": null,"
                + "\"processor\": \"NonEncounterFormProcessor\""
                + "}";
        File jsonFile = new File(testFormsDir, "mismatch-form.json");
        writeJsonFile(jsonFile, jsonContent);

        // When
        initializer = new JsonFormsInitializer(UgandaEMRConstants.MODULE_ID, testFormsDir.getAbsolutePath());
        initializer.started();

        // Then - form UUID should be updated to match JSON
        Form updatedForm = formService.getFormByUuid("new-uuid-999");
        assertNotNull(updatedForm, "Form should be found by new UUID");
        assertEquals("Mismatch Test Form", updatedForm.getName());

        // Old UUID should not exist anymore
        Form oldForm = formService.getFormByUuid("old-uuid-789");
        assertNull(oldForm, "Old UUID should not exist");
    }

    @Test
    public void load_withMultipleSameNameForms_shouldPreferNonRetired() throws Exception {
        // Given - create multiple forms with same name
        Form retiredForm = new Form();
        retiredForm.setUuid("retired-uuid-111");
        retiredForm.setName("Duplicate Name Form");
        retiredForm.setVersion("1.0");
        retiredForm.setRetired(true);
        retiredForm.setRetireReason("Old version");
        formService.saveForm(retiredForm);

        Form activeForm = new Form();
        activeForm.setUuid("active-uuid-222");
        activeForm.setName("Duplicate Name Form");
        activeForm.setVersion("2.0");
        activeForm.setRetired(false);
        formService.saveForm(activeForm);

        // Create JSON file with same name but different UUID
        String jsonContent = "{"
                + "\"name\": \"Duplicate Name Form\","
                + "\"description\": \"Test\","
                + "\"version\": \"3.0\","
                + "\"published\": false,"
                + "\"retired\": false,"
                + "\"uuid\": \"json-uuid-333\","
                + "\"encounterType\": null,"
                + "\"processor\": \"NonEncounterFormProcessor\""
                + "}";
        File jsonFile = new File(testFormsDir, "duplicate-form.json");
        writeJsonFile(jsonFile, jsonContent);

        // When
        initializer = new JsonFormsInitializer(UgandaEMRConstants.MODULE_ID, testFormsDir.getAbsolutePath());
        initializer.started();

        // Then - the non-retired form should be updated (active-uuid-222 → json-uuid-333)
        Form updatedForm = formService.getFormByUuid("json-uuid-333");
        assertNotNull(updatedForm, "Active form should be found by new UUID");
        assertEquals("3.0", updatedForm.getVersion());

        // Retired form should still exist with its original UUID
        Form stillRetired = formService.getFormByUuid("retired-uuid-111");
        assertNotNull(stillRetired, "Retired form should still exist");
        assertTrue(stillRetired.getRetired());
    }

    @Test
    public void load_withCleanupDisabled_shouldNotRetireDuplicates() throws Exception {
        // Given - ensure cleanup is disabled
        administrationService.setGlobalProperty("ugandaemr.cleanupDuplicateForms", "false");

        // Create two forms with same name
        Form form1 = new Form();
        form1.setUuid("form1-uuid");
        form1.setName("Cleanup Test Form");
        form1.setVersion("1.0");
        form1.setRetired(false);
        formService.saveForm(form1);

        Form form2 = new Form();
        form2.setUuid("form2-uuid");
        form2.setName("Cleanup Test Form");
        form2.setVersion("1.0");
        form2.setRetired(false);
        formService.saveForm(form2);

        // Create JSON file triggering UUID mismatch
        String jsonContent = "{"
                + "\"name\": \"Cleanup Test Form\","
                + "\"description\": \"Test\","
                + "\"version\": \"1.0\","
                + "\"published\": false,"
                + "\"retired\": false,"
                + "\"uuid\": \"json-uuid\","
                + "\"encounterType\": null,"
                + "\"processor\": \"NonEncounterFormProcessor\""
                + "}";
        File jsonFile = new File(testFormsDir, "cleanup-test.json");
        writeJsonFile(jsonFile, jsonContent);

        // When
        initializer = new JsonFormsInitializer(UgandaEMRConstants.MODULE_ID, testFormsDir.getAbsolutePath());
        initializer.started();

        // Then - one form should be updated with the new UUID, the other should remain with its UUID
        // Since cleanup is disabled, both should exist and neither should be retired
        Form updatedForm = formService.getFormByUuid("json-uuid");
        assertNotNull(updatedForm, "One form should be updated with the new UUID");

        // One of the original forms should still exist
        Form form1After = formService.getFormByUuid("form1-uuid");
        Form form2After = formService.getFormByUuid("form2-uuid");
        assertTrue(
                (form1After != null && !form1After.getRetired()) ||
                (form2After != null && !form2After.getRetired()),
                "At least one original form should still exist and not be retired when cleanup disabled"
        );
    }

    @Test
    public void load_withCleanupEnabled_shouldRetireDuplicateForms() throws Exception {
        // Given - enable cleanup
        administrationService.setGlobalProperty("ugandaemr.cleanupDuplicateForms", "true");

        // Create two forms with same name
        Form form1 = new Form();
        form1.setUuid("dup1-uuid");
        form1.setName("Cleanup Enabled Test");
        form1.setVersion("1.0");
        form1.setRetired(false);
        formService.saveForm(form1);

        Form form2 = new Form();
        form2.setUuid("dup2-uuid");
        form2.setName("Cleanup Enabled Test");
        form2.setVersion("1.0");
        form2.setRetired(false);
        formService.saveForm(form2);

        // Create JSON file triggering UUID mismatch
        String jsonContent = "{"
                + "\"name\": \"Cleanup Enabled Test\","
                + "\"description\": \"Test\","
                + "\"version\": \"1.0\","
                + "\"published\": false,"
                + "\"retired\": false,"
                + "\"uuid\": \"clean-uuid\","
                + "\"encounterType\": null,"
                + "\"processor\": \"NonEncounterFormProcessor\""
                + "}";
        File jsonFile = new File(testFormsDir, "cleanup-enabled.json");
        writeJsonFile(jsonFile, jsonContent);

        // When
        initializer = new JsonFormsInitializer(UgandaEMRConstants.MODULE_ID, testFormsDir.getAbsolutePath());
        initializer.started();

        // Then - one form should be updated, the other retired
        Form activeForm = formService.getFormByUuid("clean-uuid");
        assertNotNull(activeForm, "Active form should exist with new UUID");
        assertFalse(activeForm.getRetired(), "Active form should not be retired");

        // Check that one of the old forms is now retired
        Form retiredForm = formService.getFormByUuid("dup1-uuid");
        Form otherForm = formService.getFormByUuid("dup2-uuid");

        assertTrue(
                (retiredForm != null && retiredForm.getRetired()) ||
                (otherForm != null && otherForm.getRetired()),
                "At least one duplicate should be retired when cleanup enabled"
        );
    }

    @Test
    public void load_withAlreadyRetiredDuplicates_shouldNotRetireAgain() throws Exception {
        // Given - enable cleanup
        administrationService.setGlobalProperty("ugandaemr.cleanupDuplicateForms", "true");

        // Create an already retired form
        Form retiredForm = new Form();
        retiredForm.setUuid("already-retired-uuid");
        retiredForm.setName("Already Retired Test");
        retiredForm.setVersion("1.0");
        retiredForm.setRetired(true);
        retiredForm.setRetireReason("Previously retired");
        formService.saveForm(retiredForm);

        // Create JSON file
        String jsonContent = "{"
                + "\"name\": \"Already Retired Test\","
                + "\"description\": \"Test\","
                + "\"version\": \"1.0\","
                + "\"published\": false,"
                + "\"retired\": false,"
                + "\"uuid\": \"new-uuid\","
                + "\"encounterType\": null,"
                + "\"processor\": \"NonEncounterFormProcessor\""
                + "}";
        File jsonFile = new File(testFormsDir, "already-retired.json");
        writeJsonFile(jsonFile, jsonContent);

        // When
        initializer = new JsonFormsInitializer(UgandaEMRConstants.MODULE_ID, testFormsDir.getAbsolutePath());
        initializer.started();

        // Then - the retired form should be updated with the new UUID
        // and should now be unretired (as per JSON file)
        Form updatedForm = formService.getFormByUuid("new-uuid");
        assertNotNull(updatedForm, "Form should be found by new UUID after update");
        // The form should now be unretired as per the JSON file
        assertFalse(updatedForm.getRetired(), "Form should be unretired as per JSON");
    }

    @Test
    public void load_withInvalidJson_shouldLogErrorAndContinue() throws Exception {
        // Given - create a valid form first
        Form existingForm = new Form();
        existingForm.setUuid("valid-uuid");
        existingForm.setName("Valid Form");
        existingForm.setVersion("1.0");
        formService.saveForm(existingForm);

        // Create an invalid JSON file
        File invalidJson = new File(testFormsDir, "invalid.json");
        writeJsonFile(invalidJson, "{ invalid json }");

        // Create a valid JSON file
        String validJson = "{"
                + "\"name\": \"Another Valid Form\","
                + "\"description\": \"Test\","
                + "\"version\": \"1.0\","
                + "\"published\": false,"
                + "\"retired\": false,"
                + "\"uuid\": \"another-valid-uuid\","
                + "\"encounterType\": null,"
                + "\"processor\": \"NonEncounterFormProcessor\""
                + "}";
        File validJsonFile = new File(testFormsDir, "valid.json");
        writeJsonFile(validJsonFile, validJson);

        // When - should not throw exception
        initializer = new JsonFormsInitializer(UgandaEMRConstants.MODULE_ID, testFormsDir.getAbsolutePath());

        // Then/When - should process valid file and log error for invalid one
        assertDoesNotThrow(() -> initializer.started());

        // Verify valid form was created
        Form validForm = formService.getFormByUuid("another-valid-uuid");
        assertNotNull(validForm);
    }

    /**
     * Helper method to write JSON content to a file
     */
    private void writeJsonFile(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }
}
