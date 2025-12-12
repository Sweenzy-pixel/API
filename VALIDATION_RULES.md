# CSV Record Validation Rules - Success vs Failed

## Overview
A CSV record is marked as **SUCCESS** only if it passes ALL validation rules. If ANY validation fails, the record is marked as **FAILED** and moved to the failed table.

## Current Validation Logic

### Record Classification
- **SUCCESS**: Record passes ALL validation rules
- **FAILED**: Record fails ONE or MORE validation rules

---

## Complete List of Validation Rules

### 1. Student ID
- **Required**: ✅ YES
- **Validation**: Must not be blank/empty
- **Error Message**: "Student ID is required"
- **Status**: ❌ FAILED if empty

### 2. First Name
- **Required**: ✅ YES
- **Validations**:
  - Must not be blank/empty
  - Must not be purely numeric (e.g., "123" is invalid)
- **Error Messages**:
  - "First Name is required" (if empty)
  - "First Name must not be a number" (if numeric)
- **Status**: ❌ FAILED if empty or numeric

### 3. Last Name
- **Required**: ✅ YES
- **Validations**:
  - Must not be blank/empty
  - Must not be purely numeric
- **Error Messages**:
  - "Last Name is required" (if empty)
  - "Last Name must not be a number" (if numeric)
- **Status**: ❌ FAILED if empty or numeric

### 4. Middle Name
- **Required**: ❌ NO (Optional)
- **Validations**:
  - If provided, must not be purely numeric
- **Error Message**: "Middle Name must not be a number" (if numeric)
- **Status**: ❌ FAILED if numeric (only if provided)

### 5. Gender
- **Required**: ✅ YES
- **Validation**: Must not be blank/empty
- **Error Message**: "Gender is required"
- **Status**: ❌ FAILED if empty

### 6. Date of Birth
- **Required**: ✅ YES
- **Validation**: Must not be blank/empty
- **Error Message**: "Date of Birth is required"
- **Status**: ❌ FAILED if empty

### 7. Email Address
- **Required**: ✅ YES
- **Validations**:
  - Must not be blank/empty
  - Must contain '@' symbol
- **Error Messages**:
  - "Email Address is required" (if empty)
  - "Email Address must contain '@' symbol" (if no @)
- **Status**: ❌ FAILED if empty or missing '@'

### 8. Age
- **Required**: ✅ YES
- **Validations**:
  - Must not be blank/empty
  - Must be a valid number (numeric)
- **Error Messages**:
  - "Age is required" (if empty)
  - "Age must be a number" (if not numeric)
- **Status**: ❌ FAILED if empty or not numeric

### 9. Contact Number
- **Required**: ✅ YES
- **Validations**:
  - Must not be blank/empty
  - Must be numeric (digits only)
  - Must be at least 7 digits long
- **Error Messages**:
  - "Contact Number is required" (if empty)
  - "Contact Number must be numeric" (if contains non-digits)
  - "Contact Number must be at least 7 digits" (if less than 7 digits)
- **Status**: ❌ FAILED if empty, not numeric, or less than 7 digits

### 10. Nationality
- **Required**: ❌ NO (Optional)
- **Validations**:
  - If provided, must not be purely numeric
- **Error Message**: "Nationality must not be a number" (if numeric)
- **Status**: ❌ FAILED if numeric (only if provided)

### 11. Religion
- **Required**: ❌ NO (Optional)
- **Validations**:
  - If provided, must not be purely numeric
- **Error Message**: "Religion must not be a number" (if numeric)
- **Status**: ❌ FAILED if numeric (only if provided)

### 12. Civil Status
- **Required**: ❌ NO (Optional)
- **Validations**:
  - If provided, must not be purely numeric
  - Must not exceed 30 characters
- **Error Messages**:
  - "Civil Status must not be a number" (if numeric)
  - "Civil Status value appears invalid (too long)" (if > 30 chars)
- **Status**: ❌ FAILED if numeric or too long (only if provided)

### 13. Home Address
- **Required**: ❌ NO (Optional)
- **Validations**:
  - If provided, must not be purely numeric
- **Error Message**: "Home Address must not be purely numeric" (if numeric)
- **Status**: ❌ FAILED if purely numeric (only if provided)

### 14. City/Municipality
- **Required**: ❌ NO (Optional)
- **Validations**:
  - If provided, must not be purely numeric
- **Error Message**: "City/Municipality must not be a number" (if numeric)
- **Status**: ❌ FAILED if numeric (only if provided)

### 15. Province/State
- **Required**: ❌ NO (Optional)
- **Validations**:
  - If provided, must not be purely numeric
- **Error Message**: "Province/State must not be a number" (if numeric)
- **Status**: ❌ FAILED if numeric (only if provided)

### 16. Zip Code
- **Required**: ❌ NO (Optional)
- **Validations**:
  - If provided, must not exceed 10 characters
- **Error Message**: "Zip Code must not exceed 10 characters" (if > 10 chars)
- **Status**: ❌ FAILED if too long (only if provided)

### 17. Guardian's Name
- **Required**: ❌ NO (Optional)
- **Validations**:
  - If provided, must not be purely numeric
- **Error Message**: "Guardian's Name must not be a number" (if numeric)
- **Status**: ❌ FAILED if numeric (only if provided)

### 18. Guardian's Contact Number
- **Required**: ❌ NO (Optional)
- **Validations**:
  - If provided, must be numeric
  - Must be at least 7 digits
  - Must not exceed 15 digits
- **Error Messages**:
  - "Guardian's Contact Number must be numeric" (if not numeric)
  - "Guardian's Contact Number must be at least 7 digits" (if < 7 digits)
  - "Guardian's Contact Number must not exceed 15 digits" (if > 15 digits)
- **Status**: ❌ FAILED if not numeric, too short, or too long (only if provided)

### 19. Father's Name
- **Required**: ❌ NO (Optional)
- **Validations**:
  - If provided, must not be purely numeric
- **Error Message**: "Father's Name must not be a number" (if numeric)
- **Status**: ❌ FAILED if numeric (only if provided)

### 20. Father's Occupation
- **Required**: ❌ NO (Optional)
- **Validations**:
  - If provided, must not be purely numeric
- **Error Message**: "Father's Occupation must not be purely numeric" (if numeric)
- **Status**: ❌ FAILED if purely numeric (only if provided)

### 21. Mother's Name
- **Required**: ❌ NO (Optional)
- **Validations**:
  - If provided, must not be purely numeric
- **Error Message**: "Mother's Name must not be a number" (if numeric)
- **Status**: ❌ FAILED if numeric (only if provided)

### 22. Mother's Occupation
- **Required**: ❌ NO (Optional)
- **Validations**:
  - If provided, must not be purely numeric
- **Error Message**: "Mother's Occupation must not be purely numeric" (if numeric)
- **Status**: ❌ FAILED if purely numeric (only if provided)

### 23. Course / Program
- **Required**: ❌ NO (Optional)
- **Validations**:
  - If provided, must not be purely numeric
- **Error Message**: "Course / Program must not be purely numeric" (if numeric)
- **Status**: ❌ FAILED if purely numeric (only if provided)

### 24. Year Level
- **Required**: ❌ NO (Optional)
- **Validations**:
  - If provided, must be numeric
  - Must be between 1 and 10
- **Error Messages**:
  - "Year Level must be numeric" (if not numeric)
  - "Year Level must be between 1 and 10" (if out of range)
- **Status**: ❌ FAILED if not numeric or out of range (only if provided)

### 25. Section
- **Required**: ❌ NO (Optional)
- **Validations**: None (can be any format - alphanumeric)
- **Status**: ✅ Always passes (no validation)

---

## Enhanced Validations Added

### Gender Validation
- Now validates common gender values (Male, Female, M, F, Other, Prefer not to say)
- Warns if value is unusually long (> 20 characters)

### Date of Birth Validation
- Now checks that date contains at least one number
- Basic format validation

### Email Address Validation
- Enhanced format validation:
  - Must have text before '@'
  - Must have text after '@'
  - Must contain a domain with '.' (e.g., example.com)

### Age Validation
- Now validates range: must be between 1 and 150

### Contact Number Validation
- Now validates maximum length: must not exceed 15 digits

---

## Additional Logic for Success/Failed Classification

### Explicit Empty Check
- If a mapped column is explicitly empty (mapped but value is empty), the record is marked as **FAILED**
- Logic: `boolean ok = !explicitEmptyFound && isValid(record)`

### Validation Process
1. Extract data from CSV using mapping
2. Check for explicit empty fields (mapped but empty)
3. Run all validation rules
4. If ANY error found → **FAILED**
5. If NO errors found → **SUCCESS**

---

## Summary Table

| Field | Required | Validations | Fails If |
|-------|----------|------------|----------|
| Student ID | ✅ | Not empty | Empty |
| First Name | ✅ | Not empty, Not numeric | Empty or numeric |
| Last Name | ✅ | Not empty, Not numeric | Empty or numeric |
| Middle Name | ❌ | Not numeric (if provided) | Numeric (if provided) |
| Gender | ✅ | Not empty, Valid format | Empty or invalid format |
| Date of Birth | ✅ | Not empty, Contains numbers | Empty or no numbers |
| Email Address | ✅ | Not empty, Contains '@', Valid format | Empty, no '@', or invalid format |
| Age | ✅ | Not empty, Numeric, Range 1-150 | Empty, not numeric, or out of range |
| Contact Number | ✅ | Not empty, Numeric, 7-15 digits | Empty, not numeric, or invalid length |
| Nationality | ❌ | Not numeric (if provided) | Numeric (if provided) |
| Religion | ❌ | Not numeric (if provided) | Numeric (if provided) |
| Civil Status | ❌ | Not numeric, Max 30 chars (if provided) | Numeric or too long (if provided) |
| Home Address | ❌ | Not purely numeric (if provided) | Purely numeric (if provided) |
| City/Municipality | ❌ | Not numeric (if provided) | Numeric (if provided) |
| Province/State | ❌ | Not numeric (if provided) | Numeric (if provided) |
| Zip Code | ❌ | Max 10 chars (if provided) | Too long (if provided) |
| Guardian's Name | ❌ | Not numeric (if provided) | Numeric (if provided) |
| Guardian's Contact | ❌ | Numeric, 7-15 digits (if provided) | Invalid format or length (if provided) |
| Father's Name | ❌ | Not numeric (if provided) | Numeric (if provided) |
| Father's Occupation | ❌ | Not purely numeric (if provided) | Purely numeric (if provided) |
| Mother's Name | ❌ | Not numeric (if provided) | Numeric (if provided) |
| Mother's Occupation | ❌ | Not purely numeric (if provided) | Purely numeric (if provided) |
| Course / Program | ❌ | Not purely numeric (if provided) | Purely numeric (if provided) |
| Year Level | ❌ | Numeric, Range 1-10 (if provided) | Not numeric or out of range (if provided) |
| Section | ❌ | None | Always passes |

---

## Current Implementation Location
- **File**: `src/main/java/com/loginpage/API/service/CsvService.java`
- **Method**: `getValidationErrors(CsvRecord record)`
- **Lines**: 488-542

