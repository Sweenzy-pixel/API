package com.loginpage.API.controller;

import com.loginpage.API.model.MappingTemplate;
import com.loginpage.API.service.CsvService;
import com.loginpage.API.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/csv/template")
public class TemplateController {

    @Autowired
    private CsvService csvService;

    @Autowired
    private AuditLogService auditLogService;

    @GetMapping
    public String showTemplateBuilder(@RequestParam(value = "templateId", required = false) Long templateId,
                                      Model model) {
        List<String> targetFields = CsvService.EXPECTED_HEADERS;
        Map<String, String> mappingUsed = defaultSlugSelections();

        if (templateId != null) {
            Optional<MappingTemplate> templateOpt = csvService.getMappingTemplate(templateId);
            if (templateOpt.isPresent()) {
                try {
                    MappingTemplate template = templateOpt.get();
                    // Debug: Log the raw JSON
                    System.out.println("Template JSON: " + template.getMappingJson());
                    Map<String, String> headerMap = csvService.readTemplateMapping(template);
                    // Debug: Log what we're reading from template
                    System.out.println("Template headerMap: " + headerMap);
                    mappingUsed = buildSlugSelections(headerMap);
                    // Debug: Log the converted mapping
                    System.out.println("Converted mappingUsed: " + mappingUsed);
                    // Debug: Log a specific field to verify
                    System.out.println("field_1 value: " + mappingUsed.get("field_1"));
                    model.addAttribute("templateSelected", template.getName());
                    model.addAttribute("selectedTemplateId", templateId);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    model.addAttribute("templateError", "Unable to load template: " + ex.getMessage());
                }
            } else {
                model.addAttribute("templateError", "Template not found.");
            }
        }

        model.addAttribute("targetFields", targetFields);
        model.addAttribute("columnOptions", columnOptions(targetFields.size()));
        model.addAttribute("mappingUsed", mappingUsed);
        model.addAttribute("mappingTemplates", csvService.getMappingTemplates());
        return "csv-template";
    }

    @PostMapping("/save")
    public String saveTemplate(@RequestParam Map<String, String> params,
                               RedirectAttributes redirectAttributes,
                               HttpServletRequest request) {
        String templateName = params.getOrDefault("templateName", "").trim();
        if (templateName.isEmpty()) {
            redirectAttributes.addFlashAttribute("templateError", "Template name is required.");
            return "redirect:/csv/template";
        }
        Map<String, String> slugSelections = extractSlugSelections(params);
        Map<String, String> headerMapping = slugToHeaderMapping(slugSelections);
        try {
            csvService.saveMappingTemplate(templateName, headerMapping);
            String username = getCurrentUsername(request);
            auditLogService.logAction(username, "Template Created", "Template: " + templateName, request);
            redirectAttributes.addFlashAttribute("templateMessage", "Template '" + templateName + "' saved.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("templateError", "Unable to save template: " + ex.getMessage());
        }
        return "redirect:/csv/template";
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> downloadTemplate(@RequestParam Map<String, String> params) {
        String templateName = params.getOrDefault("templateName", "").trim();
        if (templateName.isEmpty()) templateName = "csv-template";

        Map<String, String> slugSelections = extractSlugSelections(params);
        Map<String, String> headerMapping = slugToHeaderMapping(slugSelections);
        try {
            csvService.saveMappingTemplate(templateName, headerMapping);
        } catch (Exception ex) {
            // still allow download even if save fails
        }

        try {
            byte[] bytes = buildTemplateWorkbook(headerMapping);
            String filename = sanitizeFilename(templateName) + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(bytes.length)
                    .body(bytes);
        } catch (RuntimeException ex) {
            return ResponseEntity.internalServerError()
                    .body(("Unable to download template: " + ex.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteTemplate(@PathVariable("id") @org.springframework.lang.NonNull Long id,
                                 RedirectAttributes redirectAttributes,
                                 HttpServletRequest request) {
        try {
            csvService.deleteTemplate(id);
            String username = getCurrentUsername(request);
            auditLogService.logAction(username, "Template Deleted", "Template ID: " + id, request);
            redirectAttributes.addFlashAttribute("templateMessage", "Template moved to recycle bin.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("templateError", "Unable to delete template: " + ex.getMessage());
        }
        return "redirect:/csv/template";
    }
    
    @GetMapping("/recycle-bin")
    public String showRecycleBin(Model model) {
        model.addAttribute("deletedTemplates", csvService.getDeletedTemplates());
        return "template-recycle-bin";
    }
    
    @PostMapping("/{id}/restore")
    public String restoreTemplate(@PathVariable("id") @org.springframework.lang.NonNull Long id,
                                  RedirectAttributes redirectAttributes,
                                  HttpServletRequest request) {
        try {
            csvService.restoreTemplate(id);
            String username = getCurrentUsername(request);
            auditLogService.logAction(username, "Template Restored", "Template ID: " + id, request);
            redirectAttributes.addFlashAttribute("templateMessage", "Template restored successfully.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("templateError", "Unable to restore template: " + ex.getMessage());
        }
        return "redirect:/csv/template/recycle-bin";
    }
    
    @PostMapping("/{id}/permanent-delete")
    public String permanentlyDeleteTemplate(@PathVariable("id") @org.springframework.lang.NonNull Long id,
                                           RedirectAttributes redirectAttributes,
                                           HttpServletRequest request) {
        try {
            csvService.permanentlyDeleteTemplate(id);
            String username = getCurrentUsername(request);
            auditLogService.logAction(username, "Template Permanently Deleted", "Template ID: " + id, request);
            redirectAttributes.addFlashAttribute("templateMessage", "Template permanently deleted.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("templateError", "Unable to delete template: " + ex.getMessage());
        }
        return "redirect:/csv/template/recycle-bin";
    }
    
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadExistingTemplate(@PathVariable("id") @org.springframework.lang.NonNull Long id) {
        Optional<MappingTemplate> templateOpt = csvService.getMappingTemplate(id);
        if (templateOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        MappingTemplate template = templateOpt.get();
        try {
            Map<String, String> headerMap = csvService.readTemplateMapping(template);
            byte[] bytes = buildTemplateWorkbook(headerMap);
            String filename = sanitizeFilename(template.getName()) + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(bytes.length)
                    .body(bytes);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                    .body(("Unable to download template: " + ex.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    @GetMapping("/{id}/edit")
    public String editTemplate(@PathVariable("id") @org.springframework.lang.NonNull Long id,
                               Model model) {
        System.out.println("=== editTemplate called with id: " + id + " ===");
        try {
            Optional<MappingTemplate> templateOpt = csvService.getMappingTemplate(id);
            if (templateOpt.isEmpty()) {
                model.addAttribute("error", "Template not found");
                return "redirect:/csv/template";
            }

            MappingTemplate template = templateOpt.get();
            
            // Always set these attributes first
            model.addAttribute("template", template);
            model.addAttribute("targetFields", CsvService.EXPECTED_HEADERS);
            model.addAttribute("columnOptions", columnOptions(CsvService.EXPECTED_HEADERS.size()));
        
        try {
            Map<String, String> headerMap = null;
            try {
                if (template.getMappingJson() != null && !template.getMappingJson().trim().isEmpty()) {
                    try {
                        headerMap = csvService.readTemplateMapping(template);
                        if (headerMap == null) {
                            headerMap = Collections.emptyMap();
                        }
                        // Debug: Print the headerMap to see what we're getting
                        System.out.println("=== Template Header Map ===");
                        System.out.println("Template JSON: " + template.getMappingJson());
                        System.out.println("HeaderMap size: " + headerMap.size());
                        System.out.println("ALL HeaderMap entries:");
                        for (Map.Entry<String, String> entry : headerMap.entrySet()) {
                            System.out.println("  KEY: '" + entry.getKey() + "' -> VALUE: '" + entry.getValue() + "'");
                            System.out.println("    Key length: " + entry.getKey().length() + ", Key bytes: " + java.util.Arrays.toString(entry.getKey().getBytes()));
                        }
                        System.out.println("Expected headers for comparison:");
                        for (int i = 0; i < Math.min(10, CsvService.EXPECTED_HEADERS.size()); i++) {
                            String exp = CsvService.EXPECTED_HEADERS.get(i);
                            System.out.println("  [" + i + "] '" + exp + "' (length: " + exp.length() + ")");
                        }
                    } catch (com.fasterxml.jackson.core.JsonProcessingException jsonEx) {
                        System.err.println("JSON parsing error in template mapping: " + jsonEx.getMessage());
                        jsonEx.printStackTrace();
                        headerMap = Collections.emptyMap();
                    } catch (java.io.IOException ioEx) {
                        System.err.println("IOException reading template mapping JSON: " + ioEx.getMessage());
                        ioEx.printStackTrace();
                        headerMap = Collections.emptyMap();
                    }
                } else {
                    headerMap = Collections.emptyMap();
                }
            } catch (Exception e) {
                System.err.println("Unexpected error reading template mapping JSON: " + e.getMessage());
                e.printStackTrace();
                headerMap = Collections.emptyMap();
            }
            
            // Process the headerMap - preserve all mappings, don't default to "1" unless empty/IGNORE
            Map<String, String> processedHeaderMap = new HashMap<>();
            if (headerMap != null && !headerMap.isEmpty()) {
                System.out.println("Processing headerMap with " + headerMap.size() + " entries");
                // Normalize keys (trim whitespace) to handle any encoding issues
                for (Map.Entry<String, String> entry : headerMap.entrySet()) {
                    String key = entry.getKey();
                    String normalizedKey = key != null ? key.trim() : "";
                    String value = entry.getValue();
                    System.out.println("  Processing: KEY='" + key + "' (normalized='" + normalizedKey + "') -> VALUE='" + value + "'");
                    if (value == null || value.trim().isEmpty() || value.equalsIgnoreCase("IGNORE")) {
                        processedHeaderMap.put(normalizedKey, "1");
                        System.out.println("    -> Defaulted to '1' (empty/IGNORE)");
                    } else {
                        processedHeaderMap.put(normalizedKey, value.trim());
                        System.out.println("    -> Kept as '" + value.trim() + "'");
                    }
                }
            } else {
                System.out.println("headerMap is null or empty, creating defaults");
            }
            
            // Only create default mappings if processedHeaderMap is completely empty
            // This means the template had no mappings at all
            if (processedHeaderMap.isEmpty()) {
                System.out.println("processedHeaderMap is empty, creating default mappings to '1'");
                List<String> expected = CsvService.EXPECTED_HEADERS;
                for (String header : expected) {
                    processedHeaderMap.put(header.trim(), "1");
                }
            } else {
                // Fill in any missing headers with "1" as default, but DON'T overwrite existing ones
                List<String> expected = CsvService.EXPECTED_HEADERS;
                for (String header : expected) {
                    String normalizedHeader = header.trim();
                    if (!processedHeaderMap.containsKey(normalizedHeader)) {
                        System.out.println("  Adding missing header '" + normalizedHeader + "' with default '1'");
                        processedHeaderMap.put(normalizedHeader, "1");
                    } else {
                        System.out.println("  Header '" + normalizedHeader + "' already exists with value '" + processedHeaderMap.get(normalizedHeader) + "'");
                    }
                }
            }
            
            System.out.println("=== About to call buildSlugSelections ===");
            System.out.println("processedHeaderMap size: " + processedHeaderMap.size());
            System.out.println("Sample processedHeaderMap entries:");
            int sampleCount = 0;
            for (Map.Entry<String, String> entry : processedHeaderMap.entrySet()) {
                if (sampleCount++ < 10) {
                    System.out.println("  '" + entry.getKey() + "' -> '" + entry.getValue() + "'");
                }
            }
            
            Map<String, String> mappingUsed = buildSlugSelections(processedHeaderMap);
            if (mappingUsed == null) {
                System.out.println("ERROR: buildSlugSelections returned null!");
                mappingUsed = new HashMap<>();
                List<String> expected = CsvService.EXPECTED_HEADERS;
                for (int i = 0; i < expected.size(); i++) {
                    mappingUsed.put("field_" + (i + 1), "1");
                }
            }
            // Debug: Print first few mappings to verify
            System.out.println("=== Final mappingUsed (being sent to template) ===");
            System.out.println("mappingUsed size: " + mappingUsed.size());
            System.out.println("mappingUsed type: " + (mappingUsed != null ? mappingUsed.getClass().getName() : "null"));
            for (int i = 1; i <= Math.min(10, mappingUsed.size()); i++) {
                String key = "field_" + i;
                String value = mappingUsed.get(key);
                String headerName = i <= CsvService.EXPECTED_HEADERS.size() ? CsvService.EXPECTED_HEADERS.get(i-1) : "N/A";
                System.out.println(key + " (" + headerName + ") = '" + value + "' (type: " + (value != null ? value.getClass().getName() : "null") + ")");
            }
            // Ensure all values are strings, not integers
            Map<String, String> stringMap = new HashMap<>();
            for (Map.Entry<String, String> entry : mappingUsed.entrySet()) {
                String val = entry.getValue();
                stringMap.put(entry.getKey(), val != null ? String.valueOf(val) : "1");
            }
            System.out.println("Converted to string map, sample: field_1 = '" + stringMap.get("field_1") + "'");
            model.addAttribute("mappingUsed", stringMap);
            
            // Build a map showing which columns are mapped to which fields
            Map<Integer, List<String>> columnToFields = new HashMap<>();
            List<String> expected = CsvService.EXPECTED_HEADERS;
            for (int i = 0; i < expected.size(); i++) {
                String slug = "field_" + (i + 1);
                String header = expected.get(i);
                String columnValue = mappingUsed != null ? mappingUsed.getOrDefault(slug, "1") : "1";
                if (columnValue != null && !columnValue.isEmpty() && !columnValue.equals("IGNORE")) {
                    try {
                        int columnNum = Integer.parseInt(columnValue);
                        columnToFields.computeIfAbsent(columnNum, k -> new ArrayList<>()).add(header);
                    } catch (NumberFormatException ignored) {}
                }
            }
            model.addAttribute("columnToFields", columnToFields);
            
        } catch (Exception ex) {
            ex.printStackTrace();
            System.err.println("Error in editTemplate: " + ex.getMessage());
            ex.printStackTrace();
            model.addAttribute("error", "Unable to load template: " + ex.getMessage());
            // Still provide default values so the page can render
            Map<String, String> defaultMapping = new HashMap<>();
            List<String> expected = CsvService.EXPECTED_HEADERS;
            for (int i = 0; i < expected.size(); i++) {
                defaultMapping.put("field_" + (i + 1), "1");
            }
            model.addAttribute("mappingUsed", defaultMapping);
            model.addAttribute("columnToFields", new HashMap<Integer, List<String>>());
            }
        } catch (Exception outerEx) {
            // Catch any unexpected errors and still try to render the page
            outerEx.printStackTrace();
            System.err.println("Unexpected error in editTemplate: " + outerEx.getMessage());
            model.addAttribute("error", "An error occurred: " + outerEx.getMessage());
            // Ensure we have at least basic attributes
            if (!model.containsAttribute("template")) {
                return "redirect:/csv/template";
            }
            if (!model.containsAttribute("targetFields")) {
                model.addAttribute("targetFields", CsvService.EXPECTED_HEADERS);
            }
            if (!model.containsAttribute("columnOptions")) {
                model.addAttribute("columnOptions", columnOptions(CsvService.EXPECTED_HEADERS.size()));
            }
            if (!model.containsAttribute("mappingUsed")) {
                Map<String, String> defaultMapping = new HashMap<>();
                List<String> expected = CsvService.EXPECTED_HEADERS;
                for (int i = 0; i < expected.size(); i++) {
                    defaultMapping.put("field_" + (i + 1), "1");
                }
                model.addAttribute("mappingUsed", defaultMapping);
            }
            if (!model.containsAttribute("columnToFields")) {
                model.addAttribute("columnToFields", new HashMap<Integer, List<String>>());
            }
        }
        
        return "template-edit";
    }

    @PostMapping("/{id}/update")
    public String updateTemplate(@PathVariable("id") @org.springframework.lang.NonNull Long id,
                                 @RequestParam Map<String, String> params,
                                 RedirectAttributes redirectAttributes,
                                 HttpServletRequest request) {
        Optional<MappingTemplate> templateOpt = csvService.getMappingTemplate(id);
        if (templateOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Template not found");
            return "redirect:/csv/template";
        }

        try {
            Map<String, String> slugSelections = extractSlugSelections(params);
            Map<String, String> headerMapping = slugToHeaderMapping(slugSelections);
            
            // Update the template
            MappingTemplate template = templateOpt.get();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            template.setMappingJson(mapper.writeValueAsString(headerMapping));
            csvService.updateMappingTemplate(template);
            
            String username = getCurrentUsername(request);
            auditLogService.logAction(username, "Template Updated", "Template: " + template.getName(), request);
            redirectAttributes.addFlashAttribute("success", "Template updated successfully.");
            return "redirect:/csv/template/" + id + "/edit";
        } catch (Exception ex) {
            ex.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Unable to update template: " + ex.getMessage());
            return "redirect:/csv/template/" + id + "/edit";
        }
    }

    private Map<String, String> defaultSlugSelections() {
        // Return empty map so AUTO is selected by default instead of Column 1
        Map<String, String> defaults = new HashMap<>();
        for (int i = 0; i < CsvService.EXPECTED_HEADERS.size(); i++) {
            defaults.put("field_" + (i + 1), ""); // Empty string = AUTO
        }
        return defaults;
    }

    private List<Integer> columnOptions(int size) {
        // Always return 1-25 for consistency
        List<Integer> opts = new ArrayList<>();
        for (int i = 1; i <= 25; i++) opts.add(i);
        return opts;
    }

    private Map<String, String> extractSlugSelections(Map<String, String> params) {
        Map<String, String> csvToTarget = new HashMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            String key = e.getKey();
            if (key.startsWith("mapping[") && key.endsWith("]")) {
                String slug = key.substring(8, key.length() - 1);
                String value = e.getValue();
                // Store the value (could be empty for AUTO, or a column number)
                csvToTarget.put(slug, value != null ? value : "");
            }
        }
        if (csvToTarget.isEmpty()) csvToTarget = defaultSlugSelections();
        // Debug: Log what we extracted
        System.out.println("Extracted slug selections: " + csvToTarget);
        return csvToTarget;
    }

    private Map<String, String> slugToHeaderMapping(Map<String, String> slugSelections) {
        Map<String, String> headerMap = new HashMap<>();
        List<String> expected = CsvService.EXPECTED_HEADERS;
        for (int i = 0; i < expected.size(); i++) {
            String slug = "field_" + (i + 1);
            String header = expected.get(i);
            // Get the column number from slug selection
            String columnValue = slugSelections.getOrDefault(slug, "");
            // If empty or null, use "IGNORE" to skip this field
            if (columnValue == null || columnValue.trim().isEmpty()) {
                columnValue = "IGNORE";
            }
            headerMap.put(header, columnValue);
        }
        return headerMap;
    }

    private Map<String, String> buildSlugSelections(Map<String, String> headerToColumn) {
        Map<String, String> selections = new HashMap<>();
        List<String> expected = CsvService.EXPECTED_HEADERS;
        System.out.println("=== buildSlugSelections ===");
        System.out.println("headerToColumn size: " + (headerToColumn != null ? headerToColumn.size() : 0));
        if (headerToColumn != null && !headerToColumn.isEmpty()) {
            System.out.println("ALL headerToColumn entries:");
            for (Map.Entry<String, String> entry : headerToColumn.entrySet()) {
                System.out.println("  '" + entry.getKey() + "' -> '" + entry.getValue() + "'");
            }
        }
        System.out.println("Expected headers (first 10):");
        for (int i = 0; i < Math.min(10, expected.size()); i++) {
            System.out.println("  [" + i + "] '" + expected.get(i) + "'");
        }
        for (int i = 0; i < expected.size(); i++) {
            String slug = "field_" + (i + 1);
            String header = expected.get(i);
            String value = "1"; // Default to "1" instead of empty for edit page
            boolean found = false;
            
            // Normalize header for matching
            String normalizedHeader = header != null ? header.trim() : "";
            
            // Try exact match first
            if (headerToColumn != null && headerToColumn.containsKey(normalizedHeader)) {
                String mapValue = headerToColumn.get(normalizedHeader);
                found = true;
                System.out.println("  [EXACT MATCH] " + slug + " ('" + normalizedHeader + "') found in map -> '" + mapValue + "'");
                // Ensure value is not null or empty, default to "1"
                if (mapValue != null && !mapValue.trim().isEmpty() && !mapValue.equalsIgnoreCase("IGNORE")) {
                    value = mapValue.trim();
                    // Ensure the value is a valid integer string (1-25)
                    try {
                        int colNum = Integer.parseInt(value);
                        if (colNum < 1 || colNum > 25) {
                            value = "1"; // Default to 1 if out of range
                            System.out.println("    -> Out of range, defaulted to '1'");
                        } else {
                            value = String.valueOf(colNum); // Normalize to string
                            System.out.println("    -> Using value '" + value + "'");
                        }
                    } catch (NumberFormatException e) {
                        value = "1"; // Default to 1 if not a number
                        System.out.println("    -> Not a number, defaulted to '1'");
                    }
                } else {
                    System.out.println("    -> Value is empty/IGNORE, defaulted to '1'");
                }
            } else {
                // Try case-insensitive match if exact match failed
                if (headerToColumn != null) {
                    for (Map.Entry<String, String> entry : headerToColumn.entrySet()) {
                        String entryKey = entry.getKey() != null ? entry.getKey().trim() : "";
                        if (entryKey.equalsIgnoreCase(normalizedHeader)) {
                            found = true;
                            String mapValue = entry.getValue();
                            System.out.println("  [CASE-INSENSITIVE MATCH] " + slug + " ('" + normalizedHeader + "') matched '" + entryKey + "' -> '" + mapValue + "'");
                            if (mapValue != null && !mapValue.trim().isEmpty() && !mapValue.equalsIgnoreCase("IGNORE")) {
                                value = mapValue.trim();
                                try {
                                    int colNum = Integer.parseInt(value);
                                    if (colNum >= 1 && colNum <= 25) {
                                        value = String.valueOf(colNum);
                                    } else {
                                        value = "1";
                                    }
                                } catch (NumberFormatException e) {
                                    value = "1";
                                }
                            }
                            break;
                        }
                    }
                }
                if (!found) {
                    System.out.println("  [NOT FOUND] " + slug + " ('" + header + "') not in map, using default '1'");
                }
            }
            selections.put(slug, value);
            if (i < 10) { // Debug first 10
                System.out.println("  FINAL: " + slug + " -> '" + value + "'");
            }
        }
        System.out.println("Total selections: " + selections.size());
        return selections;
    }

    private byte[] buildTemplateWorkbook(Map<String, String> headerMapping) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Template");
            Row headerRow = sheet.createRow(0);

            int maxIdx = 0;
            for (String val : headerMapping.values()) {
                if (val == null || val.isBlank()) continue;
                try {
                    int idx = Integer.parseInt(val);
                    if (idx > maxIdx) maxIdx = idx;
                } catch (NumberFormatException ignored) {}
            }
            if (maxIdx == 0) maxIdx = CsvService.EXPECTED_HEADERS.size();

            for (Map.Entry<String, String> entry : headerMapping.entrySet()) {
                String header = entry.getKey();
                String val = entry.getValue();
                int idx;
                try {
                    idx = Integer.parseInt(val);
                } catch (NumberFormatException ex) {
                    idx = CsvService.EXPECTED_HEADERS.indexOf(header) + 1;
                }
                int columnIndex = Math.max(1, idx) - 1;
                Cell cell = headerRow.createCell(columnIndex);
                cell.setCellValue(header);
            }

            for (int i = 0; i < maxIdx; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Unable to build template: " + ex.getMessage(), ex);
        }
    }

    private String sanitizeFilename(String name) {
        String base = name.trim();
        if (base.isEmpty()) base = "csv-template";
        return base.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
    }

    private String getCurrentUsername(HttpServletRequest request) {
        // Try to get username from session or request parameters
        // For now, return a default - you can enhance this with proper session management
        return request.getParameter("username") != null ? 
               request.getParameter("username") : "System";
    }
}

