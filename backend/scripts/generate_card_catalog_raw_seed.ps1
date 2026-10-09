<#
.SYNOPSIS
    all_cards.csv(크롤링 매니페스트)와 실제 원문 txt 폴더를 읽어서,
    card_catalog_raw 테이블에 넣을 Flyway seed SQL(V2)을 생성한다.

.EXAMPLE
    .\generate_card_catalog_raw_seed.ps1 `
        -CsvPath "data\card_catalog_run_2026-10-06\all_cards.csv" `
        -CrawlRoot "C:\path\to\크롤링결과" `
        -OutPath "..\src\main\resources\db\migration\V2__seed_card_catalog_raw.sql"

.NOTES
    CrawlRoot는 all_cards.csv의 source_text_path에서
    "outputs\card_catalog_run_2026-10-06\" 뒤에 오는 상대경로(예: kb\text\09828.txt)가
    실제로 존재하는 폴더를 가리켜야 한다.
#>

param(
    [Parameter(Mandatory = $true)][string]$CsvPath,
    [Parameter(Mandatory = $true)][string]$CrawlRoot,
    [Parameter(Mandatory = $true)][string]$OutPath
)

$RunPrefix = "outputs\card_catalog_run_2026-10-06\"

function Escape-SqlString([string]$Text) {
    # 순서 중요: 백슬래시를 먼저 두 배로 만든 다음 홑따옴표를 escape 해야 중복 escape가 안 생긴다.
    $escaped = $Text -replace '\\', '\\\\'
    $escaped = $escaped -replace "'", "\'"
    return $escaped
}

function Read-TextSmart([byte[]]$Bytes) {
    $text = [System.Text.Encoding]::UTF8.GetString($Bytes)
    if ($text.Contains([char]0xFFFD)) {
        $text = [System.Text.Encoding]::GetEncoding(949).GetString($Bytes)
    }
    if ($text.Length -gt 0 -and $text[0] -eq [char]0xFEFF) {
        $text = $text.Substring(1)
    }
    return $text
}

$rows = Import-Csv -Path $CsvPath -Encoding UTF8
$sha256Provider = [System.Security.Cryptography.SHA256]::Create()
$sb = New-Object System.Text.StringBuilder
$missing = New-Object System.Collections.Generic.List[string]
$now = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$okCount = 0

foreach ($row in $rows) {
    $rel = $row.source_text_path
    if ($rel.StartsWith($RunPrefix)) {
        $rel = $rel.Substring($RunPrefix.Length)
    }
    $localPath = Join-Path $CrawlRoot $rel

    if (-not (Test-Path -LiteralPath $localPath -PathType Leaf)) {
        $missing.Add("$($row.issuer) / $($row.card_name) -> $localPath")
        continue
    }

    $bytes = [System.IO.File]::ReadAllBytes($localPath)
    $text = Read-TextSmart $bytes
    $hashBytes = $sha256Provider.ComputeHash($bytes)
    $sha256 = ([System.BitConverter]::ToString($hashBytes) -replace '-', '').ToLower()

    $escIssuer = Escape-SqlString $row.issuer
    $escCardId = Escape-SqlString $row.card_id
    $escCardName = Escape-SqlString $row.card_name
    $escCardType = Escape-SqlString $row.card_type
    $escStatus = Escape-SqlString $row.status
    $escUrl = Escape-SqlString $row.detail_url
    $escText = Escape-SqlString $text

    $line = "INSERT INTO card_catalog_raw " +
        "(issuer, card_id, card_name, card_type, status, detail_url, content_hash, raw_text, raw_text_length, review_status, collected_at) VALUES (" +
        "'$escIssuer', '$escCardId', '$escCardName', '$escCardType', '$escStatus', '$escUrl', '$sha256', " +
        "'$escText', $($bytes.Length), 'raw_collected', '$now');"

    [void]$sb.AppendLine($line)
    $okCount++
}

$outDir = Split-Path -Path $OutPath -Parent
if ($outDir -and -not (Test-Path -LiteralPath $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}
Set-Content -LiteralPath $OutPath -Value $sb.ToString() -Encoding UTF8

Write-Host "완료: $okCount 건 -> $OutPath"
if ($missing.Count -gt 0) {
    Write-Host "`n[경고] 파일을 찾지 못한 $($missing.Count)건:"
    $missing | Select-Object -First 30 | ForEach-Object { Write-Host "  - $_" }
}
