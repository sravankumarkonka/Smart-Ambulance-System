import ExcelJS from 'exceljs';
import path from 'path';

/**
 * Generates a color-coded E2E test report in Excel format.
 * @param {Array} results - List of test case result objects
 * @param {string} outputPath - Output file path
 */
export async function generateReport(results, outputPath) {
  const workbook = new ExcelJS.Workbook();
  workbook.creator = 'Smart Ambulance E2E Test Suite';
  workbook.lastModifiedBy = 'Smart Ambulance E2E Test Suite';
  workbook.created = new Date();
  workbook.modified = new Date();

  const worksheet = workbook.addWorksheet('E2E Test Results');

  // Ensure gridlines are visible
  worksheet.views = [{ showGridLines: true }];

  // Calculate statistics
  const total = results.length;
  const passed = results.filter(r => r.status === 'PASS').length;
  const failed = total - passed;
  const passRate = total > 0 ? ((passed / total) * 100).toFixed(1) + '%' : '0%';

  // --- Title Block ---
  worksheet.mergeCells('A1:H1');
  const titleCell = worksheet.getCell('A1');
  titleCell.value = 'Smart Ambulance E2E Test Suite - Complete E2E Report';
  titleCell.font = { name: 'Arial', size: 16, bold: true, color: { argb: 'FFFFFFFF' } };
  titleCell.fill = {
    type: 'pattern',
    pattern: 'solid',
    fgColor: { argb: 'FF1E293B' } // Slate 800
  };
  titleCell.alignment = { horizontal: 'center', vertical: 'middle' };
  worksheet.getRow(1).height = 40;

  // --- Summary Dashboard (Cards) ---
  // Total Cases Card
  worksheet.mergeCells('A3:B4');
  const totalCard = worksheet.getCell('A3');
  totalCard.value = `TOTAL CASES\n\n${total}`;
  totalCard.font = { name: 'Arial', size: 10, bold: true, color: { argb: 'FF334155' } };
  totalCard.alignment = { horizontal: 'center', vertical: 'middle', wrapText: true };
  totalCard.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFF1F5F9' } }; // Slate 100
  setBorder(worksheet, 'A3:B4', 'FFCBD5E1');

  // Passed Card
  worksheet.mergeCells('C3:D4');
  const passedCard = worksheet.getCell('C3');
  passedCard.value = `PASSED\n\n${passed}`;
  passedCard.font = { name: 'Arial', size: 10, bold: true, color: { argb: 'FF15803D' } }; // Green 700
  passedCard.alignment = { horizontal: 'center', vertical: 'middle', wrapText: true };
  passedCard.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFDCFCE7' } }; // Green 100
  setBorder(worksheet, 'C3:D4', 'FF86EFAC');

  // Failed Card
  worksheet.mergeCells('E3:F4');
  const failedCard = worksheet.getCell('E3');
  failedCard.value = `FAILED\n\n${failed}`;
  failedCard.font = { name: 'Arial', size: 10, bold: true, color: { argb: 'FFB91C1C' } }; // Red 700
  failedCard.alignment = { horizontal: 'center', vertical: 'middle', wrapText: true };
  failedCard.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFFEE2E2' } }; // Red 100
  setBorder(worksheet, 'E3:F4', 'FFFCA5A5');

  // Pass Rate Card
  worksheet.mergeCells('G3:H4');
  const rateCard = worksheet.getCell('G3');
  rateCard.value = `PASS RATE\n\n${passRate}`;
  rateCard.font = { name: 'Arial', size: 10, bold: true, color: { argb: 'FF1D4ED8' } }; // Blue 700
  rateCard.alignment = { horizontal: 'center', vertical: 'middle', wrapText: true };
  rateCard.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFDBEAFE' } }; // Blue 100
  setBorder(worksheet, 'G3:H4', 'FF93C5FD');

  worksheet.getRow(3).height = 20;
  worksheet.getRow(4).height = 20;

  // --- Spacer Row ---
  worksheet.getRow(5).height = 15;

  // --- Table Headers ---
  const headers = [
    'Case ID',
    'Test Suite',
    'Test Scenario / Description',
    'Test Inputs',
    'Expected Behavior',
    'Actual Outcome',
    'Status',
    'Duration (ms)'
  ];

  const headerRowNumber = 6;
  const headerRow = worksheet.getRow(headerRowNumber);
  headerRow.values = headers;
  headerRow.height = 28;

  headers.forEach((h, idx) => {
    const cell = headerRow.getCell(idx + 1);
    cell.font = { name: 'Arial', size: 11, bold: true, color: { argb: 'FFFFFFFF' } };
    cell.fill = {
      type: 'pattern',
      pattern: 'solid',
      fgColor: { argb: 'FF475569' } // Slate 600
    };
    cell.alignment = { horizontal: 'center', vertical: 'middle', wrapText: true };
    cell.border = {
      top: { style: 'medium', color: { argb: 'FF334155' } },
      bottom: { style: 'medium', color: { argb: 'FF334155' } },
      left: { style: 'thin', color: { argb: 'FFCBD5E1' } },
      right: { style: 'thin', color: { argb: 'FFCBD5E1' } }
    };
  });

  // --- Data Rows ---
  let currentRowNumber = 7;
  results.forEach(result => {
    const row = worksheet.getRow(currentRowNumber);
    row.values = [
      result.id,
      result.suite,
      result.description,
      result.inputs,
      result.expected,
      result.actual,
      result.status,
      result.durationMs
    ];
    row.height = 22;

    // Apply color schemes based on Status (PASS / FAIL)
    const isPass = result.status === 'PASS';
    const cellBgColor = isPass ? 'FFF0FDF4' : 'FFFDF2F2'; // Very light green / red for rows
    const statusTextClr = isPass ? 'FF15803D' : 'FFB91C1C';
    const statusBgClr = isPass ? 'FFDCFCE7' : 'FFFEE2E2';

    for (let colIdx = 1; colIdx <= 8; colIdx++) {
      const cell = row.getCell(colIdx);
      cell.font = { name: 'Arial', size: 10, color: { argb: 'FF1E293B' } };
      cell.border = {
        top: { style: 'thin', color: { argb: 'FFE2E8F0' } },
        bottom: { style: 'thin', color: { argb: 'FFE2E8F0' } },
        left: { style: 'thin', color: { argb: 'FFE2E8F0' } },
        right: { style: 'thin', color: { argb: 'FFE2E8F0' } }
      };

      // Set cell fills
      if (colIdx === 7) {
        // Status column has unique bubble/pill look
        cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: statusBgClr } };
        cell.font = { name: 'Arial', size: 10, bold: true, color: { argb: statusTextClr } };
        cell.alignment = { horizontal: 'center', vertical: 'middle' };
      } else {
        cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: cellBgColor } };
        
        // Alignment formatting
        if (colIdx === 1 || colIdx === 8) {
          cell.alignment = { horizontal: 'center', vertical: 'middle' };
        } else if (colIdx === 2) {
          cell.alignment = { horizontal: 'center', vertical: 'middle' };
        } else {
          cell.alignment = { horizontal: 'left', vertical: 'middle', wrapText: true };
        }
      }
    }
    currentRowNumber++;
  });

  // Enable Auto Filter
  worksheet.autoFilter = {
    from: { row: headerRowNumber, column: 1 },
    to: { row: currentRowNumber - 1, column: 8 }
  };

  // Adjust columns to fit content
  worksheet.columns.forEach((column, idx) => {
    let maxLen = 0;
    // Don't size columns based on the merged title row
    column.eachCell({ includeEmpty: false }, (cell, rowNum) => {
      if (rowNum >= headerRowNumber) {
        const valStr = cell.value ? cell.value.toString() : '';
        if (valStr.length > maxLen) {
          maxLen = valStr.length;
        }
      }
    });

    // Limit maximum size to prevent huge wraps, set reasonable defaults
    let width = Math.max(maxLen + 4, 12);
    if (idx === 0) width = 10;  // Case ID
    if (idx === 1) width = 15;  // Test Suite
    if (idx === 2) width = 35;  // Scenario
    if (idx === 3) width = 30;  // Inputs
    if (idx === 4) width = 35;  // Expected
    if (idx === 5) width = 35;  // Actual
    if (idx === 6) width = 12;  // Status
    if (idx === 7) width = 14;  // Duration (ms)

    column.width = width;
  });

  // Save the report
  await workbook.xlsx.writeFile(outputPath);
  console.log(`[ExcelReporter] Excel E2E Test Report saved to: ${outputPath}`);
}

// Helper function to set borders on cell range
function setBorder(worksheet, range, borderColor) {
  const [start, end] = range.split(':');
  const startCol = start.charCodeAt(0) - 64;
  const startRow = parseInt(start.slice(1));
  const endCol = end.charCodeAt(0) - 64;
  const endRow = parseInt(end.slice(1));

  for (let r = startRow; r <= endRow; r++) {
    for (let c = startCol; c <= endCol; c++) {
      const cell = worksheet.getCell(r, c);
      cell.border = {
        top: { style: 'thin', color: { argb: borderColor } },
        bottom: { style: 'thin', color: { argb: borderColor } },
        left: { style: 'thin', color: { argb: borderColor } },
        right: { style: 'thin', color: { argb: borderColor } }
      };
    }
  }
}
