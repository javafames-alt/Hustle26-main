# Import a small, texture-deduplicated selection using the client's existing model format.
param([Parameter(Mandatory = $true)][string]$Source)
$ErrorActionPreference = 'Stop'
$assetRoot = Join-Path $PSScriptRoot '../src/main/resources/assets'
$catalogPath = Join-Path $assetRoot 'coliseum/cosmetics/coliseum/catalog.json'
$catalog = @(Get-Content $catalogPath -Raw | ConvertFrom-Json)
$knownTextures = @{}
Get-ChildItem $assetRoot -Recurse -Filter '*.png' | ForEach-Object {
    $knownTextures[(Get-FileHash $_.FullName -Algorithm SHA256).Hash] = $true
}
$sourceCatalog = Get-Content (Join-Path $Source 'catalog.json') -Raw | ConvertFrom-Json
$added = @()
foreach ($item in $sourceCatalog) {
    if ($item.category -ne 'wings' -or $item.name.StartsWith('pulse_')) { continue }
    $sourceFolder = Join-Path $Source ('wings/' + $item.name)
    $texturePath = Join-Path $sourceFolder 'texture.png'
    if (!(Test-Path $texturePath)) { continue }
    $textureHash = (Get-FileHash $texturePath -Algorithm SHA256).Hash
    if ($knownTextures.ContainsKey($textureHash)) { continue }
    $id = 1000 + [array]::IndexOf($sourceCatalog, $item)
    $relativePath = 'cosmetics/coliseum/wings/' + $id + '/'
    $destination = Join-Path $assetRoot ('coliseum/' + $relativePath)
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    $model = [ordered]@{
        name = $item.displayName; id = $id; category = 1; pos = 1
        scale = $item.scale; x = ($item.x + 0.5); y = ($item.y + 0.81); z = ($item.z + 0.5)
        previewScale = 1; previewY = 0.2
        model = (Get-Content (Join-Path $sourceFolder 'model.geo.json') -Raw | ConvertFrom-Json)
    }
    $animationPath = Join-Path $sourceFolder 'animation.json'
    if (Test-Path $animationPath) {
        $model.animation = Get-Content $animationPath -Raw | ConvertFrom-Json
    }
    $model | ConvertTo-Json -Depth 100 | Set-Content (Join-Path $destination 'model.json') -Encoding utf8
    Copy-Item $texturePath (Join-Path $destination 'texture.png')
    Copy-Item $texturePath (Join-Path $destination 'preview.png')
    $catalog += [pscustomobject]@{ id = $id; name = $item.displayName; type = 'wings'; path = $relativePath }
    $knownTextures[$textureHash] = $true
    $added += $item.displayName
    if ($added.Count -eq 6) { break }
}
$catalog | ConvertTo-Json -Depth 10 | Set-Content $catalogPath -Encoding utf8
Write-Output ('Added: ' + ($added -join ', '))
