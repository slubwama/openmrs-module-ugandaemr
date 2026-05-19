package org.openmrs.module.ugandaemr.activator;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.openmrs.EncounterType;
import org.openmrs.Form;
import org.openmrs.FormResource;
import org.openmrs.api.DatatypeService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.FormService;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.ClobDatatypeStorage;
import org.openmrs.util.OpenmrsUtil;


import java.io.File;
import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

public class JsonFormsInitializer implements Initializer {

    protected static final Log log = LogFactory.getLog(JsonFormsInitializer.class);

    protected static final String formsPath = "jsonforms/";


    public static final String AMPATH_FORMS_UUID = "794c4598-ab82-47ca-8d18-483a8abe6f4f";
    private static final String CLEANUP_DUPLICATE_FORMS_GP = "ugandaemr.cleanupDuplicateForms";

    protected String providerName;
    private String formFilePath;

    // Performance cache: all forms loaded once per initialization cycle
    private List<Form> allFormsCache;

    public JsonFormsInitializer(String newProviderName, String newFormFilePath) {
        this.providerName = newProviderName;
        this.formFilePath = newFormFilePath;
    }


    @Override
    public void started() {
        log.info("Setting HFE forms for " + getProviderName());

        // Clear any existing cache to handle module reload scenarios
        allFormsCache = null;

        // Initialize forms cache for performance (loaded once, used for all form lookups)
        FormService formService = Context.getFormService();
        try {
            allFormsCache = formService.getAllForms();
            if (allFormsCache != null) {
                log.debug("Cached " + allFormsCache.size() + " forms for efficient UUID/name lookup");
            }
        } catch (Exception e) {
            log.warn("Failed to cache forms, falling back to per-lookups: " + e.getMessage());
            allFormsCache = null;
        }

        // Scanning the forms resources folder
        String resolvedFormPath = formsPath;
        List<String> formPaths = new ArrayList<String>();
        File formsDir = null;

        if (!formFilePath.isEmpty()) {
            resolvedFormPath = this.formFilePath;
            formsDir = new File(resolvedFormPath);
        }

        if (formsDir == null || !formsDir.isDirectory()) {
            log.error("No HTML forms could be retrieved from the provided folder: " + getProviderName() + ":" + resolvedFormPath);
            return;
        }

        // Cache listFiles() result to avoid duplicate file system calls
        File[] files = formsDir.listFiles();
        if (files != null) {
            for (File file : files) {
                formPaths.add(resolvedFormPath + file.getName());
            }
            for (File file : files) {
                try {
                    load(file);
                } catch (Exception e) {
                    log.error("error loading form" + file.getName(), e);
                }
            }
        }
    }


    @Override
    public void stopped() {
        // Clear cache to free memory
        allFormsCache = null;
    }

    protected void load(File file) throws Exception {


        DatatypeService datatypeService = Context.getDatatypeService();
        FormService formService = Context.getFormService();
        EncounterService encounterService = Context.getEncounterService();

        String jsonString = FileUtils.readFileToString(file, StandardCharsets.UTF_8.toString());
        Map<String, Object> jsonFile = new ObjectMapper().readValue(jsonString, Map.class);

        String formName = (String) jsonFile.get("name");
        if (StringUtils.isBlank(formName)) {
            throw new Exception("Form Name is required");
        }

        String formDescription = (String) jsonFile.get("description");
        boolean formPublished = Boolean.TRUE.equals(jsonFile.get("published"));
        boolean formRetired = Boolean.TRUE.equals(jsonFile.get("retired"));

        String formProcessor = (String) jsonFile.get("processor");
        boolean isEncounterForm = formProcessor == null || StringUtils.isBlank(formProcessor)
                || formProcessor.equalsIgnoreCase("EncounterFormProcessor");

        EncounterType encounterType = null;
        String formEncounterType = (String) jsonFile.get("encounterType");
        if (formEncounterType != null) {
            encounterType = encounterService.getEncounterTypeByUuid(formEncounterType);
            if (encounterType == null) {
                throw new Exception("Form Encounter type " + formEncounterType + " could not be found. Please ensure that "
                        + "this encountertype is either loaded by Iniz or loaded in the system before Iniz runs.");
            }
        }

        if (isEncounterForm && encounterType == null) {
            throw new Exception("No encounter was found for this form. You should have an \"encounter\" entry whose value "
                    + "is the id of the encounter type to use for this form, e.g., \"encounter\": \"Emergency\".");
        }

        String formVersion = (String) jsonFile.get("version");
        if (formVersion == null) {
            throw new Exception("Form Version is required");
        }

        String uuid = (String) jsonFile.get("uuid");
        // Process Form
        // ISSUE-150 If form with uuid present then update it
        Form form = formService.getFormByUuid(uuid);

        // If not found by UUID, try to find by name (handles UUID changes during development)
        // WARNING: This will mutate the form's UUID to match the JSON file
        boolean uuidMismatch = false;
        List<Form> formsByName = null; // Track for cleanup to avoid re-scanning
        if (form == null) {
            // Use cached forms if available, otherwise fetch fresh
            List<Form> allForms = allFormsCache;
            if (allForms == null) {
                allForms = formService.getAllForms();
            }
            formsByName = new ArrayList<Form>();
            if (allForms != null) {
                for (Form f : allForms) {
                    if (formName.equals(f.getName())) {
                        formsByName.add(f);
                    }
                }
            }
            if (!formsByName.isEmpty()) {
                // Prefer non-retired forms, or take the first one if all are retired
                form = formsByName.stream()
                        .filter(f -> !f.getRetired())
                        .findFirst()
                        .orElse(formsByName.get(0));
                uuidMismatch = true;
                log.warn("Form '" + formName + "' found by name with different UUID. DB: " + form.getUuid()
                        + ", JSON: " + uuid + ". Updating DB UUID to match JSON.");
            }
        }

        if (form != null) {
            // If UUID mismatch, update the form's UUID to match the JSON file
            if (uuidMismatch && !uuid.equals(form.getUuid())) {
                form.setUuid(uuid);
            }
            boolean needToSaveForm = false;
            FormResource formResource = formService.getFormResource(form, "JSON schema");

            // Name
            if (!OpenmrsUtil.nullSafeEquals(form.getName(), formName)) {
                form.setName(formName);
                needToSaveForm = true;
            }

            // Description
            if (!OpenmrsUtil.nullSafeEquals(form.getDescription(), formDescription)) {
                form.setDescription(formDescription);
                needToSaveForm = true;
            }
            // Version
            if (!OpenmrsUtil.nullSafeEquals(form.getVersion(), formVersion)) {
                form.setVersion(formVersion);
                needToSaveForm = true;
            }
            // Add in schema
            // Published
            if (!OpenmrsUtil.nullSafeEquals(form.getPublished(), formPublished)) {
                form.setPublished(formPublished);
                needToSaveForm = true;
            }
            // Add to schema
            // Retired
            if (!OpenmrsUtil.nullSafeEquals(form.getRetired(), formRetired)) {
                form.setRetired(formRetired);
                if (formRetired && StringUtils.isBlank(form.getRetireReason())) {
                    form.setRetireReason("Retired by Initializer");
                }
                needToSaveForm = true;
            }
            // Add encounter to schema
            if (encounterType != null && !OpenmrsUtil.nullSafeEquals(form.getEncounterType(), encounterType)) {
                form.setEncounterType(encounterType);
                needToSaveForm = true;
            }

            if (formResource != null) {
                ClobDatatypeStorage clobData = datatypeService.getClobDatatypeStorageByUuid(formResource.getValueReference());

                if (clobData != null) {
                    clobData.setValue(jsonString);
                } else {
                    clobData = new ClobDatatypeStorage();
                    clobData.setValue(jsonString);
                    clobData.setUuid(UUID.randomUUID().toString());
                    formResource.setValueReferenceInternal(clobData.getUuid());
                    formService.saveFormResource(formResource);
                }

                datatypeService.saveClobDatatypeStorage(clobData);

            } else {
                createFormResource(form, UUID.randomUUID().toString(), jsonString);
            }

            if (needToSaveForm) {
                formService.saveForm(form);
            }

            // Cleanup: Retire duplicate forms with the same name but different UUID
            if (uuidMismatch) {
                cleanupDuplicateForms(form, formsByName, formName, uuid);
            }
        } else {
            createNewForm(uuid, formName, formDescription, formPublished, formRetired, encounterType, formVersion, jsonString);
        }
    }

    private void createNewForm(String uuid, String formName, String formDescription, Boolean formPublished, Boolean formRetired, EncounterType encounterType, String formVersion, String jsonString) {
        FormService formService = Context.getFormService();
        String clobUuid = UUID.randomUUID().toString();
        Form newForm = new Form();
        newForm.setName(formName);
        newForm.setVersion(formVersion);
        newForm.setUuid(uuid);
        newForm.setDescription(formDescription);
        newForm.setRetired(formRetired);
        newForm.setPublished(formPublished);
        newForm.setEncounterType(encounterType);
        newForm = formService.saveForm(newForm);
        createFormResource(newForm, clobUuid, jsonString);
    }


    private void createFormResource(Form form, String clobUuid, String jsonString) {
        FormService formService = Context.getFormService();
        DatatypeService datatypeService = Context.getDatatypeService();
        FormResource formResource;
        formResource = new FormResource();
        formResource.setName("JSON schema");
        formResource.setForm(form);
        formResource.setValueReferenceInternal(clobUuid);
        formResource.setDatatypeClassname("AmpathJsonSchema");
        formService.saveFormResource(formResource);
        ClobDatatypeStorage clobData = new ClobDatatypeStorage();
        clobData.setUuid(clobUuid);
        clobData.setValue(jsonString);
        datatypeService.saveClobDatatypeStorage(clobData);
    }

    /**
     * Cleanup duplicate forms with the same name but different UUID.
     * This is controlled by the global property "ugandaemr.cleanupDuplicateForms".
     * <p>
     * <b>Warning:</b> When a form is found by name during UUID mismatch handling,
     * its UUID is mutated to match the JSON file. This is intended for development
     * scenarios where UUIDs change, but be aware of potential side effects:
     * <ul>
     *   <li>External references to the old UUID will break</li>
     *   <li>Audit trails may be affected</li>
     *   <li>Replication/sync conflicts in distributed environments</li>
     * </ul>
     *
     * @param activeForm The form that was just updated (to keep)
     * @param formsByName Pre-collected list of forms with the same name (for efficiency)
     * @param formName The name of the form
     * @param activeUuid The UUID of the active form
     */
    private void cleanupDuplicateForms(Form activeForm, List<Form> formsByName, String formName, String activeUuid) {
        // Check if cleanup is enabled via global property
        String cleanupEnabled = Context.getAdministrationService()
                .getGlobalProperty(CLEANUP_DUPLICATE_FORMS_GP, "false");
        if (!"true".equalsIgnoreCase(cleanupEnabled)) {
            if (formsByName != null && formsByName.size() > 1) {
                log.info("Found " + (formsByName.size() - 1) + " duplicate form(s) with name '" + formName
                        + "'. Cleanup is disabled. Set global property '" + CLEANUP_DUPLICATE_FORMS_GP + "=true' to enable.");
            }
            return;
        }

        if (formsByName == null || formsByName.isEmpty()) {
            return;
        }

        FormService formService = Context.getFormService();
        int retiredCount = 0;
        for (Form duplicateForm : formsByName) {
            // Skip the active form we just updated
            if (duplicateForm.getFormId().equals(activeForm.getFormId())) {
                continue;
            }
            // Skip already retired forms
            if (duplicateForm.getRetired()) {
                continue;
            }
            // Check if this is a duplicate (same name, different UUID)
            if (!activeUuid.equals(duplicateForm.getUuid())) {
                log.warn("Retiring duplicate form '" + formName + "' with UUID " + duplicateForm.getUuid()
                        + " (replaced by UUID " + activeUuid + ")");
                duplicateForm.setRetired(true);
                duplicateForm.setRetireReason("Replaced by form with UUID " + activeUuid + " during JsonFormsInitializer cleanup");
                formService.saveForm(duplicateForm);
                retiredCount++;
            }
        }

        if (retiredCount > 0) {
            log.info("Retired " + retiredCount + " duplicate form(s) with name '" + formName + "'");
        }
    }

    public String getProviderName() {
        return this.providerName;
    }

}
