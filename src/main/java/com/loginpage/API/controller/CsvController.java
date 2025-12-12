package com.loginpage.API.controller;

import com.loginpage.API.service.CsvService;
import com.loginpage.API.service.AuditLogService;
import com.loginpage.API.model.CsvFile;
import com.loginpage.API.model.CsvRecord;
import com.loginpage.API.model.CsvRecordEntity;
import com.loginpage.API.model.MappingTemplate;
import com.loginpage.API.model.RecordEditHistory;
import com.loginpage.API.repository.CsvFileRepository;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.lang.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

@Controller
public class CsvController {

    @Autowired
    private CsvService csvService;

    @Autowired
    private CsvFileRepository csvFileRepository;

    @Autowired
    private AuditLogService auditLogService;

    @PostMapping("/upload-csv")
    public String uploadCsv(@RequestParam("file") MultipartFile file,
                            @RequestParam(value = "templateId", required = false) Long templateId,
                            Model model,
                            HttpServletRequest request) {
        try {
            if (templateId == null) {
                model.addAttribute("error", "Please select a template before uploading.");
                prepareUploadPage(model, null);
                return "csv-upload";
            }
            Optional<MappingTemplate> templateOpt = csvService.getMappingTemplate(templateId);
            if (templateOpt.isEmpty()) {
                model.addAttribute("error", "Template not found. Please try again.");
                prepareUploadPage(model, templateId);
                return "csv-upload";
            }

            // Save uploaded CSV to database (keeps history of uploads)
            CsvFile saved = csvService.saveFileToDb(file);
            MappingTemplate template = templateOpt.get();
            Map<String, String> templateMapping = csvService.readTemplateMapping(template);
            Map<String, String> finalMapping = buildFinalMappingFromTemplate(templateMapping);

            csvService.saveMappingForFile(saved, finalMapping);
            CsvService.CsvResult result = csvService.parseAndPersistWithMapping(saved, finalMapping);
            populateResultModel(model, saved, finalMapping, result);
            
            // Log audit trail - get username from session or use a default
            String username = getCurrentUsername(request);
            String details = "File: " + saved.getFilename() + ", Template: " + template.getName() + 
                           ", Success: " + result.getSuccessRecords().size() + 
                           ", Failed: " + result.getFailedRecords().size();
            auditLogService.logAction(username, "CSV Upload", details, request);
            
            model.addAttribute("success", "CSV file uploaded and processed successfully!");
            model.addAttribute("templateMessage", "Upload completed using template '" + template.getName() + "'.");
            prepareUploadPage(model, templateId);
            return "csv-upload";

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Error processing CSV file: " + e.getMessage());
            prepareUploadPage(model, null);
            return "csv-upload";
        }
    }

    @GetMapping("/csv/map/{id}")
    public String showMappingPage(@PathVariable("id") @NonNull Long id,
                                  @RequestParam(value = "templateId", required = false) Long templateId,
                                  Model model) {
        Optional<CsvFile> maybe = csvFileRepository.findById(id);
        if (maybe.isEmpty()) {
            model.addAttribute("error", "CSV file not found");
            return "csv-upload";
        }
        CsvFile csvFile = maybe.get();
        List<String> headers = Collections.emptyList();
        List<String> sampleValues = Collections.emptyList();
        try {
            headers = csvService.extractHeaders(csvFile);
            sampleValues = csvService.getFirstRecordValues(csvFile);
        } catch (Exception ex) {
            model.addAttribute("error", "Unable to read CSV headers: " + ex.getMessage());
        }
        model.addAttribute("csvFile", csvFile);
        model.addAttribute("csvHeaders", headers);
        model.addAttribute("targetFields", CsvService.EXPECTED_HEADERS);
        model.addAttribute("columnOptions", buildColumnOptions(headers, sampleValues));
        model.addAttribute("columnLabels", buildColumnLabelMap(headers));
        model.addAttribute("mappingTemplates", csvService.getMappingTemplates());
        model.addAttribute("selectedTemplateId", templateId);

        // Default to empty selections (AUTO) - user will manually select columns
        Map<String, String> slugSelections = buildSlugSelections(Collections.emptyMap());

        if (templateId != null) {
            Optional<MappingTemplate> templateOpt = csvService.getMappingTemplate(templateId);
            if (templateOpt.isPresent()) {
                try {
                    Map<String, String> templateMap = csvService.readTemplateMapping(templateOpt.get());
                    slugSelections = buildSlugSelections(templateMap);
                    model.addAttribute("templateSelected", templateOpt.get().getName());
                } catch (Exception ex) {
                    model.addAttribute("templateError", "Unable to load template: " + ex.getMessage());
                }
            } else {
                model.addAttribute("templateError", "Template not found.");
            }
        }

        model.addAttribute("mappingUsed", slugSelections);
        model.addAttribute("sampleBySlug", buildSampleBySlug(sampleValues, slugSelections));
        return "csv-map";
    }

    @PostMapping("/csv/map/{id}/import")
    public String importWithMapping(@PathVariable("id") @NonNull Long id,
                                    @RequestParam Map<String, String> params,
                                    Model model) {
        try {
        Optional<CsvFile> maybe = csvFileRepository.findById(id);
        if (maybe.isEmpty()) {
            model.addAttribute("error", "CSV file not found");
            return "csv-upload";
        }
        CsvFile csvFile = maybe.get();

        // csvToTarget holds selections keyed by slug (e.g. field_1, field_2 ...)
        Map<String, String> csvToTarget = extractSlugSelections(params);

        // Convert slug-based selections into a mapping keyed by the expected header names.
        // If the user selected "AUTO" (or left blank), consult the service to compute
        // an automatic mapping from expected header -> best column index.
        Map<String, String> finalMapping = new HashMap<>();
        List<String> expected = CsvService.EXPECTED_HEADERS;
        Map<String, String> autoMap = Collections.emptyMap();
        try {
            autoMap = csvService.computeAutoMapping(csvFile);
        } catch (Exception ex) {
            // if auto mapping fails, fall back to IGNORE for AUTO selections
            autoMap = Collections.emptyMap();
        }

        for (int i = 0; i < expected.size(); i++) {
            String expectedHeader = expected.get(i);
            String slug = "field_" + (i + 1);
            String sel = csvToTarget.getOrDefault(slug, "").trim();
            if (sel.isEmpty() || sel.equalsIgnoreCase("AUTO")) {
                // prefer the auto-detected value if available
                String autoVal = autoMap.getOrDefault(expectedHeader, "IGNORE");
                finalMapping.put(expectedHeader, autoVal == null ? "IGNORE" : autoVal);
            } else {
                finalMapping.put(expectedHeader, sel);
            }
        }

        try {
            // persist the mapping choice so the CsvFile is aware of the mapping used
            csvService.saveMappingForFile(csvFile, finalMapping);

            CsvService.CsvResult result = csvService.parseAndPersistWithMapping(csvFile, finalMapping);
            populateResultModel(model, csvFile, finalMapping, result);

            // Return mapped results view (do not force showing raw CSV table here so mapped results are visible)
            return "csv-upload";
        } catch (Exception ex) {
            ex.printStackTrace();
            model.addAttribute("error", "Unexpected error during import: " + ex.getMessage());
            model.addAttribute("csvFile", csvFile);
            // safe header extraction for error page
            List<String> headersForView;
            try {
                headersForView = csvService.extractHeaders(csvFile);
            } catch (Exception headerEx) {
                headersForView = Collections.emptyList();
            }
            model.addAttribute("csvHeaders", headersForView);
            model.addAttribute("columnOptions", buildColumnOptions(headersForView, null));
            model.addAttribute("columnLabels", buildColumnLabelMap(headersForView));
            model.addAttribute("targetFields", CsvService.EXPECTED_HEADERS);
            // preserve user's slug-based selections so they can correct mapping
            model.addAttribute("mappingUsed", csvToTarget);
            List<String> sampleValues = Collections.emptyList();
            try {
                sampleValues = csvService.getFirstRecordValues(csvFile);
            } catch (Exception ignored) {}
            model.addAttribute("sampleBySlug", buildSampleBySlug(sampleValues, csvToTarget));
            model.addAttribute("mappingTemplates", csvService.getMappingTemplates());
            return "csv-map";
        }
        } catch (Throwable t) {
            // Log full stack trace and return mapping page with helpful debug info
            t.printStackTrace();
            model.addAttribute("error", "Unexpected error during import: " + t.getClass().getSimpleName() + ": " + t.getMessage());

            // try to populate mappingUsed so template preserves selections
            Map<String,String> mappingUsed = new HashMap<>();
            for (Map.Entry<String,String> p : params.entrySet()) {
                String key = p.getKey();
                if (key.startsWith("mapping[") && key.endsWith("]")) {
                    String slug = key.substring(8, key.length()-1);
                    mappingUsed.put(slug, p.getValue());
                }
            }
            model.addAttribute("mappingUsed", mappingUsed);

            model.addAttribute("columnOptions", buildColumnOptions(Collections.emptyList(), null));
            model.addAttribute("columnLabels", buildColumnLabelMap(Collections.emptyList()));
            try {
                Optional<CsvFile> maybe = csvFileRepository.findById(id);
                if (maybe.isPresent()) {
                    CsvFile csvFile = maybe.get();
                    model.addAttribute("csvFile", csvFile);
                    List<String> headers = csvService.extractHeaders(csvFile);
                    model.addAttribute("csvHeaders", headers);
                    model.addAttribute("targetFields", CsvService.EXPECTED_HEADERS);
                    model.addAttribute("columnLabels", buildColumnLabelMap(headers));
                    List<String> samples = csvService.getFirstRecordValues(csvFile);
                    model.addAttribute("columnOptions", buildColumnOptions(headers, samples));
                    model.addAttribute("sampleBySlug", buildSampleBySlug(samples, mappingUsed));
                }
            } catch (Exception ex) { /* ignore */ }
            model.addAttribute("mappingTemplates", csvService.getMappingTemplates());

            return "csv-map";
        }
    }

    @PostMapping("/csv/map/{id}/template/save")
    public String saveTemplate(@PathVariable("id") @NonNull Long id,
                               @RequestParam Map<String, String> params,
                               RedirectAttributes redirectAttributes) {
        String templateName = params.getOrDefault("templateName", "").trim();
        if (templateName.isEmpty()) {
            redirectAttributes.addFlashAttribute("templateError", "Template name is required.");
            return "redirect:/csv/map/" + id;
        }

        Map<String, String> slugSelections = extractSlugSelections(params);
        Map<String, String> headerMapping = slugToHeaderMapping(slugSelections);
        try {
            MappingTemplate saved = csvService.saveMappingTemplate(templateName, headerMapping);
            redirectAttributes.addFlashAttribute("templateMessage", "Template '" + templateName + "' saved.");
            return "redirect:/csv/map/" + id + "?templateId=" + saved.getId();
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("templateError", "Unable to save template: " + ex.getMessage());
            return "redirect:/csv/map/" + id;
        }
    }

    @PostMapping("/csv/map/{id}/template/apply")
    public String applyTemplate(@PathVariable("id") @NonNull Long id,
                                @RequestParam("templateId") Long templateId,
                                RedirectAttributes redirectAttributes) {
        if (templateId == null) {
            redirectAttributes.addFlashAttribute("templateError", "Please select a template.");
            return "redirect:/csv/map/" + id;
        }
        redirectAttributes.addFlashAttribute("templateMessage", "Template applied.");
        return "redirect:/csv/map/" + id + "?templateId=" + templateId;
    }

    @GetMapping("/csv/history")
    public String csvHistory(Model model) {
        List<CsvFile> files = csvFileRepository.findAllByOrderByUploadedAtDesc();
        
        // Add statistics for each file
        Map<Long, Map<String, Integer>> fileStats = new HashMap<>();
        for (CsvFile file : files) {
            fileStats.put(file.getId(), csvService.getFileStatistics(file.getId()));
        }
        model.addAttribute("fileStats", fileStats);
        model.addAttribute("csvFiles", files);
        model.addAttribute("mappingTemplates", csvService.getMappingTemplates());
        return "csv-history";
    }

    @PostMapping("/csv/{id}/save-template")
    @org.springframework.web.bind.annotation.ResponseBody
    public ResponseEntity<Map<String, Object>> saveTemplateToFile(@PathVariable("id") @NonNull Long id,
                                                                   @RequestParam(value = "templateId", required = false) Long templateId,
                                                                   HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<CsvFile> maybe = csvFileRepository.findById(id);
            if (maybe.isEmpty()) {
                response.put("success", false);
                response.put("error", "CSV file not found");
                return ResponseEntity.badRequest().body(response);
            }

            CsvFile csvFile = maybe.get();

            // Require template
            if (templateId == null || templateId <= 0) {
                response.put("success", false);
                response.put("error", "Please select a template to save.");
                return ResponseEntity.badRequest().body(response);
            }
            
            Optional<MappingTemplate> templateOpt = csvService.getMappingTemplate(templateId);
            if (templateOpt.isEmpty()) {
                response.put("success", false);
                response.put("error", "Template not found. Please select a valid template.");
                return ResponseEntity.badRequest().body(response);
            }
            
            MappingTemplate template = templateOpt.get();
            Map<String, String> templateMapping = csvService.readTemplateMapping(template);
            Map<String, String> finalMapping = buildFinalMappingFromTemplate(templateMapping);
            
            // Save the mapping to the file (without reprocessing)
            csvService.saveMappingForFile(csvFile, finalMapping);

            // Log audit trail
            String username = getCurrentUsername(request);
            String details = "Saved template '" + template.getName() + "' mapping to file: " + csvFile.getFilename();
            auditLogService.logAction(username, "SAVE_TEMPLATE_TO_FILE", details, request);

            response.put("success", true);
            response.put("message", "Template mapping saved successfully to file!");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return new ResponseEntity<>(response, headers, org.springframework.http.HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("error", "Error saving template mapping: " + e.getMessage());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return new ResponseEntity<>(response, headers, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/csv/{id}/view")
    public String viewCsvRecords(@PathVariable("id") @NonNull Long id,
                                 Model model,
                                 HttpServletRequest request) {
        try {
            Optional<CsvFile> maybe = csvFileRepository.findById(id);
            if (maybe.isEmpty()) {
                model.addAttribute("error", "CSV file not found");
                prepareUploadPage(model, null);
                return "csv-upload";
            }

            CsvFile csvFile = maybe.get();
            
            // Get records for this file
            CsvService.CsvResult result = csvService.getRecordsForFile(id);
            
            // Load the mapping that was used (if available)
            Map<String, String> finalMapping = Collections.emptyMap();
            try {
                if (csvFile.getMappingJson() != null && !csvFile.getMappingJson().isEmpty()) {
                    // Parse the mapping JSON directly from csvFile
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    finalMapping = mapper.readValue(
                        csvFile.getMappingJson(), 
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {}
                    );
                } else {
                    // If no mapping saved, try to compute auto mapping
                    finalMapping = csvService.computeAutoMapping(csvFile);
                }
            } catch (Exception e) {
                // If we can't load mapping, try auto mapping
                try {
                    finalMapping = csvService.computeAutoMapping(csvFile);
                } catch (Exception ex) {
                    // If that fails too, use empty mapping
                    finalMapping = Collections.emptyMap();
                }
            }
            
            // Populate model similar to populateResultModel
            populateResultModel(model, csvFile, finalMapping, result);
            
            // Add file info for display
            model.addAttribute("viewingHistory", true);
            model.addAttribute("historyFileId", id);
            model.addAttribute("historyFileName", csvFile.getFilename());
            model.addAttribute("historyUploadDate", csvFile.getUploadedAt());
            
            prepareUploadPage(model, null);
            
            // Log audit trail
            String username = getCurrentUsername(request);
            auditLogService.logAction(username, "VIEW_CSV_RECORDS", "Viewed records for file: " + csvFile.getFilename(), request);
            
            return "csv-upload";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Error loading CSV records: " + e.getMessage());
            prepareUploadPage(model, null);
            return "csv-upload";
        }
    }

    @PostMapping("/csv/{id}/reprocess")
    public Object reprocessCsv(@PathVariable("id") @NonNull Long id,
                               @RequestParam(value = "templateId", required = false) Long templateId,
                               Model model,
                               HttpServletRequest request) {
        // Check if this is an AJAX request
        boolean isAjax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
        
        if (isAjax) {
            // Return JSON response for AJAX requests
            Map<String, Object> response = new HashMap<>();
            try {
                Optional<CsvFile> maybe = csvFileRepository.findById(id);
                if (maybe.isEmpty()) {
                    response.put("success", false);
                    response.put("error", "CSV file not found");
                    return ResponseEntity.badRequest().body(response);
                }

                CsvFile csvFile = maybe.get();
                
                // Verify CSV content exists
                if (csvFile.getContent() == null || csvFile.getContent().trim().isEmpty()) {
                    response.put("success", false);
                    response.put("error", "CSV file content is missing. Cannot re-extract without file content.");
                    return ResponseEntity.badRequest().body(response);
                }
                
                Map<String, String> finalMapping;

                // Require template for re-extraction
                if (templateId == null || templateId <= 0) {
                    response.put("success", false);
                    response.put("error", "Please select a template to re-extract the file.");
                    return ResponseEntity.badRequest().body(response);
                }
                
                Optional<MappingTemplate> templateOpt = csvService.getMappingTemplate(templateId);
                if (templateOpt.isEmpty()) {
                    response.put("success", false);
                    response.put("error", "Template not found. Please select a valid template.");
                    return ResponseEntity.badRequest().body(response);
                }
                
                MappingTemplate template = templateOpt.get();
                Map<String, String> templateMapping = csvService.readTemplateMapping(template);
                finalMapping = buildFinalMappingFromTemplate(templateMapping);
                csvService.saveMappingForFile(csvFile, finalMapping);
                
                // Store template name for audit log
                String templateName = template.getName();

                // Delete existing records for this file before re-processing
                csvService.deleteRecordsForFile(id);

                // Re-process the file
                CsvService.CsvResult result = csvService.parseAndPersistWithMapping(csvFile, finalMapping);
                
                // Get updated statistics
                Map<String, Integer> stats = csvService.getFileStatistics(id);

                // Log audit trail
                String username = getCurrentUsername(request);
                String details = "Re-extracted file: " + csvFile.getFilename() + 
                               " with template: " + templateName +
                               ", Success: " + result.getSuccessRecords().size() + 
                               ", Failed: " + result.getFailedRecords().size();
                auditLogService.logAction(username, "RE-EXTRACT_CSV", details, request);

                response.put("success", true);
                response.put("message", "File re-extracted successfully!");
                response.put("stats", stats);
                response.put("successCount", result.getSuccessRecords().size());
                response.put("failedCount", result.getFailedRecords().size());
                response.put("totalCount", result.getSuccessRecords().size() + result.getFailedRecords().size());
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                return new ResponseEntity<>(response, headers, org.springframework.http.HttpStatus.OK);
            } catch (Exception e) {
                e.printStackTrace();
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Error re-processing CSV file: " + e.getMessage());
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                return new ResponseEntity<>(errorResponse, headers, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        
        // Regular form submission - return view
        try {
            Optional<CsvFile> maybe = csvFileRepository.findById(id);
            if (maybe.isEmpty()) {
                model.addAttribute("error", "CSV file not found");
                prepareUploadPage(model, null);
                return "csv-upload";
            }

            CsvFile csvFile = maybe.get();
            Map<String, String> finalMapping;

            // Require template for re-extraction
            if (templateId == null || templateId <= 0) {
                model.addAttribute("error", "Please select a template to re-extract the file.");
                return csvHistory(model);
            }
            
            Optional<MappingTemplate> templateOpt = csvService.getMappingTemplate(templateId);
            if (templateOpt.isEmpty()) {
                model.addAttribute("error", "Template not found. Please select a valid template.");
                return csvHistory(model);
            }
            
            MappingTemplate template = templateOpt.get();
            Map<String, String> templateMapping = csvService.readTemplateMapping(template);
            finalMapping = buildFinalMappingFromTemplate(templateMapping);
            csvService.saveMappingForFile(csvFile, finalMapping);
            
            // Store template name for audit log
            String templateName = template.getName();

            // Delete existing records for this file before re-processing
            csvService.deleteRecordsForFile(id);

            // Re-process the file
            CsvService.CsvResult result = csvService.parseAndPersistWithMapping(csvFile, finalMapping);
            populateResultModel(model, csvFile, finalMapping, result);

            // Add file info for display
            model.addAttribute("viewingHistory", true);
            model.addAttribute("historyFileId", id);
            model.addAttribute("historyFileName", csvFile.getFilename());
            model.addAttribute("historyUploadDate", csvFile.getUploadedAt());
            model.addAttribute("success", "CSV file re-processed successfully!");

            prepareUploadPage(model, templateId);

            // Log audit trail
            String username = getCurrentUsername(request);
            String details = "Re-extracted file: " + csvFile.getFilename() + 
                           " with template: " + templateName +
                           ", Success: " + result.getSuccessRecords().size() + 
                           ", Failed: " + result.getFailedRecords().size();
            auditLogService.logAction(username, "RE-EXTRACT_CSV", details, request);

            return "csv-upload";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Error re-processing CSV file: " + e.getMessage());
            return viewCsvRecords(id, model, request);
        }
    }

    private List<Integer> buildColumnOptions(List<String> headers, List<String> sampleValues) {
        // Always provide options 1-25 for consistency
        List<Integer> options = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            options.add(i);
        }
        return options;
    }

    private Map<String, String> buildSlugSelections(Map<String, String> headerToColumn) {
        Map<String, String> selections = new HashMap<>();
        List<String> expected = CsvService.EXPECTED_HEADERS;
        for (int i = 0; i < expected.size(); i++) {
            String slug = "field_" + (i + 1);
            String header = expected.get(i);
            String value = "";
            if (headerToColumn != null && headerToColumn.containsKey(header)) {
                value = headerToColumn.get(header);
            }
            selections.put(slug, value == null ? "" : value);
        }
        return selections;
    }

    private Map<String, String> buildSampleBySlug(List<String> sampleValues, Map<String, String> slugSelections) {
        Map<String, String> samples = new HashMap<>();
        int total = CsvService.EXPECTED_HEADERS.size();
        for (int i = 0; i < total; i++) {
            String slug = "field_" + (i + 1);
            String selection = slugSelections != null ? slugSelections.get(slug) : null;
            String sample = "";
            if (sampleValues != null && selection != null && !selection.trim().isEmpty() && !"IGNORE".equalsIgnoreCase(selection)) {
                try {
                    int idx = Integer.parseInt(selection) - 1;
                    if (idx >= 0 && idx < sampleValues.size()) {
                        sample = sampleValues.get(idx);
                    }
                } catch (NumberFormatException ignored) {}
            }
            if ((sample == null || sample.isEmpty()) && sampleValues != null && i < sampleValues.size()) {
                sample = sampleValues.get(i);
            }
            samples.put(slug, sample == null ? "" : sample);
        }
        return samples;
    }

    private Map<String, String> buildColumnLabelMap(List<String> headers) {
        Map<String, String> labels = new HashMap<>();
        if (headers == null) return labels;
        for (int i = 0; i < headers.size(); i++) {
            labels.put(String.valueOf(i + 1), headers.get(i));
        }
        return labels;
    }

    private void prepareUploadPage(Model model, Long selectedTemplateId) {
        List<MappingTemplate> templates = csvService.getMappingTemplates();
        model.addAttribute("mappingTemplates", templates);
        if (selectedTemplateId != null) model.addAttribute("selectedTemplateId", selectedTemplateId);
        boolean hasTemplates = templates != null && !templates.isEmpty();
        model.addAttribute("hasTemplates", hasTemplates);
        if (!hasTemplates) {
            model.addAttribute("templateInfo", "No templates available yet. Please create one before uploading.");
        }
    }

    private Map<String, String> buildFinalMappingFromTemplate(Map<String, String> templateMapping) {
        Map<String, String> finalMapping = new HashMap<>();
        List<String> expected = CsvService.EXPECTED_HEADERS;
        for (String expectedHeader : expected) {
            String val = templateMapping != null ? templateMapping.get(expectedHeader) : null;
            if (val == null || val.trim().isEmpty()) {
                val = "IGNORE";
            }
            finalMapping.put(expectedHeader, val);
        }
        return finalMapping;
    }

    private void populateResultModel(Model model, CsvFile csvFile, Map<String, String> finalMapping, CsvService.CsvResult result) {
        int successCount = result.getSuccessRecords().size();
        int failedCount = result.getFailedRecords().size();
        int rowsPerPage = 20;
        
        model.addAttribute("successRecords", result.getSuccessRecords());
        model.addAttribute("failedRecords", result.getFailedRecords());
        model.addAttribute("successCount", successCount);
        model.addAttribute("failedCount", failedCount);
        
        // Add validation errors for failed records (indexed by record index)
        Map<Integer, List<String>> validationErrors = new HashMap<>();
        int index = 0;
        for (CsvRecord failedRecord : result.getFailedRecords()) {
            validationErrors.put(index, csvService.getValidationErrors(failedRecord));
            index++;
        }
        model.addAttribute("validationErrors", validationErrors);
        model.addAttribute("successTotalPages", Math.max(1, (successCount + rowsPerPage - 1) / rowsPerPage));
        model.addAttribute("failedTotalPages", Math.max(1, (failedCount + rowsPerPage - 1) / rowsPerPage));
        model.addAttribute("expectedHeaders", CsvService.EXPECTED_HEADERS);
        model.addAttribute("csvFileId", csvFile.getId());
        
        // Fetch record entities to get IDs for editing
        try {
            List<com.loginpage.API.model.CsvRecordEntity> allEntities = csvService.getRecordEntities(csvFile.getId());
            // Create a map: row index -> entity ID (matching by data content since we don't have row numbers in CsvRecord)
            Map<String, Long> dataToEntityId = new HashMap<>();
            for (com.loginpage.API.model.CsvRecordEntity entity : allEntities) {
                dataToEntityId.put(entity.getData(), entity.getId());
            }
            model.addAttribute("dataToEntityId", dataToEntityId);
        } catch (Exception e) {
            // Ignore if entities not found
        }

        try {
            List<String> rawHeaders = csvService.extractHeaders(csvFile);
            List<List<String>> rawRows = csvService.getAllRows(csvFile);
            model.addAttribute("rawHeaders", rawHeaders);
            model.addAttribute("rawRows", rawRows);
        } catch (Exception ignore) {}

        List<String> displayFields = new ArrayList<>();
        List<String> displayTitles = new ArrayList<>();
        List<Map.Entry<String, String>> mapped = new ArrayList<>();
        for (Map.Entry<String, String> me : finalMapping.entrySet()) {
            String v = me.getValue();
            if (v != null && !v.equalsIgnoreCase("IGNORE")) mapped.add(me);
        }
        mapped.sort((a, b) -> {
            try { return Integer.compare(Integer.parseInt(a.getValue()), Integer.parseInt(b.getValue())); }
            catch (Exception ex) { return 0; }
        });

        for (Map.Entry<String, String> e : mapped) {
            String expectedHeader = e.getKey();
            displayFields.add(expectedHeader);
            displayTitles.add(expectedHeader);
        }
        if (!displayFields.isEmpty()) {
            model.addAttribute("displayFields", displayFields);
            model.addAttribute("displayTitles", displayTitles);
        }
    }

    private Map<String, String> extractSlugSelections(Map<String, String> params) {
        Map<String, String> csvToTarget = new HashMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            String key = e.getKey();
            if (key.startsWith("mapping[") && key.endsWith("]")) {
                String slug = key.substring(8, key.length() - 1);
                csvToTarget.put(slug, e.getValue());
            }
        }
        return csvToTarget;
    }

    private Map<String, String> slugToHeaderMapping(Map<String, String> slugSelections) {
        Map<String, String> headerMap = new HashMap<>();
        List<String> expected = CsvService.EXPECTED_HEADERS;
        for (int i = 0; i < expected.size(); i++) {
            String slug = "field_" + (i + 1);
            String header = expected.get(i);
            headerMap.put(header, slugSelections.getOrDefault(slug, ""));
        }
        return headerMap;
    }

    @GetMapping("/csv/upload")
    public String csvUploadPage(Model model) {
        prepareUploadPage(model, null);
        return "csv-upload";
    }

    @GetMapping("/csv/{id}/download")
    public ResponseEntity<Resource> downloadCsv(@PathVariable("id") @NonNull Long id,
                                                  HttpServletRequest request) {
        Optional<CsvFile> maybe = csvFileRepository.findById(id);
        if (maybe.isEmpty()) return ResponseEntity.notFound().build();
        CsvFile cf = maybe.get();
        String content = cf.getContent();
        if (content == null) content = "";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(Objects.requireNonNull(bytes));
        MediaType mediaType = Objects.requireNonNull(MediaType.TEXT_PLAIN);
        
        // Log audit trail
        String username = getCurrentUsername(request);
        auditLogService.logAction(username, "CSV Download", "File: " + cf.getFilename(), request);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + cf.getFilename() + "\"")
                .contentType(mediaType)
                .contentLength(bytes.length)
                .body(resource);
    }

    private String getCurrentUsername(HttpServletRequest request) {
        // Try to get username from session or request parameters
        // For now, return a default - you can enhance this with proper session management
        return request.getParameter("username") != null ? 
               request.getParameter("username") : "System";
    }

    /**
     * API endpoint to update a CSV record
     */
    @PostMapping("/api/csv/record/{recordId}/update")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateRecord(
            @PathVariable @NonNull Long recordId,
            @RequestBody Map<String, String> newFields,
            HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            CsvRecordEntity updated = csvService.updateRecord(recordId, newFields);
            
            // Get updated statistics for the file
            Long fileId = updated.getCsvFile().getId();
            Map<String, Integer> stats = csvService.getFileStatistics(fileId);
            
            response.put("success", true);
            response.put("message", "Record updated successfully");
            response.put("recordId", updated.getId());
            response.put("isSuccess", updated.isSuccess());
            response.put("fileId", fileId);
            response.put("stats", stats); // Include updated statistics
            
            auditLogService.logAction(getCurrentUsername(request), 
                "UPDATE_RECORD", 
                "Updated CSV record ID: " + recordId, 
                request);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error updating record: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * API endpoint to get file statistics
     */
    @GetMapping("/api/csv/file/{fileId}/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getFileStats(@PathVariable @NonNull Long fileId) {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Integer> stats = csvService.getFileStatistics(fileId);
            response.put("success", true);
            response.put("stats", stats);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error getting statistics: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * API endpoint to get edit history for a CSV file
     */
    @GetMapping("/api/csv/file/{fileId}/edit-history")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getEditHistory(@PathVariable @NonNull Long fileId) {
        try {
            List<RecordEditHistory> history = csvService.getEditHistory(fileId);
            List<Map<String, Object>> result = new ArrayList<>();
            
            for (RecordEditHistory h : history) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", h.getId());
                item.put("recordId", h.getRecord() != null ? h.getRecord().getId() : null);
                item.put("oldData", h.getOldData());
                item.put("newData", h.getNewData());
                item.put("changedFields", h.getChangedFields());
                item.put("editedAt", h.getEditedAt());
                item.put("editedBy", h.getEditedBy());
                result.add(item);
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
