# Optional: bundle the OCR models (eng + ben, "fast", ~5 MB) inside the APK so prescription
# reading works offline from the very first use. Without this, the app downloads them once.
# Source: official Tesseract repo (Apache-2.0).
$dest = Join-Path $PSScriptRoot "..\app\src\main\assets\tessdata"
New-Item -ItemType Directory -Force $dest | Out-Null
foreach ($l in @("eng", "ben")) {
    $url = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/$l.traineddata"
    Invoke-WebRequest $url -OutFile (Join-Path $dest "$l.traineddata")
    Write-Host "saved $l.traineddata"
}
