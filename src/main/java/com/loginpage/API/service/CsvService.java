package com.loginpage.API.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loginpage.API.model.CsvFile;
import com.loginpage.API.model.CsvRecord;
import com.loginpage.API.model.CsvRecordEntity;
import com.loginpage.API.model.RecordEditHistory;
import com.loginpage.API.repository.CsvFileRepository;
import com.loginpage.API.repository.CsvRecordRepository;
import com.loginpage.API.repository.MappingTemplateRepository;
import com.loginpage.API.repository.RecordEditHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

import com.loginpage.API.model.MappingTemplate;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.lang.NonNull;

@Service
public class CsvService {

    @Autowired
    private CsvFileRepository csvFileRepository;

    @Autowired
    private CsvRecordRepository csvRecordRepository;

    @Autowired
    private MappingTemplateRepository mappingTemplateRepository;

    @Autowired
    private RecordEditHistoryRepository recordEditHistoryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final CSVFormat CSV_WITH_HEADER = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .build();

    private static final CSVFormat CSV_DATA = CSVFormat.DEFAULT.builder()
            .setTrim(true)
            .build();

    public static final List<String> EXPECTED_HEADERS = Arrays.asList(
            "Student ID", "First Name", "Middle Name", "Last Name", "Gender",
            "Date of Birth", "Age", "Nationality", "Religion", "Civil Status",
            "Contact Number", "Email Address", "Home Address", "City/Municipality",
            "Province/State", "Zip Code", "Guardian’s Name", "Guardian’s Contact Number",
            "Father’s Name", "Father’s Occupation", "Mother’s Name", "Mother’s Occupation",
            "Course / Program", "Year Level", "Section"
    );

    public static class CsvResult {
        private final List<CsvRecord> successRecords;
        private final List<CsvRecord> failedRecords;

        public CsvResult(List<CsvRecord> successRecords, List<CsvRecord> failedRecords) {
            this.successRecords = successRecords;
            this.failedRecords = failedRecords;
        }

        public List<CsvRecord> getSuccessRecords() { return successRecords; }
        public List<CsvRecord> getFailedRecords() { return failedRecords; }
    }

    public CsvResult parseCsv(MultipartFile file) throws IOException {
        List<CsvRecord> successRecords = new ArrayList<>();
        List<CsvRecord> failedRecords = new ArrayList<>();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSV_WITH_HEADER.parse(reader)) {

            Map<String, Integer> headerIndexMap = parser.getHeaderMap();

            for (CSVRecord rec : parser) {
                if (rec.size() == 0) continue;
                CsvRecord record = new CsvRecord();

                for (String expected : EXPECTED_HEADERS) {
                    String keyLower = expected.trim().toLowerCase();
                    if (headerIndexMap.containsKey(keyLower)) {
                        int index = headerIndexMap.get(keyLower);
                        String v = index < rec.size() ? rec.get(index).trim() : "";
                        record.setField(expected, v);
                    } else {
                        record.setField(expected, "");
                    }
                }

                if (isValid(record)) {
                    successRecords.add(record);
                } else {
                    failedRecords.add(record);
                }
            }
        }

        return new CsvResult(successRecords, failedRecords);
    }

    /**
     * Parse CSV content from a saved CsvFile, persist each row as CsvRecordEntity
     * and return the parsed result for display. This tolerates columns in any order
     * because we map by header name.
     */
    @Transactional
    public CsvResult parseAndPersist(CsvFile csvFile) throws IOException {
        List<CsvRecord> successRecords = new ArrayList<>();
        List<CsvRecord> failedRecords = new ArrayList<>();

        String content = csvFile.getContent();
        if (content == null) throw new IOException("CSV content is empty");

        try (Reader reader = new StringReader(content);
             CSVParser parser = CSV_WITH_HEADER.parse(reader)) {

            Map<String, Integer> headerIndexMap = parser.getHeaderMap();

            int row = 0;
            for (CSVRecord rec : parser) {
                row++;
                if (rec.size() == 0) continue;
                CsvRecord record = new CsvRecord();

                for (String expected : EXPECTED_HEADERS) {
                    String keyLower = expected.trim().toLowerCase();
                    if (headerIndexMap.containsKey(keyLower)) {
                        int index = headerIndexMap.get(keyLower);
                        String v = index < rec.size() ? rec.get(index).trim() : "";
                        record.setField(expected, v);
                    } else {
                        record.setField(expected, "");
                    }
                }

                boolean ok = isValid(record);
                if (ok) successRecords.add(record); else failedRecords.add(record);

                // Persist the row as JSON so schema is dynamic and ordering doesn't matter
                String json = objectMapper.writeValueAsString(record.getFields());
                CsvRecordEntity entity = new CsvRecordEntity(csvFile, row, ok, json);
                csvRecordRepository.save(entity);
            }
        }

        return new CsvResult(successRecords, failedRecords);
    }

    /**
     * Parse CSV using an explicit mapping provided by the user.
     * finalMapping: map of expected header -> either "IGNORE" or a 1-based column index string
     */
    @Transactional
    public CsvResult parseAndPersistWithMapping(CsvFile csvFile, Map<String, String> finalMapping) throws IOException {
        List<CsvRecord> successRecords = new ArrayList<>();
        List<CsvRecord> failedRecords = new ArrayList<>();

        String content = csvFile.getContent();
        if (content == null) throw new IOException("CSV content is empty");

        try (Reader reader = new StringReader(content);
             CSVParser parser = CSV_DATA.parse(reader)) {

            int row = 0;
            for (CSVRecord rec : parser) {
                row++;
                if (rec.size() == 0) continue;
                if (isDefaultHeaderRow(rec)) continue;
                CsvRecord record = new CsvRecord();

                boolean explicitEmptyFound = false;
                // For each expected header, if mapping has a numeric index, use it
                for (String expected : EXPECTED_HEADERS) {
                    String mapVal = finalMapping.getOrDefault(expected, "IGNORE");
                    if (mapVal != null && !mapVal.equalsIgnoreCase("IGNORE")) {
                        try {
                            int idx = Integer.parseInt(mapVal) - 1;
                            String v = idx >= 0 && idx < rec.size() ? rec.get(idx).trim() : "";
                            record.setField(expected, v);
                            if (v.isEmpty()) explicitEmptyFound = true;
                        } catch (NumberFormatException nfe) {
                            record.setField(expected, "");
                        }
                    } else {
                        record.setField(expected, "");
                    }
                }

                boolean ok = !explicitEmptyFound && isValid(record);
                if (ok) successRecords.add(record); else failedRecords.add(record);

                String json = objectMapper.writeValueAsString(record.getFields());
                CsvRecordEntity entity = new CsvRecordEntity(csvFile, row, ok, json);
                csvRecordRepository.save(entity);
            }
        }

        return new CsvResult(successRecords, failedRecords);
    }

    /**
     * Read and return the first-row headers from a saved CsvFile.
     */
    public List<String> extractHeaders(CsvFile csvFile) throws IOException {
        String content = csvFile.getContent();
        if (content == null) return Collections.emptyList();
        try (Reader r = new StringReader(content);
             CSVParser parser = CSV_WITH_HEADER.parse(r)) {
            return new ArrayList<>(parser.getHeaderNames());
        }
    }

    /**
     * Return the first data record's values in order as a list. If no data row exists, returns empty list.
     */
    public List<String> getFirstRecordValues(CsvFile csvFile) throws IOException {
        String content = csvFile.getContent();
        if (content == null) return Collections.emptyList();
        try (Reader r = new StringReader(content);
             CSVParser parser = CSV_DATA.parse(r)) {
            for (CSVRecord rec : parser) {
                if (rec.size() == 0) continue;
                if (isDefaultHeaderRow(rec)) continue;
                List<String> vals = new ArrayList<>();
                for (int i = 0; i < rec.size(); i++) vals.add(rec.get(i));
                return vals;
            }
        }
        return Collections.emptyList();
    }

    /**
     * Return all data rows as list-of-lists ordered by header names (header order preserved).
     */
    public List<List<String>> getAllRows(CsvFile csvFile) throws IOException {
        String content = csvFile.getContent();
        if (content == null) return Collections.emptyList();
        List<List<String>> rows = new ArrayList<>();
        try (Reader r = new StringReader(content);
             CSVParser parser = CSV_DATA.parse(r)) {
            for (CSVRecord rec : parser) {
                if (rec.size() == 0) continue;
                if (isDefaultHeaderRow(rec)) continue;
                List<String> vals = new ArrayList<>();
                for (int i = 0; i < rec.size(); i++) {
                    vals.add(rec.get(i));
                }
                rows.add(vals);
            }
        }
        return rows;
    }

    /**
     * Compute an automatic mapping from EXPECTED_HEADERS -> 1-based CSV column index string
     * or "IGNORE" when no good match is found. Uses exact/case-insensitive match, contains
     * heuristics and a simple Levenshtein-based similarity score. Prevents assigning the same
     * source column to multiple targets when possible.
     */
    public Map<String, String> computeAutoMapping(CsvFile csvFile) throws IOException {
        List<String> originals = extractHeaders(csvFile);
        Map<String, String> mapping = new LinkedHashMap<>();
        if (originals == null || originals.isEmpty()) {
            // nothing to map
            for (String expected : EXPECTED_HEADERS) mapping.put(expected, "IGNORE");
            return mapping;
        }

        // prepare normalized originals
        List<String> normOrig = new ArrayList<>();
        for (String h : originals) normOrig.add(normalize(h));

        Set<Integer> used = new HashSet<>();

        for (String expected : EXPECTED_HEADERS) {
            String normExp = normalize(expected);
            double bestScore = -1.0;
            int bestIdx = -1;

            for (int i = 0; i < originals.size(); i++) {
                if (used.contains(i)) continue; // prefer unique assignments
                String o = originals.get(i);
                String no = normOrig.get(i);

                // exact case-insensitive
                if (o.equalsIgnoreCase(expected)) {
                    bestScore = 1.0; bestIdx = i; break;
                }

                // contains heuristics
                if (no.contains(normExp) || normExp.contains(no)) {
                    double s = 0.9;
                    if (s > bestScore) { bestScore = s; bestIdx = i; }
                    continue;
                }

                // fuzzy similarity via Levenshtein
                int ld = levenshtein(normExp, no);
                int max = Math.max(normExp.length(), no.length());
                double sim = max == 0 ? 1.0 : 1.0 - ((double) ld / (double) max);
                if (sim > bestScore) { bestScore = sim; bestIdx = i; }
            }

            // use threshold to accept mapping; threshold tuned to 0.5
            if (bestIdx >= 0 && bestScore >= 0.5) {
                mapping.put(expected, String.valueOf(bestIdx + 1));
                used.add(bestIdx);
            } else {
                mapping.put(expected, "IGNORE");
            }
        }

        return mapping;
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private boolean isDefaultHeaderRow(CSVRecord rec) {
        if (rec == null || rec.size() == 0) return false;
        
        // Create a set of normalized expected headers for quick lookup
        Set<String> normalizedHeaders = new HashSet<>();
        for (String header : EXPECTED_HEADERS) {
            normalizedHeaders.add(normalize(header));
        }
        
        // Check if all non-empty cells in the row match expected headers
        int nonEmptyCount = 0;
        int matchingCount = 0;
        
        for (int i = 0; i < rec.size(); i++) {
            String cell = rec.get(i);
            if (cell != null && !cell.trim().isEmpty()) {
                nonEmptyCount++;
                String normalizedCell = normalize(cell);
                if (normalizedHeaders.contains(normalizedCell)) {
                    matchingCount++;
                }
            }
        }
        
        // If all non-empty cells match expected headers, consider it a header row
        // Also check if the row has at least 5 matching headers (to avoid false positives)
        return nonEmptyCount > 0 && matchingCount >= Math.min(5, nonEmptyCount) && 
               matchingCount == nonEmptyCount;
    }

    // Levenshtein distance implementation (small strings so performance is fine)
    private int levenshtein(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        return prev[b.length()];
    }

    /**
     * Persist uploaded CSV file content into database as a historical record.
     */
    @Transactional
    public CsvFile saveFileToDb(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() == null ? "unknown.csv" : file.getOriginalFilename();
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        CsvFile csvFile = new CsvFile(filename, content, LocalDateTime.now());

        // try to capture header names for later use (stored as JSON)
        try (Reader r = new StringReader(content);
             CSVParser parser = CSV_WITH_HEADER.parse(r)) {
            List<String> headers = new ArrayList<>(parser.getHeaderNames());
            csvFile.setHeadersJson(objectMapper.writeValueAsString(headers));
        } catch (Exception ex) {
            // ignore extraction errors; file will still be saved
        }

        return csvFileRepository.save(csvFile);
    }

    @Transactional
    public void saveMappingForFile(CsvFile csvFile, Map<String, String> finalMapping) {
        try {
            csvFile.setMappingJson(objectMapper.writeValueAsString(finalMapping));
            csvFileRepository.save(csvFile);
        } catch (Exception ex) {
            // ignore mapping save failures (non-fatal)
        }
    }

    public MappingTemplate saveMappingTemplate(String name, Map<String, String> mapping) throws IOException {
        // Check if name already exists (not deleted)
        if (mappingTemplateRepository.existsByNameIgnoreCaseAndDeletedFalse(name)) {
            throw new IllegalArgumentException("Template name already exists: " + name);
        }
        MappingTemplate template = new MappingTemplate();
        template.setName(name);
        template.setMappingJson(objectMapper.writeValueAsString(mapping));
        template.setDeleted(false);
        return mappingTemplateRepository.save(template);
    }
    
    @Transactional
    public void deleteTemplate(@org.springframework.lang.NonNull Long id) {
        Optional<MappingTemplate> templateOpt = mappingTemplateRepository.findById(id);
        if (templateOpt.isPresent()) {
            MappingTemplate template = templateOpt.get();
            template.setDeleted(true);
            template.setDeletedAt(LocalDateTime.now());
            mappingTemplateRepository.save(template);
        }
    }
    
    @Transactional
    public void restoreTemplate(@org.springframework.lang.NonNull Long id) {
        Optional<MappingTemplate> templateOpt = mappingTemplateRepository.findById(id);
        if (templateOpt.isPresent()) {
            MappingTemplate template = templateOpt.get();
            template.setDeleted(false);
            template.setDeletedAt(null);
            mappingTemplateRepository.save(template);
        }
    }
    
    @Transactional
    public void permanentlyDeleteTemplate(@org.springframework.lang.NonNull Long id) {
        mappingTemplateRepository.deleteById(id);
    }

    public List<MappingTemplate> getMappingTemplates() {
        return mappingTemplateRepository.findByDeletedFalseOrderByCreatedAtDesc();
    }
    
    public List<MappingTemplate> getDeletedTemplates() {
        return mappingTemplateRepository.findByDeletedTrueOrderByDeletedAtDesc();
    }

    public Optional<MappingTemplate> getMappingTemplate(Long id) {
        if (id == null) return Optional.empty();
        return mappingTemplateRepository.findById(id);
    }

    public Map<String, String> readTemplateMapping(MappingTemplate template) throws IOException {
        if (template == null || template.getMappingJson() == null) return Collections.emptyMap();
        return objectMapper.readValue(template.getMappingJson(), new TypeReference<Map<String, String>>() {});
    }

    /**
     * Update an existing mapping template
     */
    @Transactional
    public MappingTemplate updateMappingTemplate(@NonNull MappingTemplate template) {
        if (template.getId() == null) {
            throw new IllegalArgumentException("Template ID is required for update");
        }
        return mappingTemplateRepository.save(template);
    }

    private boolean isValid(CsvRecord record) {
        return getValidationErrors(record).isEmpty();
    }

    /**
     * Get validation errors for a record
     * @return List of error messages describing what's invalid
     */
    /**
     * Get validation errors for a record
     * A record is marked as SUCCESS only if ALL validations pass.
     * If ANY validation fails, the record is marked as FAILED.
     * 
     * @param record The CSV record to validate
     * @return List of error messages describing what's invalid
     */
    public List<String> getValidationErrors(CsvRecord record) {
        List<String> errors = new ArrayList<>();
        
        // ===== REQUIRED FIELDS =====
        
        // 1. Student ID - Required, must not be blank
        if (isBlank(record.getField("Student ID"))) {
            errors.add("Student ID is required");
        }
        
        // 2. First Name - Required, must not be blank, must not be numeric
        String firstName = record.getField("First Name");
        if (isBlank(firstName)) {
            errors.add("First Name is required");
        } else if (isNumeric(firstName)) {
            errors.add("First Name must not be a number");
        }
        
        // 3. Last Name - Required, must not be blank, must not be numeric
        String lastName = record.getField("Last Name");
        if (isBlank(lastName)) {
            errors.add("Last Name is required");
        } else if (isNumeric(lastName)) {
            errors.add("Last Name must not be a number");
        }
        
        // 4. Middle Name - Optional, but if provided, must not be numeric
        String middleName = record.getField("Middle Name");
        if (!isBlank(middleName) && isNumeric(middleName)) {
            errors.add("Middle Name must not be a number");
        }
        
        // 5. Gender - Required, must not be blank
        String gender = record.getField("Gender");
        if (isBlank(gender)) {
            errors.add("Gender is required");
        } else {
            // Validate gender value (should be Male, Female, M, F, etc.)
            String genderLower = gender.trim().toLowerCase();
            if (!genderLower.matches("^(male|female|m|f|other|prefer not to say)$")) {
                // Allow common variations but warn if unusual
                if (genderLower.length() > 20) {
                    errors.add("Gender value appears invalid (too long)");
                }
            }
        }
        
        // 6. Date of Birth - Required, must not be blank
        String dateOfBirth = record.getField("Date of Birth");
        if (isBlank(dateOfBirth)) {
            errors.add("Date of Birth is required");
        } else {
            // Basic date format validation (should contain numbers and separators)
            if (!dateOfBirth.matches(".*\\d.*")) {
                errors.add("Date of Birth must contain numbers");
            }
        }
        
        // 7. Age - Required, must not be blank, must be numeric, must be reasonable (1-150)
        String age = record.getField("Age");
        if (isBlank(age)) {
            errors.add("Age is required");
        } else if (!isNumeric(age)) {
            errors.add("Age must be a number");
        } else {
            try {
                int ageValue = Integer.parseInt(age.trim());
                if (ageValue < 1 || ageValue > 150) {
                    errors.add("Age must be between 1 and 150");
                }
            } catch (NumberFormatException e) {
                errors.add("Age must be a valid number");
            }
        }
        
        // 8. Email Address - Required, must not be blank, must contain '@', basic format check
        String email = record.getField("Email Address");
        if (isBlank(email)) {
            errors.add("Email Address is required");
        } else {
            email = email.trim();
            if (!email.contains("@")) {
                errors.add("Email Address must contain '@' symbol");
            } else {
                // Basic email format validation
                String[] emailParts = email.split("@");
                if (emailParts.length != 2) {
                    errors.add("Email Address format is invalid");
                } else if (emailParts[0].trim().isEmpty()) {
                    errors.add("Email Address must have text before '@'");
                } else if (!emailParts[1].contains(".")) {
                    errors.add("Email Address must contain a domain (e.g., example.com)");
                } else if (emailParts[1].trim().isEmpty()) {
                    errors.add("Email Address must have text after '@'");
                }
            }
        }
        
        // 9. Contact Number - Required, must not be blank, must be numeric, must be at least 7 digits
        String contact = record.getField("Contact Number");
        if (isBlank(contact)) {
            errors.add("Contact Number is required");
        } else if (!isNumeric(contact)) {
            errors.add("Contact Number must be numeric");
        } else {
            contact = contact.trim();
            if (contact.length() < 7) {
                errors.add("Contact Number must be at least 7 digits");
            } else if (contact.length() > 15) {
                errors.add("Contact Number must not exceed 15 digits");
            }
        }
        
        // ===== OPTIONAL FIELDS WITH VALIDATION =====
        
        // 10. Nationality - Optional, but if provided, must not be numeric
        String nationality = record.getField("Nationality");
        if (!isBlank(nationality) && isNumeric(nationality)) {
            errors.add("Nationality must not be a number");
        }
        
        // 11. Religion - Optional, but if provided, must not be numeric
        String religion = record.getField("Religion");
        if (!isBlank(religion) && isNumeric(religion)) {
            errors.add("Religion must not be a number");
        }
        
        // 12. Civil Status - Optional, but if provided, validate common values
        String civilStatus = record.getField("Civil Status");
        if (!isBlank(civilStatus)) {
            String csLower = civilStatus.trim().toLowerCase();
            if (isNumeric(civilStatus)) {
                errors.add("Civil Status must not be a number");
            } else if (csLower.length() > 30) {
                errors.add("Civil Status value appears invalid (too long)");
            }
        }
        
        // 13. Home Address - Optional, but if provided, should not be purely numeric
        String homeAddress = record.getField("Home Address");
        if (!isBlank(homeAddress) && isNumeric(homeAddress.trim())) {
            errors.add("Home Address must not be purely numeric");
        }
        
        // 14. City/Municipality - Optional, but if provided, must not be numeric
        String city = record.getField("City/Municipality");
        if (!isBlank(city) && isNumeric(city)) {
            errors.add("City/Municipality must not be a number");
        }
        
        // 15. Province/State - Optional, but if provided, must not be numeric
        String province = record.getField("Province/State");
        if (!isBlank(province) && isNumeric(province)) {
            errors.add("Province/State must not be a number");
        }
        
        // 16. Zip Code - Optional, but if provided, should be numeric or alphanumeric
        String zipCode = record.getField("Zip Code");
        if (!isBlank(zipCode)) {
            zipCode = zipCode.trim();
            // Zip codes can be numeric or alphanumeric, but should have reasonable length
            if (zipCode.length() > 10) {
                errors.add("Zip Code must not exceed 10 characters");
            }
        }
        
        // 17. Guardian's Name - Optional, but if provided, must not be numeric
        String guardianName = record.getField("Guardian's Name");
        if (!isBlank(guardianName) && isNumeric(guardianName)) {
            errors.add("Guardian's Name must not be a number");
        }
        
        // 18. Guardian's Contact Number - Optional, but if provided, must be numeric and valid length
        String guardianContact = record.getField("Guardian's Contact Number");
        if (!isBlank(guardianContact)) {
            if (!isNumeric(guardianContact)) {
                errors.add("Guardian's Contact Number must be numeric");
            } else {
                guardianContact = guardianContact.trim();
                if (guardianContact.length() < 7) {
                    errors.add("Guardian's Contact Number must be at least 7 digits");
                } else if (guardianContact.length() > 15) {
                    errors.add("Guardian's Contact Number must not exceed 15 digits");
                }
            }
        }
        
        // 19. Father's Name - Optional, but if provided, must not be numeric
        String fatherName = record.getField("Father's Name");
        if (!isBlank(fatherName) && isNumeric(fatherName)) {
            errors.add("Father's Name must not be a number");
        }
        
        // 20. Father's Occupation - Optional, but if provided, must not be purely numeric
        String fatherOccupation = record.getField("Father's Occupation");
        if (!isBlank(fatherOccupation) && isNumeric(fatherOccupation.trim())) {
            errors.add("Father's Occupation must not be purely numeric");
        }
        
        // 21. Mother's Name - Optional, but if provided, must not be numeric
        String motherName = record.getField("Mother's Name");
        if (!isBlank(motherName) && isNumeric(motherName)) {
            errors.add("Mother's Name must not be a number");
        }
        
        // 22. Mother's Occupation - Optional, but if provided, must not be purely numeric
        String motherOccupation = record.getField("Mother's Occupation");
        if (!isBlank(motherOccupation) && isNumeric(motherOccupation.trim())) {
            errors.add("Mother's Occupation must not be purely numeric");
        }
        
        // 23. Course / Program - Optional, but if provided, must not be purely numeric
        String course = record.getField("Course / Program");
        if (!isBlank(course) && isNumeric(course.trim())) {
            errors.add("Course / Program must not be purely numeric");
        }
        
        // 24. Year Level - Optional, but if provided, should be numeric and reasonable (1-10)
        String yearLevel = record.getField("Year Level");
        if (!isBlank(yearLevel)) {
            if (!isNumeric(yearLevel)) {
                errors.add("Year Level must be numeric");
            } else {
                try {
                    int yearValue = Integer.parseInt(yearLevel.trim());
                    if (yearValue < 1 || yearValue > 10) {
                        errors.add("Year Level must be between 1 and 10");
                    }
                } catch (NumberFormatException e) {
                    errors.add("Year Level must be a valid number");
                }
            }
        }
        
        // 25. Section - Optional, no specific validation (can be alphanumeric)
        // No validation needed for Section as it can be any format
        
        return errors;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean isNumeric(String s) {
        return s.matches("\\d+");
    }

    /**
     * Update a CSV record with new data and save edit history
     */
    @Transactional
    public CsvRecordEntity updateRecord(@NonNull Long recordId, Map<String, String> newFields) throws Exception {
        Optional<CsvRecordEntity> opt = csvRecordRepository.findById(recordId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Record not found: " + recordId);
        }

        CsvRecordEntity entity = opt.get();
        String oldData = entity.getData();
        Map<String, String> oldFields = objectMapper.readValue(oldData, new TypeReference<Map<String, String>>() {});
        
        // Update the data
        String newData = objectMapper.writeValueAsString(newFields);
        entity.setData(newData);
        
        // Re-validate the record
        CsvRecord record = new CsvRecord();
        record.getFields().putAll(newFields);
        entity.setSuccess(isValid(record));
        
        csvRecordRepository.save(entity);
        
        // Track which fields changed
        Map<String, Map<String, String>> changedFields = new HashMap<>();
        for (Map.Entry<String, String> entry : newFields.entrySet()) {
            String key = entry.getKey();
            String newValue = entry.getValue();
            String oldValue = oldFields.getOrDefault(key, "");
            if (!Objects.equals(oldValue, newValue)) {
                Map<String, String> change = new HashMap<>();
                change.put("old", oldValue);
                change.put("new", newValue);
                changedFields.put(key, change);
            }
        }
        
        // Save edit history
        if (!changedFields.isEmpty()) {
            RecordEditHistory history = new RecordEditHistory(
                entity,
                entity.getCsvFile(),
                oldData,
                newData,
                objectMapper.writeValueAsString(changedFields)
            );
            recordEditHistoryRepository.save(history);
        }
        
        return entity;
    }

    /**
     * Get edit history for a CSV file
     */
    public List<RecordEditHistory> getEditHistory(Long csvFileId) {
        return recordEditHistoryRepository.findByCsvFileIdOrderByEditedAtDesc(csvFileId);
    }

    /**
     * Get edit history for a specific record
     */
    public List<RecordEditHistory> getRecordEditHistory(Long recordId) {
        return recordEditHistoryRepository.findByRecordIdOrderByEditedAtDesc(recordId);
    }

    /**
     * Get all record entities for a CSV file
     */
    public List<CsvRecordEntity> getRecordEntities(Long csvFileId) {
        return csvRecordRepository.findAllByCsvFileIdOrderByRowNumberAsc(csvFileId);
    }

    /**
     * Get records for a file and convert them back to CsvRecord objects
     * Separates them into success and failed lists
     */
    public CsvResult getRecordsForFile(Long csvFileId) throws IOException {
        List<CsvRecordEntity> entities = getRecordEntities(csvFileId);
        List<CsvRecord> successRecords = new ArrayList<>();
        List<CsvRecord> failedRecords = new ArrayList<>();

        for (CsvRecordEntity entity : entities) {
            try {
                Map<String, String> fields = objectMapper.readValue(entity.getData(), new TypeReference<Map<String, String>>() {});
                CsvRecord record = new CsvRecord();
                record.getFields().putAll(fields);

                if (entity.isSuccess()) {
                    successRecords.add(record);
                } else {
                    failedRecords.add(record);
                }
            } catch (Exception e) {
                // If we can't parse the JSON, create an empty record and add to failed
                CsvRecord record = new CsvRecord();
                failedRecords.add(record);
            }
        }

        return new CsvResult(successRecords, failedRecords);
    }

    /**
     * Get file statistics (count of success/failed records)
     */
    public Map<String, Integer> getFileStatistics(Long csvFileId) {
        Map<String, Integer> stats = new HashMap<>();
        List<CsvRecordEntity> entities = getRecordEntities(csvFileId);
        
        int successCount = 0;
        int failedCount = 0;
        
        for (CsvRecordEntity entity : entities) {
            if (entity.isSuccess()) {
                successCount++;
            } else {
                failedCount++;
            }
        }
        
        stats.put("successCount", successCount);
        stats.put("failedCount", failedCount);
        stats.put("totalCount", successCount + failedCount);
        
        return stats;
    }

    /**
     * Delete all records for a CSV file (used when re-processing)
     * First deletes edit history records to avoid foreign key constraint violations
     */
    @Transactional
    public void deleteRecordsForFile(Long csvFileId) {
        // First, delete all edit history records for this file to avoid foreign key constraint
        recordEditHistoryRepository.deleteByCsvFileId(csvFileId);
        
        // Then delete the CSV records themselves
        csvRecordRepository.deleteByCsvFileId(csvFileId);
    }
}
