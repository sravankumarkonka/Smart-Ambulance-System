import ExcelJS from 'exceljs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

async function main() {
  const workbook = new ExcelJS.Workbook();
  const reportPath = path.join(__dirname, 'E2E_Test_Report.xlsx');
  
  await workbook.xlsx.readFile(reportPath);
  const worksheet = workbook.getWorksheet('E2E Test Results');
  
  console.log('--- Verification of Excel Report ---');
  
  // Read Title
  const title = worksheet.getCell('A1').value;
  console.log('Title Block:', title);
  
  // Read KPI Summary cards
  const totalVal = worksheet.getCell('A3').value;
  const passedVal = worksheet.getCell('C3').value;
  const failedVal = worksheet.getCell('E3').value;
  const rateVal = worksheet.getCell('G3').value;
  
  console.log('\n--- KPI Summary Cards ---');
  console.log(totalVal);
  console.log(passedVal);
  console.log(failedVal);
  console.log(rateVal);
  
  // Count passed/failed rows
  let passCount = 0;
  let failCount = 0;
  worksheet.eachRow((row, rowNumber) => {
    if (rowNumber <= 6) return; // Skip headers/summary
    const status = row.getCell(7).value;
    if (status === 'PASS') {
      passCount++;
    } else if (status === 'FAIL') {
      failCount++;
    }
  });
  
  console.log('\n--- Failed Test Case Details ---');
  worksheet.eachRow((row, rowNumber) => {
    if (rowNumber <= 6) return; // Skip headers/summary
    const id = row.getCell(1).value;
    const suite = row.getCell(2).value;
    const desc = row.getCell(3).value;
    const inputs = row.getCell(4).value;
    const expected = row.getCell(5).value;
    const actual = row.getCell(6).value;
    const status = row.getCell(7).value;
    
    if (status === 'FAIL') {
      console.log(`Row: ${rowNumber} | ID: ${id} | Suite: ${suite}`);
      console.log(`  Desc    : ${desc}`);
      console.log(`  Inputs  : ${inputs}`);
      console.log(`  Expected: ${expected}`);
      console.log(`  Actual  : ${actual}`);
      console.log('------------------------------------------------');
    }
  });
  
  console.log('\n--- Data Row Counts ---');
  console.log(`Passed: ${passCount}`);
  console.log(`Failed: ${failCount}`);
  console.log(`Total: ${passCount + failCount}`);
}

main().catch(console.error);
